# Spring integration (drop-in client)

This folder contains a small Spring Boot client that captures requests and
replays them against a shadow instance via ShadowDeploy.

## How it works
- A servlet filter captures incoming requests and production responses.
- Captured traffic is batched and sent to ShadowDeploy `/api/shadow/replay`.
- ShadowDeploy replays against your shadow base URL and updates the dashboard.

## Add to your Spring app
Copy these classes into your app (same package is fine), then add this config:

```yaml
shadowdeploy:
  client:
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
- **Do not** send sensitive data (PII, payment data). Redact before capture.
- Sample rate helps control volume.
- Ensure your shadow environment is safe (no writes to prod DB).
