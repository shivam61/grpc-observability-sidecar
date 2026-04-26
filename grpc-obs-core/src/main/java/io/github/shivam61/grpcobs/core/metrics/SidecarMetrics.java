package io.github.shivam61.grpcobs.core.metrics;

import io.github.shivam61.grpcobs.core.config.SidecarConfig.SamplingConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.HdrHistogram.Histogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SidecarMetrics {
    private final MeterRegistry registry;
    private final CardinalityController cardinalityController;
    private final SamplingConfig samplingConfig;
    
    private final AtomicInteger requestsInFlight = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Histogram> internalHistograms = new ConcurrentHashMap<>();
    
    // Meter Caches
    private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> statusCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> requestSizeSummaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> responseSizeSummaries = new ConcurrentHashMap<>();

    public SidecarMetrics(MeterRegistry registry, CardinalityController cardinalityController, SamplingConfig samplingConfig) {
        this.registry = registry;
        this.cardinalityController = cardinalityController;
        this.samplingConfig = samplingConfig;
        
        registry.gauge("grpc_requests_in_flight", requestsInFlight);
    }

    public void recordRequestStarted(String rawMethodName) {
        requestsInFlight.incrementAndGet();
        String method = cardinalityController.normalizeMethod(rawMethodName);
        
        requestCounters.computeIfAbsent(method, m -> Counter.builder("grpc_requests_total")
                .tag("method", m)
                .register(registry))
                .increment();
    }

    public void recordRequestCompleted(String rawMethodName, String statusCode, long durationNanos) {
        requestsInFlight.decrementAndGet();
        
        boolean isError = !"OK".equals(statusCode);
        if (!shouldSample(isError)) {
            return;
        }

        String method = cardinalityController.normalizeMethod(rawMethodName);
        String statusKey = method + "|" + statusCode;

        if (isError) {
            errorCounters.computeIfAbsent(statusKey, k -> Counter.builder("grpc_errors_total")
                    .tag("method", method)
                    .tag("grpc_status", statusCode)
                    .register(registry))
                    .increment();
        }

        statusCounters.computeIfAbsent(statusKey, k -> Counter.builder("grpc_status_total")
                .tag("method", method)
                .tag("grpc_status", statusCode)
                .register(registry))
                .increment();

        latencyTimers.computeIfAbsent(statusKey, k -> Timer.builder("grpc_request_duration_seconds")
                .tag("method", method)
                .tag("grpc_status", statusCode)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry))
                .record(durationNanos, TimeUnit.NANOSECONDS);
                
        // Efficient HdrHistogram recording
        internalHistograms.computeIfAbsent(method, k -> new Histogram(3600000000000L, 3))
                .recordValue(durationNanos);
    }

    private boolean shouldSample(boolean isError) {
        if (!samplingConfig.enabled()) {
            return true;
        }
        double rate = isError ? samplingConfig.errorSampleRate() : samplingConfig.successSampleRate();
        if (rate >= 1.0) return true;
        if (rate <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    public void recordRequestBytes(String rawMethodName, long bytes) {
        String method = cardinalityController.normalizeMethod(rawMethodName);
        requestSizeSummaries.computeIfAbsent(method, m -> DistributionSummary.builder("grpc_request_bytes")
                .tag("method", m)
                .register(registry))
                .record(bytes);
    }

    public void recordResponseBytes(String rawMethodName, long bytes) {
        String method = cardinalityController.normalizeMethod(rawMethodName);
        responseSizeSummaries.computeIfAbsent(method, m -> DistributionSummary.builder("grpc_response_bytes")
                .tag("method", m)
                .register(registry))
                .record(bytes);
    }
    
    public ConcurrentHashMap<String, Histogram> getInternalHistograms() {
        return internalHistograms;
    }
}
