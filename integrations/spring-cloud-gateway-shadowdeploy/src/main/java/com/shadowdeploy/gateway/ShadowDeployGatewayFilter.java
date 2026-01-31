package com.shadowdeploy.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ShadowDeployGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> SAFE_HEADER_ALLOWLIST = List.of(
            "content-type",
            "accept",
            "user-agent",
            "x-request-id",
            "x-correlation-id",
            "traceparent"
    );

    private final ShadowDeployGatewayReplayClient replayClient;
    private final ShadowDeployGatewayProperties properties;
    private final ObjectMapper objectMapper;

    public ShadowDeployGatewayFilter(ShadowDeployGatewayReplayClient replayClient,
                                     ShadowDeployGatewayProperties properties,
                                     ObjectMapper objectMapper) {
        this.replayClient = replayClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && properties.getExcludedPaths().stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        long start = System.currentTimeMillis();
        return cacheRequestBody(exchange)
                .flatMap(cachedExchange -> decorateResponse(cachedExchange, chain, start));
    }

    private Mono<ServerWebExchange> cacheRequestBody(ServerWebExchange exchange) {
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(bufferFactory.wrap(new byte[0]))
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    exchange.getAttributes().put("cachedRequestBody", bytes);

                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(bufferFactory.wrap(bytes)));
                    ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };
                    return exchange.mutate().request(requestDecorator).build();
                });
    }

    private Mono<Void> decorateResponse(ServerWebExchange exchange, GatewayFilterChain chain, long start) {
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!(body instanceof Flux<?> fluxBody)) {
                    return super.writeWith(body);
                }
                return DataBufferUtils.join(fluxBody)
                        .flatMap(dataBuffer -> {
                            byte[] responseBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(responseBytes);
                            DataBufferUtils.release(dataBuffer);
                            capture(exchange, responseBytes, System.currentTimeMillis() - start);
                            return super.writeWith(Mono.just(bufferFactory.wrap(responseBytes)));
                        });
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    private void capture(ServerWebExchange exchange, byte[] responseBytes, long latencyMs) {
        byte[] requestBytes = extractRequestBytes(exchange);
        byte[] clippedRequest = truncate(requestBytes, properties.getMaxBodyBytes());
        byte[] clippedResponse = truncate(responseBytes, properties.getMaxBodyBytes());

        String requestContentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        String responseContentType = exchange.getResponse().getHeaders().getFirst("Content-Type");

        JsonNode requestBody = parseBody(clippedRequest, requestContentType);
        JsonNode responseBody = parseBody(clippedResponse, responseContentType);

        Map<String, String> headers = captureHeaders(exchange);
        String requestId = resolveRequestId(exchange, headers);

        CapturedRequestPayload payload = new CapturedRequestPayload(
                requestId,
                exchange.getRequest().getMethodValue(),
                exchange.getRequest().getURI().getPath(),
                exchange.getRequest().getURI().getQuery(),
                headers,
                requestBody,
                exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0,
                responseBody,
                latencyMs,
                requestContentType
        );

        replayClient.enqueue(payload);
    }

    private byte[] extractRequestBytes(ServerWebExchange exchange) {
        Object cached = exchange.getAttribute("cachedRequestBody");
        if (cached instanceof byte[] bytes) {
            return bytes;
        }
        return new byte[0];
    }

    private Map<String, String> captureHeaders(ServerWebExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<>();
        SAFE_HEADER_ALLOWLIST.forEach(header -> {
            String value = exchange.getRequest().getHeaders().getFirst(header);
            if (StringUtils.hasText(value)) {
                headers.put(header, value);
            }
        });
        return headers;
    }

    private String resolveRequestId(ServerWebExchange exchange, Map<String, String> headers) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (!StringUtils.hasText(requestId)) {
            requestId = headers.get("x-request-id");
        }
        return StringUtils.hasText(requestId) ? requestId : "req-" + UUID.randomUUID();
    }

    private JsonNode parseBody(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (isJson(contentType) || looksLikeJson(text)) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception ignored) {
                return TextNode.valueOf(text);
            }
        }
        return TextNode.valueOf(text);
    }

    private boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private byte[] truncate(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return new byte[0];
        }
        if (bytes.length <= maxBytes) {
            return bytes;
        }
        byte[] trimmed = new byte[maxBytes];
        System.arraycopy(bytes, 0, trimmed, 0, maxBytes);
        return trimmed;
    }
}
