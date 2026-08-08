package shit.zen.modules.impl.render;

import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.render.nametag.NameTagStyle;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;
import shit.zen.event.EventTarget;

public class NameTags
extends Module {
    public static NameTags INSTANCE;
    public final ModeValue styleSetting = new ModeValue("Style", "Opal", "Simple").withDefault("Opal");
    public final NumberValue scaleSetting = new NumberValue("Scale", 0.3, 0.1, 1.0, 0.01);
    public final NumberValue distanceSetting = new NumberValue("Max Distance", 64.0, 8.0, 256.0, 1.0);
    public final BooleanValue showHealthSetting = new BooleanValue("Invisibles", false);
    public final BooleanValue showArmorSetting = new BooleanValue("Show Artifacts", true);
    public final BooleanValue showPingSetting = new BooleanValue("Hide Teammates", false);

    public NameTags() {
        super("NameTags", Category.RENDER);
        INSTANCE = this;
        NameTagStyle.registerStyles();
    }

    @Override
    public void onEnable() {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onEnable();
        }
    }

    @Override
    public void onDisable() {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onDisable();
        }
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onRender(renderEvent);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onRender2D(render2DEvent);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onPacket(packetEvent);
        }
    }
}
