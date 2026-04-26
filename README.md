# Low-Overhead gRPC Observability Sidecar

A production-oriented low-overhead prototype designed to explore the cost of telemetry in latency-critical systems through bounded cardinality, controlled sampling, and byte-level forwarding.

## Problem

Observing gRPC microservices typically requires embedding language-specific interceptors. This couples observability to the application layer. This sidecar aims to provide a language-agnostic way to capture "Golden Signals" with minimal impact on tail latency.

## Detailed Design & Core Features

- **Byte-level Proxying (No Protobuf AST):** Traditional interceptors unmarshal payloads into Java objects (ASTs). This proxy intercepts streams as raw `byte[]`, preventing massive GC pressure and CPU overhead associated with deserialization on the hot path.
- **Bounded Cardinality Controller:** Prometheus crashes if label cardinality explodes (e.g., from fuzzed or dynamically generated method names). The sidecar enforces a strict cap and an optional allowlist, efficiently routing unknown methods to an `__other__` label without leaking memory.
- **Hot-Path Metric Caching:** Micrometer meter lookups can be expensive. The sidecar caches `Counter` and `Timer` instances in a `ConcurrentHashMap`, achieving near zero-allocation metric emission on the hot path.
- **Dual-Mode Latency Tracking:** Uses Micrometer for standard Prometheus percentiles and `HdrHistogram` internally for ultra-high-fidelity, bucketless latency snapshots.
- **Dynamic Sampling:** Configurable success and error sample rates shed CPU load during extreme traffic spikes while mathematically guaranteeing 100% error visibility.
- **Context & Deadline Propagation:** Automatically extracts gRPC deadlines from the incoming request `Context` and actively propagates them to the upstream service via `CallOptions`.

## Key Tradeoffs

Building a transparent proxy involves careful compromises to balance visibility against overhead:

1. **Sidecar vs. In-Process Interceptor**
   - *Tradeoff:* We pay a latency penalty for an extra `localhost` TCP network hop (typically 1-3ms in virtualized networks).
   - *Benefit:* Complete language agnosticism. You can monitor Java, Go, Rust, or Python upstreams uniformly without embedding metric libraries into their codebases or triggering a massive redeployment to change a telemetry configuration.
2. **Byte-Array Marshalling vs. True Zero-Copy**
   - *Tradeoff:* We use `InputStream.readAllBytes()` to ferry payloads. This avoids Protobuf deserialization but still allocates heap memory for the byte array.
   - *Benefit:* Much simpler to implement cleanly within the `grpc-java` API. True zero-copy (e.g., Netty `FileRegion` or `ByteBuf` slicing) requires breaking encapsulation and using internal gRPC hooks, risking compatibility issues on version upgrades.
3. **Pre-calculated Percentiles vs. Raw Buckets**
   - *Tradeoff:* Prometheus prefers scraping raw histogram buckets to aggregate across fleets, but poor fixed-bucket boundaries ruin percentile accuracy.
   - *Benefit:* We publish `p50/p90/p99` percentiles directly from the proxy to ensure tail latency accuracy, supplementing with an internal `HdrHistogram` for debug snapshots without relying entirely on Prometheus `histogram_quantile` estimations.

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

## Limitations

- Only Unary RPC is supported.
- Streaming (Server, Client, Bidi) is on the roadmap.
- Does not inject xDS or service mesh config.

## Roadmap

1. Implement Streaming RPCs.
2. Add TLS / mTLS support.
3. Optimize byte marshaller with Zero-Copy implementations if possible using Netty direct buffers.
