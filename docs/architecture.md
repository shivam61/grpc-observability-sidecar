# Architecture

```mermaid
graph TD
    Client[gRPC Client] -->|Unary RPC| Proxy[Sidecar Proxy]
    Proxy -->|Forwarded RPC| Upstream[Upstream Service]
    Proxy -->|Record Latency| MetricsRegistry[Micrometer Registry]
    MetricsRegistry -->|Scrape| Prometheus[Prometheus]
    Prometheus -->|Query| Grafana[Grafana Dashboard]
```

## Components
1. **ProxyCallHandler:** A generic `ServerCallHandler` that intercepts all `byte[]` streams.
2. **CardinalityController:** Ensures method labels do not cause a Prometheus cardinality explosion.
3. **MetricsServer:** Exposes `/metrics` for Prometheus and `/debug/obs` for human-readable latency snapshots using HdrHistogram.
