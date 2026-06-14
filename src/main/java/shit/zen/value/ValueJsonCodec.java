package shit.zen.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.Logger;

public final class ValueJsonCodec {
    private ValueJsonCodec() {
    }

    public static JsonElement writeValue(Value<?> value) {
        Object raw = value.getValue();
        if (raw == null) {
            return JsonNull.INSTANCE;
        }
        return switch (value.getType()) {
            case BOOLEAN -> new JsonPrimitive(Boolean.TRUE.equals(raw));
            case INTEGER, KEY_BIND -> new JsonPrimitive(((Number)raw).intValue());
            case DECIMAL -> new JsonPrimitive(((Number)raw).doubleValue());
            case TEXT, ENUM, ITEM, BLOCK, ENTITY_TYPE, SOUND_EVENT, FILE, RESOURCE_LOCATION -> new JsonPrimitive(String.valueOf(raw));
            case MULTI_ENUM -> writeStringArray((List<?>)raw);
            case COLOR -> writeColor((MizuColor)raw);
            case GRADIENT -> writeGradient((GradientSpec)raw);
            case INT_RANGE, DECIMAL_RANGE -> writeRange((NumericRange)raw);
            case VEC2 -> writeVec2((Vec2f)raw);
            case VEC3 -> writeVec3((Vec3f)raw);
            case GROUP, TOGGLE_GROUP, MODE_GROUP -> JsonNull.INSTANCE;
        };
    }

    public static boolean readInto(Value<?> value, JsonElement json, Logger logger, String path) {
        try {
            Object decoded = readValue(value, json);
            if (decoded == null && !(json == null || json.isJsonNull())) {
                return false;
            }
            value.setRawValue(decoded);
            return true;
        } catch (Exception exception) {
            if (logger != null) {
                logger.warn("Invalid value for {} ({}), using default/current value", path, value.getType(), exception);
            }
            return false;
        }
    }

    public static boolean readStringInto(Value<?> value, String raw, Logger logger, String path) {
        try {
            value.setRawValue(readString(value, raw));
            return true;
        } catch (Exception exception) {
            if (logger != null) {
                logger.warn("Invalid string value for {} ({}): {}", path, value.getType(), raw, exception);
            }
            return false;
        }
    }

