package io.github.shivam61.grpcobs.core.config;

import java.util.List;
import java.util.Set;

public record SidecarConfig(
    ServerConfig server,
    UpstreamConfig upstream,
    MetricsConfig metrics,
    CardinalityConfig cardinality,
    SamplingConfig sampling,
    OverheadConfig overhead
) {
    public record ServerConfig(int listenPort) {}
    public record UpstreamConfig(String host, int port) {}
    public record MetricsConfig(int port, String path, List<Double> histogramBuckets, boolean enablePayloadSizeMetrics) {}
    public record CardinalityConfig(int maxMethods, String unknownMethodLabel, Set<String> methodAllowlist) {}
    public record SamplingConfig(boolean enabled, double successSampleRate, double errorSampleRate) {}
    public record OverheadConfig(boolean enableSelfMetrics) {}
}
