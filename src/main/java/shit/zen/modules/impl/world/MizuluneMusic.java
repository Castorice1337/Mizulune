package shit.zen.modules.impl.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.music.ui.MusicScreen;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;

public class MizuluneMusic extends Module {
    private static final Logger LOGGER = LogManager.getLogger(MizuluneMusic.class);
    public static MizuluneMusic INSTANCE;

    public final BooleanValue liquidGlass = new BooleanValue("Liquid Glass", false);

    public MizuluneMusic() {
        super("Mizulune Music", Category.WORLD);
        INSTANCE = this;
    }

    public boolean useLiquidGlass() {
        return Boolean.TRUE.equals(this.liquidGlass.getValue());
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        root.group("visual", "Visual").add(this.liquidGlass);
    }

    @Override
    protected boolean defaultHiddenInModuleList() {
        return true;
    }

    @Override
    protected void onEnable() {
        try {
            mc.setScreen(new MusicScreen(ZenClient.getInstance().getMusicService(), this));
        } catch (Exception exception) {
            LOGGER.error("Failed to open Mizulune Music", exception);
        } finally {
            this.setEnabled(false);
        }
    }
}
