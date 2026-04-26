package io.github.shivam61.grpcobs.sidecar;

import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyHandlerRegistry extends HandlerRegistry {
    
    private final ProxyCallHandler proxyCallHandler;
    
    public ProxyHandlerRegistry(ProxyCallHandler proxyCallHandler) {
        this.proxyCallHandler = proxyCallHandler;
    }

    @Override
    public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
        MethodDescriptor<byte[], byte[]> methodDescriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(methodName)
                .setRequestMarshaller(new ByteArrayMarshaller())
                .setResponseMarshaller(new ByteArrayMarshaller())
                .build();
                
        return ServerMethodDefinition.create(methodDescriptor, proxyCallHandler);
    }
    
    public static class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read InputStream", e);
            }
        }
    }
}
