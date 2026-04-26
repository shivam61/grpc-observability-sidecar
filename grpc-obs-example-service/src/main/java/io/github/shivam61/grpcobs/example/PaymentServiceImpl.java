package io.github.shivam61.grpcobs.example;

import io.github.shivam61.grpcobs.example.payment.AuthorizeRequest;
import io.github.shivam61.grpcobs.example.payment.AuthorizeResponse;
import io.github.shivam61.grpcobs.example.payment.CaptureRequest;
import io.github.shivam61.grpcobs.example.payment.CaptureResponse;
import io.github.shivam61.grpcobs.example.payment.PaymentServiceGrpc;
import io.github.shivam61.grpcobs.example.payment.RefundRequest;
import io.github.shivam61.grpcobs.example.payment.RefundResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Random;

public class PaymentServiceImpl extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final Random random = new Random();

    @Override
    public void authorize(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
        simulateWork();
        if (random.nextDouble() < 0.05) { // 5% error rate
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Simulated unavailable error").asRuntimeException());
            return;
        }
        
        AuthorizeResponse response = AuthorizeResponse.newBuilder()
                .setTransactionId("txn_" + System.currentTimeMillis())
                .setStatus("AUTHORIZED")
                .setReason("Looks good")
                .setExtraPayload(request.getExtraPayload()) // Echo for size testing
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void capture(CaptureRequest request, StreamObserver<CaptureResponse> responseObserver) {
        simulateWork();
        if (random.nextDouble() < 0.02) { // 2% error rate
            responseObserver.onError(Status.INTERNAL.withDescription("Simulated internal error").asRuntimeException());
            return;
        }

        CaptureResponse response = CaptureResponse.newBuilder()
                .setStatus("CAPTURED")
                .setExtraPayload(request.getExtraPayload())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void refund(RefundRequest request, StreamObserver<RefundResponse> responseObserver) {
        simulateWork();
        RefundResponse response = RefundResponse.newBuilder()
                .setStatus("REFUNDED")
                .setExtraPayload(request.getExtraPayload())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private void simulateWork() {
        try {
            // Simulate variable latency: 1ms to 20ms
            Thread.sleep(1 + random.nextInt(20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
