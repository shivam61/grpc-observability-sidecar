# Performance Benchmarking

Run the included benchmark:
```bash
mvn clean install -DskipTests
mvn -pl grpc-obs-benchmarks exec:java
```

This runs:
1. Direct connection test to the service.
2. Proxied connection through the sidecar.

Results are saved to `benchmark-results.md`.
The overhead represents the cost of the extra local TCP hop and metrics recording.
