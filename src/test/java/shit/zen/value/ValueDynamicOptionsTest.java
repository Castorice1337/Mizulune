package shit.zen.value;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ValueDynamicOptionsTest {
    @Test
    void optionsSupplierIsEvaluatedWithoutExposingMutableState() {
        AtomicReference<List<String>> options = new AtomicReference<>(List.of("alpha"));
        Value<String> value = new Value<>("profile", "Profile", "", "", ValueType.TEXT)
            .metadata("optionsSupplier", (java.util.function.Supplier<List<String>>)options::get)
            .metadata("dropdown", true);

        assertEquals(List.of("alpha"), value.getOptions());
        options.set(List.of("beta", "gamma", "beta"));
        assertEquals(List.of("beta", "gamma"), value.getOptions());
        options.set(null);
        assertEquals(List.of(), value.getOptions());
    }
}
