package shit.zen.modules.impl.combat;

import java.util.Arrays;
import java.util.Optional;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.ReceivePacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.RotationEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.antikb.AntiKBMode;
import shit.zen.modules.impl.movement.FireballBlink;
import shit.zen.modules.impl.movement.HighJump;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;
import shit.zen.utils.rotation.Rotation;
import shit.zen.event.EventTarget;

public class AntiKB
extends Module {
    public static AntiKB INSTANCE;
    public static Rotation rotation;
    public static ModeValue mode;
    public final BooleanValue autoJump = new BooleanValue("Auto Jump", false, () -> mode.is("Grim Full") || mode.is("Grim Fast"));
    public final BooleanValue rotate = new BooleanValue("Rotate", false, () -> mode.is("Jump Reset") || mode.is("Mix"));
    public final BooleanValue tryAttack = new BooleanValue("Try Attack", false, () -> mode.is("Mix"));
    public final BooleanValue movementOverride = new BooleanValue("Movement Override", false, () -> mode.is("Mix"));
    public final BooleanValue followDirection = new BooleanValue("Follow Direction", false, () -> mode.is("Jump Reset"));
    public final NumberValue rotateTicks = new NumberValue("Rotate Ticks", 12, 3, 20, 1, () -> mode.is("Jump Reset") && (this.rotate.getValue() != false || this.followDirection.getValue() != false));
    public final NumberValue attackAmount = new NumberValue("Attack amount", 5.0, 1.0, 20.0, 1, () -> mode.is("NoXZ"));
    public final BooleanValue instantAttack = new BooleanValue("Instant Attack", false, () -> mode.is("NoXZ"));
    public final BooleanValue sprintStateCheck = new BooleanValue("Sprint state check", true, () -> mode.is("NoXZ"));

    public AntiKB() {
        super("AntiKB", Category.COMBAT);
        INSTANCE = this;
        AntiKBMode.initModes();
    }

    @Override
    public String getSuffix() {
        return String.valueOf(mode.getValue());
    }

    @Override
    public void onEnable() {
        Optional<AntiKBMode> optional;
        rotation = null;
        if (!Arrays.stream((Object[])mode.getModes()).toList().contains(mode.getValue())) {
            mode.withDefault("NoXZ");
        }
        if ((optional = AntiKBMode.findMode(mode.getValue())).isEmpty()) {
            return;
        }
        optional.get().onEnable();
    }

    @Override
    public void onDisable() {
        rotation = null;
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisable();
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onGameTick(gameTickEvent);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent preMotionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onPreMotion(preMotionEvent);
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onTick(tickEvent);
    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onSprint(sprintEvent);
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRotation(rotationEvent);
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onMotion(motionEvent);
    }

    @EventTarget(value=1)
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onReceivePacket(receivePacketEvent);
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisconnect(disconnectEvent);
    }

    @EventTarget(value=3)
    public void onStrafe(StrafeEvent strafeEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onStrafe(strafeEvent);
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender(renderEvent);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender2D(render2DEvent);
    }

    static {
        mode = new ModeValue("Mode", "Jump Reset", "Mix", "NoXZ").withDefault("NoXZ");
    }
}
