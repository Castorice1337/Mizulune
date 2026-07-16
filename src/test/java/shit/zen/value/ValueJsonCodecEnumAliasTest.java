package shit.zen.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.junit.jupiter.api.Test;
import shit.zen.value.impl.ModeValue;

final class ValueJsonCodecEnumAliasTest {
    @Test
    void enumAliasMapsRemovedOptionToReplacement() {
        ModeValue mode = new ModeValue("Mode", "Normal", "Telly")
                .withDefault("Normal");
        mode.metadata("optionAliases", Map.of(
                "Telly Bridge", "Telly",
                "Old Telly", "Normal",
                "New Telly", "Normal",
                "Keep Y", "Normal"));

        assertTrue(ValueJsonCodec.readInto(mode, new JsonPrimitive("Old Telly"), null, "scaffold.mode"));
        assertEquals("Normal", mode.getValue());
        assertTrue(ValueJsonCodec.readInto(mode, new JsonPrimitive("New Telly"), null, "scaffold.mode"));
        assertEquals("Normal", mode.getValue());
        assertTrue(ValueJsonCodec.readInto(mode, new JsonPrimitive("Keep Y"), null, "scaffold.mode"));
        assertEquals("Normal", mode.getValue());
        assertTrue(ValueJsonCodec.readInto(mode, new JsonPrimitive("Telly Bridge"), null, "scaffold.mode"));
        assertEquals("Telly", mode.getValue());
    }
}
