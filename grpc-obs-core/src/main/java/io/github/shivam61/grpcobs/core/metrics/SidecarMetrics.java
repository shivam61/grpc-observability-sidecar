package io.github.shivam61.grpcobs.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.HdrHistogram.Histogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SidecarMetrics {
    private final MeterRegistry registry;
    private final CardinalityController cardinalityController;
    
    private final AtomicInteger requestsInFlight = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Histogram> internalHistograms = new ConcurrentHashMap<>();

    public SidecarMetrics(MeterRegistry registry, CardinalityController cardinalityController) {
        this.registry = registry;
        this.cardinalityController = cardinalityController;
        
        registry.gauge("grpc_requests_in_flight", requestsInFlight);
    }

    public void recordRequestStarted(String rawMethodName) {
        requestsInFlight.incrementAndGet();
        String method = cardinalityController.normalizeMethod(rawMethodName);
        Counter.builder("grpc_requests_total")
                .tag("method", method)
                .register(registry)
                .increment();
    }

    public void recordRequestCompleted(String rawMethodName, String statusCode, long durationNanos) {
        requestsInFlight.decrementAndGet();
        String method = cardinalityController.normalizeMethod(rawMethodName);
        
        if (!"OK".equals(statusCode)) {
            Counter.builder("grpc_errors_total")
                    .tag("method", method)
                    .tag("grpc_status", statusCode)
                    .register(registry)
                    .increment();
        }

        Counter.builder("grpc_status_total")
                .tag("method", method)
                .tag("grpc_status", statusCode)
                .register(registry)
                .increment();

        Timer.builder("grpc_request_duration_seconds")
                .tag("method", method)
                .tag("grpc_status", statusCode)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
                
        // Efficient HdrHistogram recording
        internalHistograms.computeIfAbsent(method, k -> new Histogram(3600000000000L, 3))
                .recordValue(durationNanos);
    }

    public void recordRequestBytes(String rawMethodName, long bytes) {
        String method = cardinalityController.normalizeMethod(rawMethodName);
        DistributionSummary.builder("grpc_request_bytes")
                .tag("method", method)
                .register(registry)
                .record(bytes);
    }

    public void recordResponseBytes(String rawMethodName, long bytes) {
        String method = cardinalityController.normalizeMethod(rawMethodName);
        DistributionSummary.builder("grpc_response_bytes")
                .tag("method", method)
                .register(registry)
                .record(bytes);
    }
    
    public ConcurrentHashMap<String, Histogram> getInternalHistograms() {
        return internalHistograms;
    }
}
