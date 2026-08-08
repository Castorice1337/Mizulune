package shit.zen.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.AttackEvent;
import shit.zen.hud.target.TargetStyle;
import shit.zen.manager.TargetManager;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.misc.AimAssist;
import shit.zen.modules.impl.render.HUD;
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.event.EventTarget;

public class TargetHud
extends HudElement {
    public static TargetHud INSTANCE;
    private static final long MANUAL_TARGET_TIMEOUT_MS = 1800L;

    private final SmoothAnimationTimer healthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer healthLagAnim = new SmoothAnimationTimer();
    public static final Map<String, AtomicInteger> playerHealthMap = new HashMap<>();
    private LivingEntity healthTarget;
    private LivingEntity manualTarget;
    private long manualTargetTime;
    private float lastHealth;
    private float healthDelta;
    private final ModeValue styleMode = new ModeValue("Mode", "Round", "Lite", "Modern").withDefault("Lite");
    private final BooleanValue liquidGlass = new BooleanValue("Liquid Glass", true, () -> this.styleMode.is("Lite"));
    private Value<Boolean> onlyShowOnKillAura;
    private Value<Boolean> modernGlow;
    private Value<Boolean> modernBackgroundBlur;
    private Value<Boolean> modernUseClientColor;
    private Value<MizuColor> modernHealthStart;
    private Value<MizuColor> modernHealthEnd;
    private Value<MizuColor> modernGlowColor;

    public TargetHud() {
        super("TargetHUD");
        INSTANCE = this;
        this.setWidth(200.0f);
        this.setHeight(60.0f);
        TargetStyle.initStyles();
        this.setEnabled(true);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup visibility = root.group("visibility", "Visibility");
        this.onlyShowOnKillAura = visibility.bool("only_show_on_killaura", "Only Show On KillAura", true)
                .alias("Only Show On KillAura");

        ValueGroup modern = root.group("modern_thud", "Modern THUD");
        modern.visibleWhen(this::isModernStyle);
        this.modernGlow = modern.bool("glow", "Glow", true).alias("Glow");
        this.modernBackgroundBlur = modern.bool("background_blur", "Background Blur", true)
                .alias("Background Blur");
        this.modernUseClientColor = modern.bool("use_client_color", "Use Client Color", true)
                .alias("Use Client Color");
        this.modernHealthStart = modern.color("health_start", "Health Start", HUD.DEFAULT_CLIENT_COLOR_START)
                .alias("Health Start")
                .visibleWhen(() -> !this.useModernClientColor());
        this.modernHealthEnd = modern.color("health_end", "Health End", HUD.DEFAULT_CLIENT_COLOR_END)
                .alias("Health End")
                .visibleWhen(() -> !this.useModernClientColor());
        this.modernGlowColor = modern.color("glow_color", "Glow Color", HUD.DEFAULT_CLIENT_GLOW_COLOR)
                .alias("Glow Color")
                .visibleWhen(() -> this.isModernGlowEnabled() && !this.useModernClientColor());
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        Packet<?> packet = packetEvent.getPacket();
        if (packet instanceof ClientboundSetScorePacket clientboundSetScorePacket) {
            if (mc.level != null && mc.player != null && ("belowHealth".equals(clientboundSetScorePacket.getObjectiveName()) || "health".equals(clientboundSetScorePacket.getObjectiveName())) && !clientboundSetScorePacket.getOwner().equals(mc.player.getGameProfile().getName())) {
                playerHealthMap.computeIfAbsent(clientboundSetScorePacket.getOwner(), string -> new AtomicInteger()).set(clientboundSetScorePacket.getScore());
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!event.isPost() || !(event.getTarget() instanceof LivingEntity livingEntity)) {
            return;
        }
        if (!this.isRenderableTarget(livingEntity, false)) {
            return;
        }
        this.manualTarget = livingEntity;
        this.manualTargetTime = System.currentTimeMillis();
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        float maxHealth;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || !playerHealthMap.containsKey(player.getName().getString())) continue;
            player.setHealth((float)Math.max(1, playerHealthMap.get(player.getName().getString()).get()));
        }
        LivingEntity target = this.resolveTarget();
        if (target != null) {
            if (this.healthTarget != target) {
                this.healthTarget = target;
                this.lastHealth = target.getHealth();
                this.healthDelta = 0.0f;
            }
            if (!Mth.equal(this.lastHealth, target.getHealth())) {
                this.healthDelta = target.getHealth() - this.lastHealth;
                this.lastHealth = target.getHealth();
            }
            float currentHealth = Math.min(target.getHealth(), 20.0f);
            maxHealth = Math.min(target.getMaxHealth(), 20.0f);
            float ratio = maxHealth > 0.0f ? currentHealth / maxHealth : 0.0f;
            this.healthAnim.animate(ratio, 0.5, Easings.EASE_OUT_POW4);
            this.healthLagAnim.animate(ratio, 1.5, Easings.EASE_OUT_POW5);
        } else {
            this.healthTarget = null;
            this.healthDelta = 0.0f;
        }
        this.healthAnim.tick();
        this.healthLagAnim.tick();
        TargetStyle targetStyle = TargetStyle.getByName(this.styleMode.getValue());
        if (targetStyle == null) {
            this.styleMode.setValue("Lite");
            targetStyle = TargetStyle.getByName(this.styleMode.getValue());
        }
        if (targetStyle != null) {
            maxHealth = target != null ? (target.getMaxHealth() > 0.0f ? Math.min(target.getHealth(), 20.0f) / Math.min(target.getMaxHealth(), 20.0f) : 0.0f) : 0.0f;
            TargetStyle.Size size = targetStyle.render(render2DEvent, target, this.healthAnim, this.healthLagAnim, maxHealth, x, y, this.liquidGlass.getValue());
            this.setWidth(size.width());
            this.setHeight(size.height());
        }
    }

    private LivingEntity resolveTarget() {
        if (mc.screen instanceof ChatScreen) {
            return mc.player;
        }
        LivingEntity killAuraTarget = this.resolveKillAuraTarget();
        if (killAuraTarget != null) {
            return killAuraTarget;
        }
        if (this.onlyShowOnKillAura()) {
            return null;
        }
        LivingEntity aimAssistTarget = this.resolveAimAssistTarget();
        if (aimAssistTarget != null) {
            return aimAssistTarget;
        }
        return this.resolveManualTarget();
    }

    private LivingEntity resolveKillAuraTarget() {
        if (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled()) {
            return null;
        }
        Entity candidate = KillAura.aimingTarget;
        if (!(candidate instanceof LivingEntity)) {
            candidate = KillAura.target;
        }
        if (!(candidate instanceof LivingEntity) && KillAura.targetList != null) {
            candidate = KillAura.targetList.stream()
                    .filter(LivingEntity.class::isInstance)
                    .findFirst()
                    .orElse(null);
        }
        if (candidate instanceof LivingEntity livingEntity && this.isRenderableTarget(livingEntity, false)) {
            return livingEntity;
        }
        return null;
    }

    private LivingEntity resolveAimAssistTarget() {
        AimAssist aimAssist = AimAssist.INSTANCE;
        if (aimAssist == null || !aimAssist.isEnabled()) {
            return null;
        }
        LivingEntity target = aimAssist.getCurrentTarget();
        return this.isRenderableTarget(target, false) ? target : null;
    }

    private LivingEntity resolveManualTarget() {
        if (this.manualTarget == null || System.currentTimeMillis() - this.manualTargetTime > MANUAL_TARGET_TIMEOUT_MS) {
            this.manualTarget = null;
            return null;
        }
        if (!this.isRenderableTarget(this.manualTarget, false)) {
            this.manualTarget = null;
            return null;
        }
        return this.manualTarget;
    }

    private boolean isRenderableTarget(LivingEntity target, boolean allowSelf) {
        if (target == null || target.isRemoved() || !target.isAlive() || target.isDeadOrDying() || target.getHealth() <= 0.0f) {
            return false;
        }
        if (target == mc.player) {
            return allowSelf;
        }
        return TargetManager.INSTANCE == null || TargetManager.INSTANCE.isValidTarget(target);
    }

    private boolean onlyShowOnKillAura() {
        return this.onlyShowOnKillAura == null || this.onlyShowOnKillAura.getValue();
    }

    private boolean isModernStyle() {
        return this.styleMode.is("Modern");
    }

    public boolean isModernGlowEnabled() {
        return this.modernGlow == null || this.modernGlow.getValue();
    }

    public boolean isModernBackgroundBlurEnabled() {
        return this.modernBackgroundBlur == null || this.modernBackgroundBlur.getValue();
    }

    public boolean useModernClientColor() {
        return this.modernUseClientColor == null || this.modernUseClientColor.getValue();
    }

    public MizuColor modernHealthStart() {
        return this.useModernClientColor() || this.modernHealthStart == null
                ? HUD.clientColorStart()
                : this.modernHealthStart.getValue();
    }

    public MizuColor modernHealthEnd() {
        return this.useModernClientColor() || this.modernHealthEnd == null
                ? HUD.clientColorEnd()
                : this.modernHealthEnd.getValue();
    }

    public MizuColor modernGlowColor() {
        return this.useModernClientColor() || this.modernGlowColor == null
                ? HUD.clientGlowColor()
                : this.modernGlowColor.getValue();
    }
}
