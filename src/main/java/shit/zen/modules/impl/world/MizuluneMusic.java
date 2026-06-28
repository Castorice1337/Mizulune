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
    public final BooleanValue playOnIsland = new BooleanValue("Play on Island", false);

    public MizuluneMusic() {
        super("Mizulune Music", Category.WORLD);
        INSTANCE = this;
    }

    public boolean useLiquidGlass() {
        return Boolean.TRUE.equals(this.liquidGlass.getValue());
    }

    public boolean shouldPlayOnIsland() {
        return Boolean.TRUE.equals(this.playOnIsland.getValue());
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup visual = root.group("visual", "Visual");
        visual.add(this.liquidGlass);
        visual.add(this.playOnIsland);
    }

    @Override
    protected boolean defaultHiddenInModuleList() {
        return true;
    }

    @Override
    protected void onEnable() {
        try {
            if (!ZenClient.getInstance().getMusicService().config().isEnabled()) {
                LOGGER.info("Mizulune Music is disabled by music config");
                return;
            }
            mc.setScreen(new MusicScreen(ZenClient.getInstance().getMusicService(), this));
        } catch (Exception exception) {
            LOGGER.error("Failed to open Mizulune Music", exception);
        } finally {
            this.setEnabled(false);
        }
    }
}
