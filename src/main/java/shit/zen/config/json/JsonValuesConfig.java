package shit.zen.config.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.config.Config;
import shit.zen.hud.HudElement;
import shit.zen.music.config.MusicConfigStore;
import shit.zen.modules.Module;
import shit.zen.value.ModeValueGroup;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.ValueJsonCodec;

public class JsonValuesConfig extends Config {
    private static final Logger LOGGER = LogManager.getLogger(JsonValuesConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 1;
    private JsonObject preservedRootFields = new JsonObject();

    public JsonValuesConfig() {
        super("settings.json");
    }

    @Override
    public void read(BufferedReader bufferedReader) throws IOException {
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonObject()) {
            LOGGER.warn("settings.json root is not an object, ignoring");
            return;
        }
        JsonObject root = parsed.getAsJsonObject();
        this.preserveRootFields(root);
        MusicConfigStore.read(root);
        JsonObject modules = object(root, "modules");
        if (modules == null) {
            LOGGER.warn("settings.json has no modules object, ignoring");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
            Module module = this.findModule(entry.getKey());
            if (module == null) {
                LOGGER.warn("Ignoring unknown module id {} in settings.json", entry.getKey());
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("Ignoring malformed module config for {}", entry.getKey());
                continue;
            }
            this.readModule(module, entry.getValue().getAsJsonObject());
        }
    }

    @Override
    public void save(BufferedWriter bufferedWriter) throws IOException {
        JsonObject root = this.copyPreservedRootFields();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject modules = new JsonObject();
        for (Module module : ZenClient.getInstance().getModuleManager().getModules()) {
            modules.add(module.getId(), this.writeModule(module));
        }
        root.add("modules", modules);
        MusicConfigStore.write(root);
        GSON.toJson(root, bufferedWriter);
    }

    private void preserveRootFields(JsonObject root) {
        this.preservedRootFields = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if ("schema".equals(entry.getKey()) || "modules".equals(entry.getKey()) || "music".equals(entry.getKey())) {
                continue;
            }
            this.preservedRootFields.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private JsonObject copyPreservedRootFields() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : this.preservedRootFields.entrySet()) {
            root.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return root;
    }

    private void readModule(Module module, JsonObject object) {
        if (object.has("key")) {
            try {
                module.setKey(object.get("key").getAsInt());
            } catch (Exception exception) {
                LOGGER.warn("Invalid key for module {}", module.getId(), exception);
            }
        }
        if (object.has("enabled")) {
            try {
                boolean enabled = object.get("enabled").getAsBoolean();
                if (module.isEnabled() != enabled) {
                    module.setEnabled(enabled);
                }
            } catch (Exception exception) {
                LOGGER.warn("Invalid enabled state for module {}", module.getId(), exception);
            }
        }
        if (module instanceof HudElement hudElement) {
            JsonObject hud = object(object, "hud");
            if (hud != null) {
                tryReadFloat(hud, "x", hudElement::setX, module.getId());
                tryReadFloat(hud, "y", hudElement::setY, module.getId());
            }
        }
        JsonObject values = object(object, "values");
        if (values != null) {
            this.readGroup(values, module.getValueTree(), module.getId());
        }
    }

    private JsonObject writeModule(Module module) {
        JsonObject object = new JsonObject();
        object.addProperty("id", module.getId());
        object.addProperty("displayName", module.getName());
        object.addProperty("category", module.getCategory().name());
        object.addProperty("key", module.getKey());
        object.addProperty("enabled", module.isEnabled());
        if (module instanceof HudElement hudElement) {
            JsonObject hud = new JsonObject();
            hud.addProperty("x", hudElement.getX());
            hud.addProperty("y", hudElement.getY());
            hud.addProperty("width", hudElement.getWidth());
            hud.addProperty("height", hudElement.getHeight());
            object.add("hud", hud);
        }
        object.add("values", this.writeGroup(module.getValueTree()));
        return object;
    }

