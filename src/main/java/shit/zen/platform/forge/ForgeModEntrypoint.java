package shit.zen.platform.forge;

import net.minecraftforge.fml.common.Mod;
import shit.zen.ZenClient;

/** Forge metadata entrypoint kept out of the loader-neutral client core. */
@Mod("hey")
public final class ForgeModEntrypoint {
    public ForgeModEntrypoint() {
        ForgeClientPlatform.install();
        ZenClient.bootstrap();
        ForgeAsmBootstrap.install();
    }
}
