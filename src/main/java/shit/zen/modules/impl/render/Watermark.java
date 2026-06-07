package shit.zen.modules.impl.render;

import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.hud.DynamicIsland;
import shit.zen.hud.NeverloseWatermark;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.event.EventTarget;

public class Watermark extends Module {
    final ModeSetting styleSetting = new ModeSetting("Style", "Neverlose", "DynamicIsland").withDefault("DynamicIsland");
    private final DynamicIsland dynamicIsland = new DynamicIsland();
    private final NeverloseWatermark neverloseWatermark = new NeverloseWatermark();

    public Watermark() {
        super("Watermark", Category.RENDER);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        if (!this.isEnabled()) {
            return;
        }
        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                this.neverloseWatermark.onRender2D(render2DEvent);
                break;
            case "DynamicIsland":
                this.dynamicIsland.onRender2D(render2DEvent);
                break;
        }
    }

    @EventTarget
    public void onGlRender(GlRenderEvent glRenderEvent) {
        if (!this.isEnabled()) {
            return;
        }
        if ("Neverlose".equals(this.styleSetting.getValue())) {
            this.neverloseWatermark.onGlRender(glRenderEvent);
        }
    }
}
