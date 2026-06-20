/*
 * This module adapts the attack-triggered sprint/movement reset idea from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSuperKnockback
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a standalone WTap combat module.
 */
package shit.zen.modules.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.AttackEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.manager.TargetManager;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class WTap extends Module {
    public static WTap INSTANCE;

    private enum Phase {
        IDLE,
        WAIT_START,
        SPRINT_TAPPING,
        MOVEMENT_BLOCKING,
        WAIT_ALLOW_MOVEMENT
    }

    public final NumberValue chance = new NumberValue("Chance", 100.0, 0.0, 100.0, 1.0);
    public final NumberValue distance = new NumberValue("Distance", 3.5, 0.0, 6.0, 0.1);
    public final NumberValue startTapDelay = new NumberValue("Start tap delay", 31.0, 0.0, 250.0, 1.0);
    public final NumberValue tapDelay = new NumberValue("Tap delay", 60.0, 1.0, 250.0, 1.0);
    public final BooleanValue onlySword = new BooleanValue("Only sword", false);
    public final BooleanValue onlyOnGround = new BooleanValue("Only on ground", true);
    public final NumberValue tapEveryHits = new NumberValue("Tap every hits", 1.0, 1.0, 10.0, 1.0);
    public final BooleanValue simulateClicks = new BooleanValue("Simulate clicks", false);
    public final ModeValue tapMode = new ModeValue("Mode", "WTap", "Sprint Tap").withDefault("WTap");
    public final ModeValue triggerMode = new ModeValue("Trigger", "Event Attack").withDefault("Event Attack");
    public final BooleanValue debug = new BooleanValue("Debug", false);
    public final BooleanValue debugRejections = new BooleanValue("Debug Rejections", true, this.debug::getValue);

    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private int hitCounter;
    private boolean moduleTapped;
    private boolean savedSprintKeyDown;
    private boolean savedForwardKeyDown;
    private boolean savedBackKeyDown;
    private boolean savedLeftKeyDown;
    private boolean savedRightKeyDown;
    private int strafeDebugTicks;

    public WTap() {
        super("WTap", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup timing = root.group("timing", "Timing");
        timing.add(this.chance);
        timing.add(this.distance);
        timing.add(this.startTapDelay);
        timing.add(this.tapDelay);
        timing.add(this.tapEveryHits);

        ValueGroup conditions = root.group("conditions", "Conditions");
        conditions.add(this.onlySword);
        conditions.add(this.onlyOnGround);

        ValueGroup mode = root.group("mode", "Mode");
        mode.add(this.tapMode);
        mode.add(this.triggerMode);
        mode.add(this.simulateClicks);

        ValueGroup debug = root.group("debug", "Debug");
        debug.add(this.debug);
        debug.add(this.debugRejections);
    }

    @Override
    protected void onDisable() {
        this.stopTap();
        this.phase = Phase.IDLE;
        this.hitCounter = 0;
        this.strafeDebugTicks = 0;
        super.onDisable();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event == null) {
            this.debugReject("attack rejected: event=null");
            return;
        }

        if (!this.triggerMode.is("Event Attack") || !event.isPost()) {
            this.debugReject("attack ignored: trigger=" + this.triggerMode.getValue() + " post=" + event.isPost());
            return;
        }

        if (this.phase != Phase.IDLE) {
            this.debugReject("attack ignored: busy phase=" + this.phase);
            return;
        }

        String blockedReason = this.getTriggerBlockReason(event.getTarget());
        if (blockedReason != null) {
            this.debugReject("attack rejected: " + blockedReason);
            return;
        }

        int everyHits = Math.max(1, this.tapEveryHits.getValue().intValue());
        this.hitCounter++;
        if (this.hitCounter % everyHits != 0) {
            this.debugReject("attack skipped: hitCounter=" + this.hitCounter + "/" + everyHits);
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        if (roll > this.chance.getValue().doubleValue()) {
            this.debugReject("attack skipped: chance roll=" + this.formatDouble(roll)
                    + " > " + this.formatDouble(this.chance.getValue().doubleValue()));
            return;
        }

        this.phase = Phase.WAIT_START;
        this.phaseStartedAt = System.currentTimeMillis();
        this.debugLog("trigger target=" + this.formatEntity(event.getTarget())
                + " mode=" + this.tapMode.getValue()
                + " startDelay=" + this.startTapDelay.getValue().longValue()
                + " tapDelay=" + this.tapDelay.getValue().longValue());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            this.stopTap();
            this.phase = Phase.IDLE;
            return;
        }

        if (this.phase == Phase.WAIT_START) {
            if (this.elapsed(this.startTapDelay.getValue().longValue())) {
                if (this.tapMode.is("Sprint Tap")) {
                    this.startSprintTap();
                } else {
                    this.startMovementBlock();
                }
            }
        } else if (this.phase == Phase.SPRINT_TAPPING) {
            this.applySprintTapSuppression();
            if (this.elapsed(this.tapDelay.getValue().longValue())) {
                this.stopTap();
                this.phase = Phase.IDLE;
            }
        } else if (this.phase == Phase.MOVEMENT_BLOCKING) {
            this.applyMovementBlockSuppression();
            if (!this.hasForwardImpulse()) {
                this.phase = Phase.WAIT_ALLOW_MOVEMENT;
                this.phaseStartedAt = System.currentTimeMillis();
                this.debugLog("movement blocked: forwardImpulse=0 waitAllow=" + this.tapDelay.getValue().longValue());
            }
        } else if (this.phase == Phase.WAIT_ALLOW_MOVEMENT) {
            this.applyMovementBlockSuppression();
            if (this.elapsed(this.tapDelay.getValue().longValue())) {
                this.stopTap();
                this.phase = Phase.IDLE;
                this.debugLog("release movement");
            }
        }
    }

    @EventTarget(EventPriority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (!this.isMovementBlockActive()) {
            return;
        }

        event.setForward(0.0f);
        event.setStrafe(0.0f);
        this.debugStrafe(event);
    }

    public boolean shouldSuppressSprint() {
        return this.isEnabled() && (this.phase == Phase.SPRINT_TAPPING || this.isMovementBlockActive());
    }

    private boolean shouldTrigger(Entity target) {
        return this.getTriggerBlockReason(target) == null;
    }

    private String getTriggerBlockReason(Entity target) {
        if (mc.player == null || mc.level == null || target == null) {
            return "missing context/player/target";
        }

        if (!(target instanceof LivingEntity livingEntity)) {
            return "target is not LivingEntity: " + this.formatEntity(target);
        }

        if (!livingEntity.isAlive() || livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) {
            return "target dead: " + this.formatEntity(target);
        }

        TargetManager targetManager = TargetManager.INSTANCE;
        if (targetManager != null && !targetManager.isValidTarget(target)) {
            return "TargetManager rejected: " + this.formatEntity(target);
        }

        if (this.onlyOnGround.getValue() && !mc.player.onGround()) {
            return "player not on ground";
        }

        if (this.onlySword.getValue() && !(mc.player.getMainHandItem().getItem() instanceof SwordItem)) {
            return "main hand is not sword";
        }

        double targetDistance = this.getDistanceToTarget(target);
        double maxDistance = this.distance.getValue().doubleValue();
        if (targetDistance > maxDistance) {
            return "distance " + this.formatDouble(targetDistance) + " > " + this.formatDouble(maxDistance);
        }

        return null;
    }

    private double getDistanceToTarget(Entity target) {
        if (mc.player == null || target == null) {
            return Double.MAX_VALUE;
        }

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 closest = RotationUtil.closestPoint(eyes, target.getBoundingBox());
        return closest.distanceTo(eyes);
    }

    private void startSprintTap() {
        if (mc.player == null || mc.options == null) {
            this.phase = Phase.IDLE;
            return;
        }

        this.saveKeyStates();

        this.phase = Phase.SPRINT_TAPPING;
        this.phaseStartedAt = System.currentTimeMillis();
        this.applySprintTapSuppression();
        this.debugLog("start sprint-tap");
    }

    private void startMovementBlock() {
        if (mc.player == null || mc.options == null) {
            this.phase = Phase.IDLE;
            return;
        }

        this.saveKeyStates();

        this.phase = Phase.MOVEMENT_BLOCKING;
        this.phaseStartedAt = System.currentTimeMillis();
        this.applyMovementBlockSuppression();
        this.debugLog("start wtap movement-block fwd=" + this.formatDouble(mc.player.input.forwardImpulse)
                + " strafe=" + this.formatDouble(mc.player.input.leftImpulse));
    }

    private void applySprintTapSuppression() {
        if (mc.player == null || mc.options == null) {
            return;
        }

        mc.player.setSprinting(false);

        if (this.simulateClicks.getValue()) {
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            KeyMapping.set(mc.options.keyUp.getKey(), false);
        }
    }

    private void applyMovementBlockSuppression() {
        if (mc.player == null || mc.options == null) {
            return;
        }

        if (this.simulateClicks.getValue()) {
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            KeyMapping.set(mc.options.keyUp.getKey(), false);
            KeyMapping.set(mc.options.keyDown.getKey(), false);
            KeyMapping.set(mc.options.keyLeft.getKey(), false);
            KeyMapping.set(mc.options.keyRight.getKey(), false);
        }
    }

    private void stopTap() {
        if (!this.moduleTapped) {
            return;
        }

        if (mc.options != null && this.simulateClicks.getValue()) {
            KeyMapping.set(mc.options.keySprint.getKey(), this.savedSprintKeyDown);
            KeyMapping.set(mc.options.keyUp.getKey(), this.savedForwardKeyDown);
            KeyMapping.set(mc.options.keyDown.getKey(), this.savedBackKeyDown);
            KeyMapping.set(mc.options.keyLeft.getKey(), this.savedLeftKeyDown);
            KeyMapping.set(mc.options.keyRight.getKey(), this.savedRightKeyDown);
        }

        this.moduleTapped = false;
    }

    private void saveKeyStates() {
        this.savedSprintKeyDown = mc.options.keySprint.isDown();
        this.savedForwardKeyDown = mc.options.keyUp.isDown();
        this.savedBackKeyDown = mc.options.keyDown.isDown();
        this.savedLeftKeyDown = mc.options.keyLeft.isDown();
        this.savedRightKeyDown = mc.options.keyRight.isDown();
        this.moduleTapped = true;
    }

    private boolean isMovementBlockActive() {
        return this.isEnabled()
                && (this.phase == Phase.MOVEMENT_BLOCKING || this.phase == Phase.WAIT_ALLOW_MOVEMENT);
    }

    private boolean hasForwardImpulse() {
        return mc.player != null && Math.abs(mc.player.input.forwardImpulse) > 1.0E-4f;
    }

    private void debugStrafe(StrafeEvent event) {
        if (!this.debug.getValue() || mc.player == null) {
            return;
        }

        this.strafeDebugTicks++;
        if (this.strafeDebugTicks % 2 != 1) {
            return;
        }

        this.debugLog("strafe zeroed phase=" + this.phase
                + " eventFwd=" + this.formatDouble(event.getForward())
                + " eventStrafe=" + this.formatDouble(event.getStrafe())
                + " inputFwd=" + this.formatDouble(mc.player.input.forwardImpulse)
                + " inputStrafe=" + this.formatDouble(mc.player.input.leftImpulse));
    }

    private void debugReject(String message) {
        if (this.debug.getValue() && this.debugRejections.getValue()) {
            this.debugPrint(message);
        }
    }

    private void debugLog(String message) {
        if (this.debug.getValue()) {
            this.debugPrint(message);
        }
    }

    private void debugPrint(String message) {
        String line = "[WTapDebug] tick=" + (mc.player == null ? -1 : mc.player.tickCount) + " " + message;
        logger.info(line);
        ChatUtil.print(line);
    }

    private String formatEntity(Entity entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getType().toShortString() + "#" + entity.getId();
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private boolean elapsed(long delayMs) {
        return System.currentTimeMillis() - this.phaseStartedAt >= Math.max(0L, delayMs);
    }
}
