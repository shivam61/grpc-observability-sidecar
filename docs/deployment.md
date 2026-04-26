# Deployment

The sidecar is designed to be deployed as a container alongside your main application pod (Kubernetes) or task (ECS).

```yaml
# kubernetes sketch
containers:
  - name: my-service
    image: my-service:latest
    ports:
      - containerPort: 50051
  - name: grpc-sidecar
    image: io.github.shivam61/grpc-observability-sidecar:latest
    ports:
      - containerPort: 9090
      - containerPort: 9464 # metrics
    env:
      - name: CONFIG_FILE
        value: "/etc/sidecar/config.yaml"
```
