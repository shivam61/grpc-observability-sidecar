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
}
