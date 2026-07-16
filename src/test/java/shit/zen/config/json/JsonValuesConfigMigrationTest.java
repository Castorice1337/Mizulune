package shit.zen.config.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import shit.zen.modules.Category;
import shit.zen.modules.Module;

final class JsonValuesConfigMigrationTest {
    @Test
    void oldRandomModuleKeyMigratesByDisplayName() throws Exception {
        TestModule module = new TestModule("BridgeAssist");
        JsonValuesConfig config = new JsonValuesConfig(() -> List.of(module));

        config.read(reader("""
                {
                  "schema": 1,
                  "modules": {
                    "xq_random_runtime_name": {
                      "id": "xq_random_runtime_name",
                      "displayName": "BridgeAssist",
                      "category": "MISC",
                      "key": 71
                    }
                  }
                }
                """));

        JsonObject root = save(config);
        JsonObject modules = root.getAsJsonObject("modules");
        assertTrue(modules.has("bridge_assist"));
        assertFalse(modules.has("xq_random_runtime_name"));
        assertEquals(71, module.getKey());
    }

    @Test
    void unknownModuleConfigIsPreservedOnSave() throws Exception {
        TestModule module = new TestModule("BridgeAssist");
        JsonValuesConfig config = new JsonValuesConfig(() -> List.of(module));

        config.read(reader("""
                {
                  "schema": 1,
                  "modules": {
                    "unknown_future_module": {
                      "id": "unknown_future_module",
                      "displayName": "Future Module",
                      "custom": true
                    }
                  }
                }
                """));

        JsonObject root = save(config);
        JsonObject modules = root.getAsJsonObject("modules");
        assertTrue(modules.has("bridge_assist"));
        assertTrue(modules.has("unknown_future_module"));
        assertTrue(modules.getAsJsonObject("unknown_future_module").get("custom").getAsBoolean());
    }

    private static BufferedReader reader(String json) {
        return new BufferedReader(new StringReader(json));
    }

    private static JsonObject save(JsonValuesConfig config) throws Exception {
        StringWriter out = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(out)) {
            config.save(writer);
        }
        return JsonParser.parseString(out.toString()).getAsJsonObject();
    }

    private static final class TestModule extends Module {
        private TestModule(String name) {
            super(name, Category.MISC);
            this.registerSettings();
        }
    }
}
