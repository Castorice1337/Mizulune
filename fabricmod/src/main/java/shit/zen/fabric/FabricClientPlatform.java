package shit.zen.fabric;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import shit.zen.platform.ClientPlatform;

/** Fabric implementation of the small shared loader boundary. */
public final class FabricClientPlatform implements ClientPlatform {
    public static final FabricClientPlatform INSTANCE = new FabricClientPlatform();

    private FabricClientPlatform() {
    }

    @Override
    public String loaderId() {
        return "fabric-mixin";
    }

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }
}
