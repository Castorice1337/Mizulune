package shit.zen.hook;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import shit.zen.modules.impl.render.NameProtect;

/** Shared component JSON name-protection hook. */
public final class NetworkSerializationHookCallbacks {
    private NetworkSerializationHookCallbacks() {
    }

    public static MutableComponent readComponentJson(String json) {
        return Component.Serializer.fromJson(NameProtect.replacePlayerName(json));
    }
}
