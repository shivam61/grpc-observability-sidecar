# xDS Integration and Proxyless gRPC

This sidecar includes the `grpc-xds` dependency, enabling Staff-level traffic management and service discovery via the xDS protocol.

## What is xDS?
The xDS (eXDS) protocol is a suite of APIs used by control planes (like Istio, Envoy, or Google Traffic Director) to push configuration to data planes.

## Proxyless gRPC vs. Sidecar Proxy
1. **Sidecar Proxy (This Project):**
   - *Architecture:* App <--> Sidecar <--> Network.
   - *Pros:* Language agnostic, zero app code changes.
   - *Cons:* Extra network hop, higher latency (1-3ms).

2. **Proxyless gRPC:**
   - *Architecture:* App (with gRPC-xDS) <--> Network.
   - *Pros:* Direct communication, lowest possible latency, advanced LB (least request, etc.).
   - *Cons:* App must be recompiled with xDS libs, requires complex control plane setup.

## How to use xDS with this Sidecar
Because we've integrated `grpc-xds`, you can configure the sidecar to resolve its **upstream** target dynamically via xDS.

In `sidecar-config.yaml`:
```yaml
upstream:
  host: "xds:///payment-service-cluster"
  port: 0 # Port is managed by xDS
```

When the sidecar starts, it will:
1. Connect to the xDS control plane (defined via `GRPC_XDS_BOOTSTRAP` env var).
2. Resolve the `payment-service-cluster`.
3. Load balance across all healthy endpoints discovered.
4. Apply any retry or hedging policies pushed by the control plane.

## Why this is powerful for Low Latency
By using xDS, the sidecar can "fail fast" if all upstreams are down, or route traffic to the nearest regional cluster, minimizing the tail latency typically introduced by static or round-robin DNS-based load balancing.
