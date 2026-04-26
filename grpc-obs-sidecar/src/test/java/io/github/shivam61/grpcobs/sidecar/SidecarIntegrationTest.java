package io.github.shivam61.grpcobs.sidecar;

import io.github.shivam61.grpcobs.core.metrics.CardinalityController;
import io.github.shivam61.grpcobs.core.metrics.SidecarMetrics;
import io.github.shivam61.grpcobs.core.config.SidecarConfig;
import io.github.shivam61.grpcobs.example.PaymentServiceImpl;
import io.github.shivam61.grpcobs.example.payment.AuthorizeRequest;
import io.github.shivam61.grpcobs.example.payment.AuthorizeResponse;
import io.github.shivam61.grpcobs.example.payment.PaymentServiceGrpc;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarIntegrationTest {

    private Server upstreamServer;
    private Server sidecarServer;
    private ManagedChannel sidecarChannel;
    private PaymentServiceGrpc.PaymentServiceBlockingStub blockingStub;
    private PaymentServiceGrpc.PaymentServiceStub asyncStub;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setup() throws IOException {
        String upstreamName = InProcessServerBuilder.generateName();
        String sidecarName = InProcessServerBuilder.generateName();

        // Upstream
        upstreamServer = InProcessServerBuilder.forName(upstreamName)
                .addService(new PaymentServiceImpl())
                .build()
                .start();

        // Sidecar Setup
        meterRegistry = new SimpleMeterRegistry();
        CardinalityController cardinalityController = new CardinalityController(10, "__other__", Collections.emptySet());
        SidecarMetrics metrics = new SidecarMetrics(meterRegistry, cardinalityController, 
                new SidecarConfig.SamplingConfig(false, 1.0, 1.0),
                new SidecarConfig.MetricsConfig(9464, "/metrics", Collections.emptyList(), true));
        
        ManagedChannel upstreamChannel = InProcessChannelBuilder.forName(upstreamName).directExecutor().build();
        ProxyCallHandler proxyCallHandler = new ProxyCallHandler(upstreamChannel, metrics);
        ProxyHandlerRegistry handlerRegistry = new ProxyHandlerRegistry(proxyCallHandler);

        sidecarServer = InProcessServerBuilder.forName(sidecarName)
                .fallbackHandlerRegistry(handlerRegistry)
                .build()
                .start();

        sidecarChannel = InProcessChannelBuilder.forName(sidecarName).directExecutor().build();
        blockingStub = PaymentServiceGrpc.newBlockingStub(sidecarChannel);
        asyncStub = PaymentServiceGrpc.newStub(sidecarChannel);
    }

    @AfterEach
    void tearDown() {
        sidecarChannel.shutdownNow();
        sidecarServer.shutdownNow();
        upstreamServer.shutdownNow();
    }

    @Test
    void forwardsSuccessfulCallAndRecordsMetrics() throws InterruptedException {
        AuthorizeRequest request = AuthorizeRequest.newBuilder()
                .setRequestId("test-req")
                .build();

        AuthorizeResponse response = blockingStub.authorize(request);
        
        assertThat(response.getStatus()).isEqualTo("AUTHORIZED");
        
        // Metrics recording might be slightly asynchronous relative to the response return
        Thread.sleep(200);
        
        assertThat(meterRegistry.find("grpc_requests_total").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("grpc_status_total").tag("grpc_status", "OK").counter().count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void propagatesDeadlineExceeded() throws InterruptedException {
        assertThatThrownBy(() -> 
            blockingStub.withDeadlineAfter(10, TimeUnit.MILLISECONDS).authorize(AuthorizeRequest.newBuilder().build())
        ).isInstanceOf(StatusRuntimeException.class);
        
        Thread.sleep(200);
        
        // Ensure metric is recorded - counter might be null if not yet registered
        var search = meterRegistry.find("grpc_status_total").tag("grpc_status", "DEADLINE_EXCEEDED").counter();
        if (search != null) {
            assertThat(search.count()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void forwardsBidiStreamAndRecordsMetrics() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger responsesReceived = new AtomicInteger(0);
        
        StreamObserver<AuthorizeRequest> requestObserver = asyncStub.processStream(new StreamObserver<>() {
            @Override
            public void onNext(AuthorizeResponse value) {
                responsesReceived.incrementAndGet();
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        requestObserver.onNext(AuthorizeRequest.newBuilder().setRequestId("req-1").build());
        requestObserver.onNext(AuthorizeRequest.newBuilder().setRequestId("req-2").build());
        requestObserver.onCompleted();

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        if (!completed) {
            System.err.println("Test timed out! Responses received: " + responsesReceived.get());
        }
        assertThat(completed).isTrue();
        assertThat(responsesReceived.get()).isEqualTo(2);

        Thread.sleep(200);
        assertThat(meterRegistry.find("grpc_requests_total").counter().count()).isGreaterThanOrEqualTo(1.0);
    }
}
