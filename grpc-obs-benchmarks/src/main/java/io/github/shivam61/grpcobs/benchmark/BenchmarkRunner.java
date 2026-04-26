package io.github.shivam61.grpcobs.benchmark;

import io.github.shivam61.grpcobs.client.ExampleClient;
import io.github.shivam61.grpcobs.example.ExampleServer;
import io.github.shivam61.grpcobs.sidecar.SidecarServer;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting Benchmark");
        
        // Start Example Service
        Thread serviceThread = new Thread(() -> {
            try {
                System.setProperty("SERVER_PORT", "50051");
                ExampleServer.main(new String[]{});
            } catch (Exception e) {
                logger.error("Service error", e);
            }
        });
        serviceThread.setDaemon(true);
        serviceThread.start();
        Thread.sleep(2000); // Wait for service to start

        // Start Sidecar
        Thread sidecarThread = new Thread(() -> {
            try {
                SidecarServer.main(new String[]{});
            } catch (Exception e) {
                logger.error("Sidecar error", e);
            }
        });
        sidecarThread.setDaemon(true);
        sidecarThread.start();
        Thread.sleep(2000); // Wait for sidecar to start

        // Run Direct Benchmark
        BenchmarkResult directResult = runLoadTest("localhost", 50051, "Direct (No Sidecar)");
        
        // Run Sidecar Benchmark
        BenchmarkResult sidecarResult = runLoadTest("localhost", 9090, "Sidecar Proxy");

        // Save Results
        saveResults(directResult, sidecarResult);
        
        logger.info("Benchmark complete. Results saved to benchmark-results.md and benchmark-results.json");
        System.exit(0);
    }

    private static BenchmarkResult runLoadTest(String host, int port, String name) throws InterruptedException {
        logger.info("Running load test for: {}", name);
        ExampleClient client = new ExampleClient(host, port);
        
        int threads = 10;
        int requestsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        
        Histogram histogram = new Histogram(3600000000000L, 3);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long reqStart = System.nanoTime();
                        client.doAuthorize();
                        long reqDuration = System.nanoTime() - reqStart;
                        synchronized (histogram) {
                            histogram.recordValue(reqDuration);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.currentTimeMillis();
        executor.shutdown();
        client.shutdown();
        
        long durationMs = endTime - startTime;
        double qps = (threads * requestsPerThread) / (durationMs / 1000.0);
        
        double p50 = histogram.getValueAtPercentile(50.0) / 1_000_000.0;
        double p95 = histogram.getValueAtPercentile(95.0) / 1_000_000.0;
        double p99 = histogram.getValueAtPercentile(99.0) / 1_000_000.0;

        return new BenchmarkResult(name, threads * requestsPerThread, durationMs, qps, p50, p95, p99);
    }
    
    private static void saveResults(BenchmarkResult direct, BenchmarkResult sidecar) throws IOException {
        double overheadMs = ((sidecar.durationMs - direct.durationMs) / (double) direct.totalRequests);
        double overheadPercentage = ((sidecar.durationMs - direct.durationMs) / (double) direct.durationMs) * 100;
        
        String md = String.format("""
            # Benchmark Results
            
            | Scenario | Total Requests | Duration (ms) | QPS | p50 (ms) | p95 (ms) | p99 (ms) |
            |----------|----------------|---------------|-----|----------|----------|----------|
            | %s | %d | %d | %.2f | %.2f | %.2f | %.2f |
            | %s | %d | %d | %.2f | %.2f | %.2f | %.2f |
            
            **Estimated Sidecar Overhead per Request:** %.2f ms
            **Relative Time Overhead:** %.2f%%
            """,
            direct.name, direct.totalRequests, direct.durationMs, direct.qps, direct.p50, direct.p95, direct.p99,
            sidecar.name, sidecar.totalRequests, sidecar.durationMs, sidecar.qps, sidecar.p50, sidecar.p95, sidecar.p99,
            overheadMs, overheadPercentage
        );
        
        try (FileWriter fw = new FileWriter(new File("benchmark-results.md"))) {
            fw.write(md);
        }
        
        String json = String.format("""
            {
              "direct": {
                "requests": %d,
                "durationMs": %d,
                "qps": %.2f,
                "p50Ms": %.2f,
                "p95Ms": %.2f,
                "p99Ms": %.2f
              },
              "sidecar": {
                "requests": %d,
                "durationMs": %d,
                "qps": %.2f,
                "p50Ms": %.2f,
                "p95Ms": %.2f,
                "p99Ms": %.2f
              },
              "overheadMsPerRequest": %.2f,
              "overheadPercentage": %.2f
            }
            """,
            direct.totalRequests, direct.durationMs, direct.qps, direct.p50, direct.p95, direct.p99,
            sidecar.totalRequests, sidecar.durationMs, sidecar.qps, sidecar.p50, sidecar.p95, sidecar.p99,
            overheadMs, overheadPercentage
        );
        
        try (FileWriter fw = new FileWriter(new File("benchmark-results.json"))) {
            fw.write(json);
        }
    }

    private record BenchmarkResult(String name, int totalRequests, long durationMs, double qps, double p50, double p95, double p99) {}
}
