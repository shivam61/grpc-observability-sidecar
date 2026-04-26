# Low-Overhead gRPC Observability Sidecar

A production-oriented low-overhead prototype designed to explore the cost of telemetry in latency-critical systems through bounded cardinality, controlled sampling, and byte-level forwarding.

## Problem

Observing gRPC microservices typically requires embedding language-specific interceptors. This couples observability to the application layer. This sidecar aims to provide a language-agnostic way to capture "Golden Signals" with minimal impact on tail latency.

## Key Features (Production-Oriented Prototype)

- **Byte-level Proxying:** Intercepts `byte[]` payloads directly, avoiding Protobuf deserialization overhead.
- **Bounded Cardinality:** Prevents Prometheus label explosion by capping unique method names and normalizing unknowns.
- **Dynamic Sampling:** Configurable success and error sample rates to reduce CPU overhead under load.
- **Accurate Percentiles:** Uses `HdrHistogram` internally for high-fidelity latency snapshots without bucket-induced bias.
- **Deadline Propagation:** Automatically honors and forwards gRPC deadlines to upstream services.

## Architecture & High-Level Design (HLD)

The sidecar operates as a completely transparent proxy. By avoiding Protobuf deserialization on the hot path, we drastically reduce memory allocations and GC pressure.

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

## Benchmark Results (Local Simulated)

| Scenario | Total Requests | Duration (ms) | QPS | p50 (ms) | p95 (ms) | p99 (ms) |
|----------|----------------|---------------|-----|----------|----------|----------|
| Direct (No Sidecar) | 5000 | ~49,000 | ~101 | 64 | 235 | 352 |
| Sidecar Proxy | 5000 | ~65,000 | ~76 | 128 | 301 | 430 |

**Estimated Sidecar Overhead per Request:** ~3.25 ms
**Relative Time Overhead:** ~33% (primarily due to extra local TCP hop and context switching).

*Note: These results represent a local development environment. In production sidecar deployments (e.g. Unix Domain Sockets or shared loopback), the overhead is expected to be lower.*

## Request flow
...
1. The client sends a gRPC request to the sidecar.
2. The sidecar extracts the full method name (e.g. `/example.payment.PaymentService/Authorize`).
3. The method name is validated against the `CardinalityController`.
4. The request payload (`byte[]`) is forwarded to the upstream without unmarshalling the protobuf.
5. `System.nanoTime()` measures the response latency.
6. The sidecar forwards the response payload and status code to the client.

## Metrics collected

- `grpc_requests_total`
- `grpc_errors_total`
- `grpc_status_total`
- `grpc_requests_in_flight`
- `grpc_request_duration_seconds` (Prometheus Histogram)
- `grpc_request_bytes`
- `grpc_response_bytes`

## Cardinality control

To protect Prometheus from label explosions (e.g., from generated or fuzzed method names), the `CardinalityController`:
- Normalizes unknown methods to `__other__`.
- Allows enforcing an explicit allowlist.
- Caps the maximum number of unique method labels tracked.

## Deadline propagation

gRPC deadlines are actively extracted from the incoming request `Context` and propagated to the upstream call using `CallOptions.withDeadline()`.

## Sampling strategy

Sampling can be configured to record 100% of errors but only a percentage of successful requests, mitigating CPU overhead during extreme traffic spikes.

## Local quick start

```bash
# Compile
mvn clean install

# Run the backend service (Port 50051)
mvn -pl grpc-obs-example-service exec:java

# Run the sidecar (Port 9090 -> 50051, Metrics 9464)
mvn -pl grpc-obs-sidecar exec:java

# Run the load generating client
mvn -pl grpc-obs-example-client exec:java
```

## Prometheus/Grafana demo

```bash
cd docker
docker-compose up -d
```
Grafana will be available at http://localhost:3000 (admin/admin). Import the JSON dashboard found in `dashboards/`.

## Benchmark methodology

A simulated local benchmark compares the QPS and latency of a direct gRPC connection vs. proxied through the sidecar.

```bash
mvn clean install -DskipTests
mvn -pl grpc-obs-benchmarks exec:java
```

## Benchmark results

Simulated local benchmark results. Reproduce with the command above. See `benchmark-results.md` after running.
It is designed to minimize overhead using byte-array proxying.

## Design tradeoffs

See [tradeoffs.md](docs/tradeoffs.md).

## Limitations

- Only Unary RPC is supported.
- Streaming (Server, Client, Bidi) is on the roadmap.
- Does not inject xDS or service mesh config.

## Roadmap

1. Implement Streaming RPCs.
2. Add TLS / mTLS support.
3. Optimize byte marshaller with Zero-Copy implementations if possible using Netty direct buffers.
