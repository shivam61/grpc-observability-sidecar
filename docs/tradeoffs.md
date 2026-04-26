## Byte-array Marshalling vs. Zero-Copy
- **Current Approach:** We use a `ByteArrayMarshaller` which reads the entire `InputStream` into a `byte[]`. This avoids Protobuf object allocation and deserialization, but still performs a heap allocation and a memory copy.
- **Staff-level Optimization:** A true Zero-Copy proxy would use Netty's `FileRegion` or direct `ByteBuf` slices. However, gRPC-Java's abstraction layers make it difficult to access the underlying transport buffers without using internal API hooks.

## Metrics Registry Overhead
- **Caching:** We cache `Counter` and `Timer` instances in `SidecarMetrics` using `ConcurrentHashMap`. This avoids the overhead of Micrometer's builder pattern and registry lookups on the hot path (every request).


## Histograms
- **Prometheus Histograms:** Fixed buckets. Easy to query, but bad bucket choices lead to inaccurate percentiles.
- **HdrHistogram:** Dynamic range, highly accurate percentiles. Memory bound. Not natively supported by Prometheus scraping without summary pre-calculation (which breaks aggregation).

## Labels and Cardinality
- Adding dynamic labels (e.g. `user_id` or `client_ip`) will break Prometheus via cardinality explosion.
- The `CardinalityController` bounds unique method names.

## Limitations
- Only Unary RPC is supported.
- Streaming (Server, Client, Bidi) is roadmap.
- No mTLS support between sidecar and upstream yet.
