package io.github.shivam61.grpcobs.sidecar;

import io.github.shivam61.grpcobs.core.metrics.SidecarMetrics;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyCallHandler implements ServerCallHandler<byte[], byte[]> {
    private static final Logger logger = LoggerFactory.getLogger(ProxyCallHandler.class);
    
    private final Channel upstreamChannel;
    private final SidecarMetrics metrics;

    public ProxyCallHandler(Channel upstreamChannel, SidecarMetrics metrics) {
        this.upstreamChannel = upstreamChannel;
        this.metrics = metrics;
    }

    @Override
    public ServerCall.Listener<byte[]> startCall(ServerCall<byte[], byte[]> call, Metadata headers) {
        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startTimeNanos = System.nanoTime();
        
        metrics.recordRequestStarted(methodName);

        ClientCall<byte[], byte[]> clientCall = upstreamChannel.newCall(call.getMethodDescriptor(), CallOptions.DEFAULT);

        ProxyServerCallListener serverCallListener = new ProxyServerCallListener(call, clientCall, methodName, startTimeNanos, metrics);
        
        clientCall.start(new ClientCall.Listener<byte[]>() {
            @Override
            public void onHeaders(Metadata headers) {
                call.sendHeaders(headers);
            }

            @Override
            public void onMessage(byte[] message) {
                metrics.recordResponseBytes(methodName, message.length);
                call.sendMessage(message);
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                serverCallListener.onUpstreamClose(status, trailers);
            }

            @Override
            public void onReady() {
                if (call.isReady()) {
                    call.request(1);
                }
            }
        }, headers);

        call.request(1);
        clientCall.request(1);

        return serverCallListener;
    }

    private static class ProxyServerCallListener extends ServerCall.Listener<byte[]> {
        private final ServerCall<byte[], byte[]> serverCall;
        private final ClientCall<byte[], byte[]> clientCall;
        private final String methodName;
        private final long startTimeNanos;
        private final SidecarMetrics metrics;
        private boolean closed = false;

        public ProxyServerCallListener(ServerCall<byte[], byte[]> serverCall,
                                       ClientCall<byte[], byte[]> clientCall,
                                       String methodName,
                                       long startTimeNanos,
                                       SidecarMetrics metrics) {
            this.serverCall = serverCall;
            this.clientCall = clientCall;
            this.methodName = methodName;
            this.startTimeNanos = startTimeNanos;
            this.metrics = metrics;
        }

        @Override
        public void onMessage(byte[] message) {
            metrics.recordRequestBytes(methodName, message.length);
            clientCall.sendMessage(message);
        }

        @Override
        public void onHalfClose() {
            clientCall.halfClose();
        }

        @Override
        public void onCancel() {
            clientCall.cancel("Client cancelled", null);
            finishCall(Status.CANCELLED, new Metadata());
        }

        @Override
        public void onComplete() {
            // Wait for upstream to close
        }

        @Override
        public void onReady() {
            if (clientCall.isReady()) {
                clientCall.request(1);
            }
        }

        public void onUpstreamClose(Status status, Metadata trailers) {
            finishCall(status, trailers);
        }
        
        private void finishCall(Status status, Metadata trailers) {
            if (closed) return;
            closed = true;
            long durationNanos = System.nanoTime() - startTimeNanos;
            
            try {
                serverCall.close(status, trailers);
            } catch (Exception e) {
                logger.warn("Error closing downstream call", e);
            }
            
            metrics.recordRequestCompleted(methodName, status.getCode().name(), durationNanos);
        }
    }
}
