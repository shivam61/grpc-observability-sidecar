# Contributing

1. Fork the repo.
2. Ensure you have Java 21 and Maven.
3. Run `mvn clean test` locally before submitting a PR.
4. If you modify the core proxy behavior, please run `mvn -pl grpc-obs-benchmarks exec:java` and include the performance impact in your PR description.
5. Sign the DCO (Developer Certificate of Origin) in your commits.
