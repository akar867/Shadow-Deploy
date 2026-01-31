# Spring Cloud Gateway integration

This integration captures real traffic at the gateway layer and replays it
against a shadow instance via ShadowDeploy.

## How it works
- A `GlobalFilter` captures request + response (limited to max bytes).
- Captured traffic is batched and sent to `/api/shadow/replay`.
- ShadowDeploy replays against your shadow base URL and updates the dashboard.

## Add to your Gateway app
Copy these classes into your Spring Cloud Gateway app, then add:

```yaml
shadowdeploy:
  gateway:
    enabled: true
    base-url: http://shadowdeploy-backend:8080
    auth-token: YOUR_SHADOWDEPLOY_TOKEN
    shadow-base-url: http://shadow-service:9090
    service-name: checkout-service
    deployment-id: deploy-rc1
    sample-rate: 0.1
    batch-size: 25
    flush-interval-ms: 2000
    max-body-bytes: 20000
    excluded-paths:
      - /actuator
      - /health
```

## Notes
- Avoid sensitive payloads (PII, payment data).
- Large responses are clipped to `max-body-bytes`.
- Shadow services must be safe (no writes to prod DB).
