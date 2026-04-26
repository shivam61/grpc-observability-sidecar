# The Cost of Observability

Observability is never free.

Every span, log line, and metric adds CPU cycles, memory allocation, and GC pressure to the host application.

In high-throughput, low-latency gRPC services, naively added interceptors can increase p99 latencies substantially.

## Overhead sources:
1. **Time measurement:** `System.nanoTime()` is fast but not free.
2. **Histograms:** Naive histogram bucketing allocates memory or requires locks. We use `HdrHistogram` to bound this.
3. **Network Hop:** A sidecar fundamentally adds a localhost TCP hop.

Our benchmark aims to quantify this.
