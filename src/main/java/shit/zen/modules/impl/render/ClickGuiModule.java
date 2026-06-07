package shit.zen.modules.impl.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.gui.NewClickGui;
import shit.zen.gui.OldClickGui;
import shit.zen.gui.PanelClickGui;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.ModeSetting;

public class ClickGuiModule
extends Module {
    public static final Logger LOGGER = LogManager.getLogger(ClickGuiModule.class);
    public final ModeSetting styleSetting = new ModeSetting("Mode", "Old", "Panel", "New").withDefault("Old");

    public ClickGuiModule() {
        super("ClickGui", Category.RENDER, 344);
    }

    @Override
    protected void onEnable() {
        try {
            if (this.styleSetting.is("Old")) {
                mc.setScreen(new OldClickGui());
            } else if (this.styleSetting.is("Panel")) {
                mc.setScreen(PanelClickGui.panelClickGui);
            } else {
                mc.setScreen(new NewClickGui());
            }
            LOGGER.info("ClickGUI opened successfully");
        } catch (Exception exception) {
            LOGGER.error("Error opening ClickGUI", exception);
        } finally {
            this.setEnabled(false);
        }
    }
}