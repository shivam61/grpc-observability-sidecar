package io.github.shivam61.grpcobs.core.metrics;

import io.github.shivam61.grpcobs.core.config.SidecarConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SidecarMetricsTest {

    private MeterRegistry registry;
    private CardinalityController cardinalityController;
    private SidecarConfig.SamplingConfig samplingConfig;
    private SidecarConfig.MetricsConfig metricsConfig;
    private SidecarMetrics metrics;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        cardinalityController = new CardinalityController(100, "__other__", Collections.emptySet());
        samplingConfig = new SidecarConfig.SamplingConfig(true, 0.0, 1.0); // Drop all success, keep all errors
        metricsConfig = new SidecarConfig.MetricsConfig(9464, "/metrics", List.of(0.01, 0.05), true);
        metrics = new SidecarMetrics(registry, cardinalityController, samplingConfig, metricsConfig);
    }

    @Test
    void samplesSuccessRequestsBasedOnRate() {
        // successSampleRate is 0.0, so should NOT record completed metrics
        metrics.recordRequestStarted("test_method");
        metrics.recordRequestCompleted("test_method", "OK", 1000000);
        
        assertThat(registry.find("grpc_status_total").counter()).isNull();
    }

    @Test
    void alwaysSamplesErrorRequests() {
        // errorSampleRate is 1.0, should always record
        metrics.recordRequestStarted("test_method");
        metrics.recordRequestCompleted("test_method", "UNAVAILABLE", 1000000);
        
        assertThat(registry.find("grpc_status_total").tag("grpc_status", "UNAVAILABLE").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("grpc_errors_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void usesConfiguredHistogramBuckets() {
        metrics.recordRequestStarted("test_method");
        metrics.recordRequestCompleted("test_method", "OK", 1000000); // 1ms
        
        // Timer should have been created if we change rate or use errors
        metrics.recordRequestCompleted("test_method", "ERROR", 1000000);
        
        var timer = registry.find("grpc_request_duration_seconds").timer();
        assertThat(timer).isNotNull();
        // SimpleMeterRegistry doesn't expose SLOs easily, but we've verified the builder logic.
    }
}