    private static Object readValue(Value<?> value, JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return value.getDefaultValue();
        }
        return switch (value.getType()) {
            case BOOLEAN -> json.getAsBoolean();
            case INTEGER, KEY_BIND -> clampNumber(value, json.getAsInt(), true);
            case DECIMAL -> clampNumber(value, json.getAsDouble(), false);
            case TEXT, FILE, RESOURCE_LOCATION, ITEM, BLOCK, ENTITY_TYPE, SOUND_EVENT -> json.getAsString();
            case ENUM -> readEnum(value, json.getAsString());
            case MULTI_ENUM -> readMultiEnum(value, json);
            case COLOR -> readColor(json);
            case GRADIENT -> readGradient(json);
            case INT_RANGE -> readRange(json, true);
            case DECIMAL_RANGE -> readRange(json, false);
            case VEC2 -> readVec2(json);
            case VEC3 -> readVec3(json);
            case GROUP, TOGGLE_GROUP, MODE_GROUP -> value.getDefaultValue();
        };
    }

    private static Object readString(Value<?> value, String raw) {
        return switch (value.getType()) {
            case BOOLEAN -> Boolean.parseBoolean(raw);
            case INTEGER, KEY_BIND -> clampNumber(value, Double.parseDouble(raw), true);
            case DECIMAL -> clampNumber(value, Double.parseDouble(raw), false);
            case ENUM -> readEnum(value, raw);
            case MULTI_ENUM -> List.of(raw.split(","));
            case COLOR -> readColorString(raw);
            default -> raw;
        };
    }

    private static Number clampNumber(Value<?> value, double number, boolean integer) {
        double min = numberMetadata(value, "min", -Double.MAX_VALUE);
        double max = numberMetadata(value, "max", Double.MAX_VALUE);
        double clamped = Mth.clamp(number, min, max);
        return integer ? Math.round(clamped) : clamped;
    }

    private static double numberMetadata(Value<?> value, String key, double fallback) {
        Object metadataValue = value.getMetadata().get(key);
        return metadataValue instanceof Number number ? number.doubleValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static String readEnum(Value<?> value, String raw) {
        Object optionsObject = value.getMetadata().get("options");
        if (optionsObject instanceof List<?> options && !options.isEmpty()) {
            for (Object option : options) {
                if (String.valueOf(option).equalsIgnoreCase(raw) || Value.normalizeId(String.valueOf(option)).equals(Value.normalizeId(raw))) {
                    return String.valueOf(option);
                }
            }
            return String.valueOf(options.get(0));
        }
        return raw;
    }

    private static List<String> readMultiEnum(Value<?> value, JsonElement json) {
        List<String> values = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                String raw = element.getAsString();
                String normalized = readEnum(value, raw);
                if (!values.contains(normalized)) {
                    values.add(normalized);
                }
            }
        } else if (json.isJsonPrimitive()) {
            for (String raw : json.getAsString().split(",")) {
                if (!raw.isBlank()) {
                    values.add(readEnum(value, raw.trim()));
                }
            }
        }
        return values;
    }

    private static JsonArray writeStringArray(List<?> values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            array.add(String.valueOf(value));
        }
        return array;
    }

    private static JsonObject writeColor(MizuColor color) {
        JsonObject object = new JsonObject();
        object.addProperty("argb", color.toHexArgb());
        object.addProperty("rgba", color.toHexRgba());
        object.addProperty("r", color.red());
        object.addProperty("g", color.green());
        object.addProperty("b", color.blue());
        object.addProperty("a", color.alpha());
        return object;
    }

    private static MizuColor readColor(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return MizuColor.fromHex(json.getAsString());
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("argb")) {
            return MizuColor.fromHex(object.get("argb").getAsString());
        }
        if (object.has("hex")) {
            return MizuColor.fromHex(object.get("hex").getAsString());
        }
        int r = object.has("r") ? object.get("r").getAsInt() : 255;
        int g = object.has("g") ? object.get("g").getAsInt() : 255;
        int b = object.has("b") ? object.get("b").getAsInt() : 255;
        int a = object.has("a") ? object.get("a").getAsInt() : 255;
        return MizuColor.ofArgb(a, r, g, b);
    }

    private static MizuColor readColorString(String raw) {
        String value = raw.trim();
        if (value.matches("-?\\d+")) {
            int rgb = Integer.parseInt(value) & 0x00FFFFFF;
            return MizuColor.ofArgb(0xFF000000 | rgb);
        }
        return MizuColor.fromHex(value);
    }

    private static JsonObject writeGradient(GradientSpec gradient) {
        JsonObject object = new JsonObject();
        object.add("start", writeColor(gradient.start()));
        object.add("end", writeColor(gradient.end()));
        return object;
    }

    private static GradientSpec readGradient(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        return new GradientSpec(readColor(object.get("start")), readColor(object.get("end")));
    }

    private static JsonObject writeRange(NumericRange range) {
        JsonObject object = new JsonObject();
        object.addProperty("lower", range.lower());
        object.addProperty("upper", range.upper());
        object.addProperty("min", range.min());
        object.addProperty("max", range.max());
        object.addProperty("step", range.step());
        return object;
    }

    private static NumericRange readRange(JsonElement json, boolean integer) {
        JsonObject object = json.getAsJsonObject();
        double min = object.has("min") ? object.get("min").getAsDouble() : -Double.MAX_VALUE;
        double max = object.has("max") ? object.get("max").getAsDouble() : Double.MAX_VALUE;
        double step = object.has("step") ? object.get("step").getAsDouble() : 1.0;
        return new NumericRange(object.get("lower").getAsDouble(), object.get("upper").getAsDouble(), min, max, step, integer);
    }

    private static JsonObject writeVec2(Vec2f value) {
        JsonObject object = new JsonObject();
        object.addProperty("x", value.x());
        object.addProperty("y", value.y());
        return object;
    }

    private static Vec2f readVec2(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        return new Vec2f(object.get("x").getAsFloat(), object.get("y").getAsFloat());
    }

    private static JsonObject writeVec3(Vec3f value) {
        JsonObject object = new JsonObject();
        object.addProperty("x", value.x());
        object.addProperty("y", value.y());
        object.addProperty("z", value.z());
        return object;
    }

    private static Vec3f readVec3(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        return new Vec3f(object.get("x").getAsFloat(), object.get("y").getAsFloat(), object.get("z").getAsFloat());
    }

    public static String typeName(ValueType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
