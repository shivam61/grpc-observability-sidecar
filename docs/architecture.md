# Architecture

```mermaid
flowchart LR
    Client([gRPC Client])
    
    subgraph Deployment Boundary [Deployment Boundary e.g., K8s Pod]
        direction LR
        
        subgraph Sidecar [gRPC Observability Sidecar]
            direction TB
            Proxy[ProxyCallHandler<br/><i>(Byte-Level Forwarding)</i>]
            
            subgraph Telemetry Core
                direction TB
                Card[Cardinality Controller<br/><i>(Label Protection)</i>]
                Met[Metrics Engine<br/><i>(Micrometer & HdrHistogram)</i>]
                Card -.-> Met
            end
            
            Proxy -.->|1. Validate Method Name| Card
            Proxy -.->|3. Record Latency & Size| Met
        end
        
        Upstream([Upstream gRPC Service])
    end
    
    subgraph Observability Stack
        Prometheus[(Prometheus)]
        Grafana[Grafana Dashboard]
    end

    Client == "gRPC Request" ==> Proxy
    Proxy == "2. Forward byte[]" ==> Upstream
    Upstream -. "Response" .-> Proxy
    Proxy -. "Response" .-> Client
    
    Prometheus -- "Scrape /metrics" --> Met
    Grafana -- "Query" --> Prometheus
```

## Components
1. **ProxyCallHandler:** A generic `ServerCallHandler` that intercepts all `byte[]` streams.
2. **CardinalityController:** Ensures method labels do not cause a Prometheus cardinality explosion.
3. **MetricsServer:** Exposes `/metrics` for Prometheus and `/debug/obs` for human-readable latency snapshots using HdrHistogram.
