# Design Tradeoffs

## Sidecar vs Interceptor
- **Interceptor:** Runs in the same process. Lowest latency overhead, but language-specific (must rewrite for Java, Go, Python, etc) and couples observability concerns to application code.
- **Sidecar:** Language agnostic. Easy to roll out infrastructure changes without redeploying apps. Adds a localhost network hop.

## Histograms
- **Prometheus Histograms:** Fixed buckets. Easy to query, but bad bucket choices lead to inaccurate percentiles.
- **HdrHistogram:** Dynamic range, highly accurate percentiles. Memory bound. Not natively supported by Prometheus scraping without summary pre-calculation (which breaks aggregation).

## Labels and Cardinality
- Adding dynamic labels (e.g. `user_id` or `client_ip`) will break Prometheus via cardinality explosion.
- The `CardinalityController` bounds unique method names.

## Limitations
- Only Unary RPC is supported.
- Streaming (Server, Client, Bidi) is roadmap.
- No mTLS support between sidecar and upstream yet.