    private JsonObject writeGroup(ValueGroup group) {
        JsonObject object = new JsonObject();
        object.addProperty("type", ValueJsonCodec.typeName(group.getType()));
        if (group instanceof ToggleValueGroup toggleGroup) {
            object.addProperty("enabled", toggleGroup.isEnabled());
        }
        if (group instanceof ModeValueGroup modeGroup) {
            object.addProperty("mode", modeGroup.getActiveModeId());
        }
        JsonObject values = new JsonObject();
        if (group instanceof ModeValueGroup modeGroup) {
            for (Map.Entry<String, ValueGroup> mode : modeGroup.getModes().entrySet()) {
                values.add(mode.getKey(), this.writeGroup(mode.getValue()));
            }
        } else {
            for (Value<?> child : group.getChildren()) {
                values.add(child.getId(), this.writeNode(child));
            }
        }
        object.add("values", values);
        return object;
    }

    private JsonObject writeNode(Value<?> value) {
        if (value instanceof ValueGroup group) {
            return this.writeGroup(group);
        }
        JsonObject object = new JsonObject();
        object.addProperty("type", ValueJsonCodec.typeName(value.getType()));
        object.add("value", ValueJsonCodec.writeValue(value));
        return object;
    }

    private void readGroup(JsonObject object, ValueGroup group, String path) {
        if (group instanceof ToggleValueGroup toggleGroup && object.has("enabled")) {
            try {
                toggleGroup.setEnabled(object.get("enabled").getAsBoolean());
            } catch (Exception exception) {
                LOGGER.warn("Invalid toggle group state for {}", path, exception);
            }
        }
        if (group instanceof ModeValueGroup modeGroup && object.has("mode")) {
            ValueJsonCodec.readInto(modeGroup.getActiveValue(), object.get("mode"), LOGGER, path + ".mode");
        }

        JsonObject values = object(object, "values");
        if (values == null) {
            values = object;
        }
        if (group instanceof ModeValueGroup modeGroup) {
            for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                String modeId = Value.normalizeId(entry.getKey());
                ValueGroup modeBranch = modeGroup.getModes().get(modeId);
                if (modeBranch == null) {
                    LOGGER.warn("Ignoring unknown mode branch {} under {}", entry.getKey(), path);
                    continue;
                }
                if (entry.getValue().isJsonObject()) {
                    this.readGroup(entry.getValue().getAsJsonObject(), modeBranch, path + "." + modeId);
                }
            }
            return;
        }

        Map<String, Value<?>> children = group.childMap();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            if ("type".equals(entry.getKey()) || "enabled".equals(entry.getKey()) || "mode".equals(entry.getKey())) {
                continue;
            }
            Value<?> child = children.get(Value.normalizeId(entry.getKey()));
            if (child == null) {
                child = group.findByAliasOrDisplayName(entry.getKey()).orElse(null);
            }
            if (child == null) {
                LOGGER.warn("Ignoring unknown value {} under {}", entry.getKey(), path);
                continue;
            }
            this.readNode(entry.getValue(), child, path + "." + child.getId());
        }
    }

    private void readNode(JsonElement element, Value<?> value, String path) {
        if (value instanceof ValueGroup group) {
            if (element.isJsonObject()) {
                this.readGroup(element.getAsJsonObject(), group, path);
            } else {
                LOGGER.warn("Ignoring non-object group value at {}", path);
            }
            return;
        }
        JsonElement valueElement = element;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type")) {
                String expected = ValueJsonCodec.typeName(value.getType());
                String actual = object.get("type").getAsString();
                if (!expected.equals(actual)) {
                    LOGGER.warn("Type mismatch for {}: expected {}, got {}", path, expected, actual);
                    return;
                }
            }
            if (object.has("value")) {
                valueElement = object.get("value");
            }
        }
        ValueJsonCodec.readInto(value, valueElement, LOGGER, path);
    }

    private Module findModule(String id) {
        try {
            return ZenClient.getInstance().getModuleManager().getModule(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static void tryReadFloat(JsonObject object, String key, FloatConsumer consumer, String moduleId) {
        if (!object.has(key)) {
            return;
        }
        try {
            consumer.accept(object.get(key).getAsFloat());
        } catch (Exception exception) {
            LOGGER.warn("Invalid HUD {} for module {}", key, moduleId, exception);
        }
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }
}
