package shit.zen.modules.impl.render;

import net.minecraft.resources.ResourceLocation;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;

public class NoRender extends Module {
    public static NoRender INSTANCE;

    private static final String FIRE_STATUS_KEY = "norender.fire";
    private static final String PUMPKIN_BLUR_PATH = "textures/misc/pumpkinblur.png";
    private static final String POWDER_SNOW_PATH = "textures/misc/powder_snow_outline.png";

    private final BooleanValue darkness = new BooleanValue("Darkness", true);
    private final BooleanValue blindness = new BooleanValue("Blindness", true);
    private final BooleanValue fire = new BooleanValue("Fire", true);
    private final BooleanValue nausea = new BooleanValue("Nausea", true);
    private final BooleanValue pumpkin = new BooleanValue("Pumpkin", true);
    private final BooleanValue portal = new BooleanValue("Nether Portal", true);
    private final BooleanValue blockOverlay = new BooleanValue("Block Overlay", true);
    private final BooleanValue powderSnow = new BooleanValue("Powder Snow", true);
    private final BooleanValue fireNotification = new BooleanValue("Fire Notification", true);

    public NoRender() {
        super("NoRender", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup effects = root.group("effects", "Effects");
        effects.add(this.darkness);
        effects.add(this.blindness);

        ValueGroup overlays = root.group("overlays", "Overlays");
        overlays.add(this.fire);
        overlays.add(this.nausea);
        overlays.add(this.pumpkin);
        overlays.add(this.portal);
        overlays.add(this.blockOverlay);
        overlays.add(this.powderSnow);

        ValueGroup alerts = root.group("alerts", "Alerts");
        alerts.add(this.fireNotification);
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        boolean burning = this.fireNotification.getValue()
                && mc.player != null
                && mc.player.isOnFire();
        Notification.showPersistentStatus(
                FIRE_STATUS_KEY,
                "On Fire",
                "Burning",
                0xFFFF9F43,
                Notification.IconType.WARNING,
                burning);
    }

    @Override
    protected void onDisable() {
        Notification.clearPersistentStatus(FIRE_STATUS_KEY);
    }

    public static boolean shouldHideDarkness() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.darkness);
    }

    public static boolean shouldHideBlindness() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.blindness);
    }

    public static boolean shouldHideFireOverlay() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.fire);
    }

    public static boolean shouldHideNausea() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.nausea);
    }

    public static boolean shouldHidePortalOverlay() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.portal);
    }

    public static boolean shouldHideBlockOverlay() {
        return isOptionEnabled(INSTANCE == null ? null : INSTANCE.blockOverlay);
    }

    public static boolean shouldCancelTextureOverlay(ResourceLocation texture) {
        if (texture == null || INSTANCE == null || !INSTANCE.isEnabled()) {
            return false;
        }
        String path = texture.getPath();
        if (PUMPKIN_BLUR_PATH.equals(path)) {
            return INSTANCE.pumpkin.getValue();
        }
        if (POWDER_SNOW_PATH.equals(path)) {
            return INSTANCE.powderSnow.getValue();
        }
        return false;
    }

    private static boolean isOptionEnabled(BooleanValue value) {
        return INSTANCE != null && INSTANCE.isEnabled() && value != null && Boolean.TRUE.equals(value.getValue());
    }
}
