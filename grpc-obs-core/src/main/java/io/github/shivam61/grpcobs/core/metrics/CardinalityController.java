package io.github.shivam61.grpcobs.core.metrics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CardinalityController {
    private final int maxMethods;
    private final String unknownMethodLabel;
    private final Set<String> allowlist;
    private final ConcurrentHashMap<String, String> normalizedCache = new ConcurrentHashMap<>();
    private final AtomicInteger uniqueMethodsCount = new AtomicInteger(0);

    public CardinalityController(int maxMethods, String unknownMethodLabel, Set<String> allowlist) {
        this.maxMethods = maxMethods;
        this.unknownMethodLabel = unknownMethodLabel;
        this.allowlist = allowlist;
    }

    public String normalizeMethod(String rawMethodName) {
        String cached = normalizedCache.get(rawMethodName);
        if (cached != null) {
            return cached;
        }

        if (allowlist != null && !allowlist.isEmpty() && !allowlist.contains(rawMethodName)) {
            return unknownMethodLabel;
        }

        if (uniqueMethodsCount.get() >= maxMethods) {
            return unknownMethodLabel;
        }

        // Only cache if we are within bounds
        int count = uniqueMethodsCount.incrementAndGet();
        if (count > maxMethods) {
            uniqueMethodsCount.decrementAndGet();
            return unknownMethodLabel;
        }

        normalizedCache.put(rawMethodName, rawMethodName);
        return rawMethodName;
    }
}
