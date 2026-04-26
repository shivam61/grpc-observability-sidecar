# Metrics Reference

| Metric Name | Type | Labels | Description |
|---|---|---|---|
| `grpc_requests_total` | Counter | `method` | Total requests received |
| `grpc_errors_total` | Counter | `method`, `grpc_status` | Total failed requests |
| `grpc_status_total` | Counter | `method`, `grpc_status` | Total requests by status |
| `grpc_requests_in_flight` | Gauge | | Active requests |
| `grpc_request_duration_seconds` | Histogram | `method`, `grpc_status` | Latency distribution |
| `grpc_request_bytes` | Summary | `method` | Payload size inbound |
| `grpc_response_bytes` | Summary | `method` | Payload size outbound |
