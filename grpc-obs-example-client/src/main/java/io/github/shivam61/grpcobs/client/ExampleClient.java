package io.github.shivam61.grpcobs.client;

import io.github.shivam61.grpcobs.example.payment.AuthorizeRequest;
import io.github.shivam61.grpcobs.example.payment.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ExampleClient {
    private static final Logger logger = LoggerFactory.getLogger(ExampleClient.class);
    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub blockingStub;

    public ExampleClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void doAuthorize() {
        AuthorizeRequest request = AuthorizeRequest.newBuilder()
                .setRequestId("req_" + System.currentTimeMillis())
                .setUserId("user_123")
                .setAmount(100.50)
                .setCurrency("USD")
                .setPaymentMethod("CREDIT_CARD")
                .setExtraPayload(ByteString.copyFrom(new byte[1024])) // 1KB payload
                .build();

        try {
            blockingStub.authorize(request);
            logger.info("Authorized successfully");
        } catch (Exception e) {
            logger.warn("RPC failed: {}", e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String target = System.getProperty("target", "localhost:9090"); // Default to sidecar
        String[] parts = target.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        
        ExampleClient client = new ExampleClient(host, port);
        try {
            for (int i = 0; i < 100; i++) {
                client.doAuthorize();
                Thread.sleep(100);
            }
        } finally {
            client.shutdown();
        }
    }
}
