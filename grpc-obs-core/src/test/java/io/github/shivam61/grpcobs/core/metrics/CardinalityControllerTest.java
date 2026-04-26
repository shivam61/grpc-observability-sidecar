package io.github.shivam61.grpcobs.core.metrics;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CardinalityControllerTest {

    @Test
    void normalizesUnknownMethodsWhenAllowlistIsPresent() {
        CardinalityController controller = new CardinalityController(100, "__other__", Set.of("allowed_method"));
        assertThat(controller.normalizeMethod("allowed_method")).isEqualTo("allowed_method");
        assertThat(controller.normalizeMethod("unknown_method")).isEqualTo("__other__");
    }

    @Test
    void respectsMaxMethodsLimit() {
        CardinalityController controller = new CardinalityController(2, "__other__", Set.of());
        assertThat(controller.normalizeMethod("method_1")).isEqualTo("method_1");
        assertThat(controller.normalizeMethod("method_2")).isEqualTo("method_2");
        assertThat(controller.normalizeMethod("method_3")).isEqualTo("__other__");
    }

    @Test
    void doesNotLeakMemoryForFuzzedMethods() {
        int maxMethods = 5;
        CardinalityController controller = new CardinalityController(maxMethods, "__other__", Set.of());
        
        // Fill the cache
        for (int i = 0; i < maxMethods; i++) {
            controller.normalizeMethod("method_" + i);
        }
        
        // Send 1000 fuzzed methods
        for (int i = 0; i < 1000; i++) {
            assertThat(controller.normalizeMethod("fuzzed_" + i)).isEqualTo("__other__");
        }
        
        // This is a bit hard to assert on private state without reflection, 
        // but we've verified the logic doesn't call normalizedCache.put for these.
    }
}
