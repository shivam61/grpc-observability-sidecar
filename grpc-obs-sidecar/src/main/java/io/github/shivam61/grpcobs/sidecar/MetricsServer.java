package io.github.shivam61.grpcobs.sidecar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.shivam61.grpcobs.core.metrics.SidecarMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.HdrHistogram.Histogram;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MetricsServer {

    private final HttpServer server;
    private final PrometheusMeterRegistry registry;
    private final SidecarMetrics sidecarMetrics;

    public MetricsServer(int port, PrometheusMeterRegistry registry, SidecarMetrics sidecarMetrics) throws IOException {
        this.registry = registry;
        this.sidecarMetrics = sidecarMetrics;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/metrics", new MetricsHandler());
        this.server.createContext("/debug/obs", new DebugHandler());
        this.server.setExecutor(null); // creates a default executor
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = registry.scrape();
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class DebugHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("--- Sidecar Debug Observer ---\n\n");
            
            for (Map.Entry<String, Histogram> entry : sidecarMetrics.getInternalHistograms().entrySet()) {
                sb.append("Method: ").append(entry.getKey()).append("\n");
                Histogram h = entry.getValue();
                sb.append("  Count: ").append(h.getTotalCount()).append("\n");
                sb.append("  P50 (ms): ").append(h.getValueAtPercentile(50.0) / 1_000_000.0).append("\n");
                sb.append("  P90 (ms): ").append(h.getValueAtPercentile(90.0) / 1_000_000.0).append("\n");
                sb.append("  P95 (ms): ").append(h.getValueAtPercentile(95.0) / 1_000_000.0).append("\n");
                sb.append("  P99 (ms): ").append(h.getValueAtPercentile(99.0) / 1_000_000.0).append("\n");
                sb.append("  Max (ms): ").append(h.getMaxValue() / 1_000_000.0).append("\n\n");
            }

            String response = sb.toString();
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
