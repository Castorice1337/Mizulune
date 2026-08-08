package shit.zen.fabric.game;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.TeamColor;

/** Bridges the 26.2 split between text formatting and RGB team colors. */
public final class FabricTextCompat {
    private FabricTextCompat() {
    }

    public static Integer color(ChatFormatting formatting) {
        if (formatting == null) return null;
        TeamColor color = TeamColor.byName(formatting.name().toLowerCase(java.util.Locale.ROOT));
        return color == null ? null : color.rgb();
    }

    public static MutableComponent fromJson(String json) {
        if (json == null || json.isBlank()) return Component.empty();
        try {
            return ComponentSerialization.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result()
                .map(Component::copy)
                .orElseGet(() -> Component.literal(json));
        } catch (RuntimeException ignored) {
            return Component.literal(json);
        }
    }
}
