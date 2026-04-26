package io.github.shivam61.grpcobs.sidecar;

import io.github.shivam61.grpcobs.core.config.ConfigLoader;
import io.github.shivam61.grpcobs.core.config.SidecarConfig;
import io.github.shivam61.grpcobs.core.metrics.CardinalityController;
import io.github.shivam61.grpcobs.core.metrics.SidecarMetrics;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;

public class SidecarServer {
    private static final Logger logger = LoggerFactory.getLogger(SidecarServer.class);

    private Server grpcServer;
    private MetricsServer metricsServer;
    private ManagedChannel upstreamChannel;

    public void start(String configPath) throws Exception {
        logger.info("Starting Sidecar with config: {}", configPath);
        
        SidecarConfig config;
        if (new File(configPath).exists()) {
            config = ConfigLoader.load(configPath);
        } else {
            logger.warn("Config file not found, using defaults");
            config = new SidecarConfig(
                new SidecarConfig.ServerConfig(9090),
                new SidecarConfig.UpstreamConfig("localhost", 50051),
                new SidecarConfig.MetricsConfig(9464, "/metrics", null, true),
                new SidecarConfig.CardinalityConfig(100, "__other__", Collections.emptySet()),
                new SidecarConfig.SamplingConfig(false, 1.0, 1.0),
                new SidecarConfig.OverheadConfig(false)
            );
        }

        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        CardinalityController cardinalityController = new CardinalityController(
                config.cardinality().maxMethods(),
                config.cardinality().unknownMethodLabel(),
                config.cardinality().methodAllowlist()
        );
        SidecarMetrics metrics = new SidecarMetrics(prometheusRegistry, cardinalityController);

        upstreamChannel = ManagedChannelBuilder
                .forAddress(config.upstream().host(), config.upstream().port())
                .usePlaintext()
                .build();

        ProxyCallHandler proxyCallHandler = new ProxyCallHandler(upstreamChannel, metrics);
        ProxyHandlerRegistry handlerRegistry = new ProxyHandlerRegistry(proxyCallHandler);

        grpcServer = ServerBuilder.forPort(config.server().listenPort())
                .fallbackHandlerRegistry(handlerRegistry)
                .build()
                .start();

        metricsServer = new MetricsServer(config.metrics().port(), prometheusRegistry, metrics);
        metricsServer.start();

        logger.info("gRPC Proxy listening on port {}", config.server().listenPort());
        logger.info("Metrics server listening on port {}", config.metrics().port());
        logger.info("Upstream target: {}:{}", config.upstream().host(), config.upstream().port());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down sidecar...");
            try {
                SidecarServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }

    public void stop() throws InterruptedException {
        if (grpcServer != null) grpcServer.shutdown();
        if (metricsServer != null) metricsServer.stop();
        if (upstreamChannel != null) upstreamChannel.shutdown();
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        String configPath = System.getProperty("config", "sidecar-config.yaml");
        SidecarServer server = new SidecarServer();
        server.start(configPath);
        server.blockUntilShutdown();
    }
}
