package shit.zen.config.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
    private final Supplier<List<Module>> modulesSupplier;
    private JsonObject preservedRootFields = new JsonObject();
    private JsonObject preservedUnknownModules = new JsonObject();

    public JsonValuesConfig() {
        this(() -> ZenClient.getInstance().getModuleManager().getModules());
    }

    JsonValuesConfig(Supplier<List<Module>> modulesSupplier) {
        super("settings.json");
        this.modulesSupplier = modulesSupplier;
    }

    @Override
    public void read(BufferedReader bufferedReader) throws IOException {
        this.preservedRootFields = new JsonObject();
        this.preservedUnknownModules = new JsonObject();
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonObject()) {
            throw new IOException("settings.json root is not an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        this.preserveRootFields(root);
        MusicConfigStore.read(root);
        JsonObject modules = object(root, "modules");
        if (modules == null) {
            throw new IOException("settings.json has no modules object");
        }
        for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("Ignoring malformed module config for {}", entry.getKey());
                continue;
            }
            JsonObject moduleObject = entry.getValue().getAsJsonObject();
            Module module = this.resolveModule(entry.getKey(), moduleObject);
            if (module == null) {
                LOGGER.warn("Preserving unknown module id {} in settings.json", entry.getKey());
                this.preservedUnknownModules.add(entry.getKey(), moduleObject.deepCopy());
                continue;
            }
            this.readModule(module, moduleObject);
        }
    }

    @Override
    public void save(BufferedWriter bufferedWriter) throws IOException {
        JsonObject root = this.copyPreservedRootFields();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject modules = new JsonObject();
        for (Module module : this.modules()) {
            modules.add(module.getId(), this.writeModule(module));
        }
        for (Map.Entry<String, JsonElement> entry : this.preservedUnknownModules.entrySet()) {
            if (!modules.has(entry.getKey())) {
                modules.add(entry.getKey(), entry.getValue().deepCopy());
            }
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
        module.notifyConfigLoaded();
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
            if ("type".equals(entry.getKey())
                    || "enabled".equals(entry.getKey()) && group instanceof ToggleValueGroup) {
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
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = Value.normalizeId(id);
        for (Module module : this.modules()) {
            if (module.getId().equals(normalized)
                    || Value.normalizeId(module.getName()).equals(normalized)
                    || module.getName().replace(" ", "").equalsIgnoreCase(id)
                    || Value.normalizeId(module.getClass().getSimpleName()).equals(normalized)) {
                return module;
            }
        }
        return null;
    }

    private Module resolveModule(String entryKey, JsonObject object) {
        Module module = this.findModule(entryKey);
        if (module != null) {
            return module;
        }
        module = this.findModule(readString(object, "displayName"));
        if (module != null) {
            LOGGER.info("Migrating config module key {} to stable id {}", entryKey, module.getId());
            return module;
        }
        module = this.findModule(readString(object, "id"));
        if (module != null) {
            LOGGER.info("Migrating config module key {} to stable id {}", entryKey, module.getId());
        }
        return module;
    }

    private List<Module> modules() {
        try {
            List<Module> modules = this.modulesSupplier.get();
            return modules == null ? List.of() : modules;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        try {
            return object.get(key).getAsString();
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
