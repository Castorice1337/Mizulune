package shit.zen.utils.rotation;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.CameraPitchEvent;
import shit.zen.event.impl.ChatEvent;
import shit.zen.event.impl.FallFlyingEvent;
import shit.zen.event.impl.RotationAnimationEvent;
import shit.zen.event.impl.JumpMarkerEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.RayTraceEvent;
import shit.zen.event.impl.RotationEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.UseItemRayTraceEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.impl.combat.AntiKB;
import shit.zen.modules.impl.combat.AutoThrow;
import shit.zen.modules.impl.combat.CrystalAura;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.movement.FireballBlink;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.player.AntiTNT;
import shit.zen.modules.impl.player.AntiWeb;
import shit.zen.modules.impl.player.AutoMLG;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.modules.impl.player.Helper;
import shit.zen.modules.impl.player.MidPearl;
import shit.zen.utils.animation.TickTimer;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.event.EventTarget;

public class RotationHandler
extends ClientBase {
    private static final List<RotationProvider> ROTATION_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RotationSmoother PROVIDER_SMOOTHER = new RotationSmoother();
    public static Rotation targetRotation;
    public static Rotation prevRotation;
    public static Rotation sentRotation;
    public static Rotation prevSentRotation;
    public static boolean isRotating;
    private static RotationProvider activeProvider;
    private static boolean movementFixing;
    private static RotationApplyMode activeApplyMode;

    public static void setTargetRotation(Rotation rotation) {
        RotationHandler.setTargetRotation(rotation, true);
    }

    public static void setTargetRotation(Rotation rotation, boolean fixMovement) {
        RotationHandler.setTargetRotation(rotation, fixMovement, RotationApplyMode.SILENT);
    }

    public static void setTargetRotation(Rotation rotation, boolean fixMovement, RotationApplyMode applyMode) {
        if (rotation == null) {
            return;
        }
        targetRotation = rotation;
        movementFixing = fixMovement;
        activeApplyMode = applyMode == null ? RotationApplyMode.SILENT : applyMode;
        ClientBase.yaw = rotation.getYaw();
        if (activeApplyMode == RotationApplyMode.CHANGE_LOOK) {
            RotationHandler.applyToLocalCamera(rotation);
        }
    }

    public static void registerProvider(RotationProvider provider) {
        if (provider != null && !ROTATION_PROVIDERS.contains(provider)) {
            ROTATION_PROVIDERS.add(provider);
        }
    }

    public static void unregisterProvider(RotationProvider provider) {
        ROTATION_PROVIDERS.remove(provider);
        if (activeProvider == provider) {
            RotationHandler.clearRotationState();
        }
    }

    public static Rotation getSmoothedRotation(RotationProvider provider) {
        if (provider != null && provider == activeProvider && isRotating && targetRotation != null) {
            return targetRotation.clone();
        }
        return null;
    }

    private static RotationProvider resolveProvider() {
        return ROTATION_PROVIDERS.stream()
                .filter(provider -> provider.isRotationActive()
                        && provider.getApplyMode() != RotationApplyMode.OFF
                        && provider.getRotation() != null)
                .max(Comparator.comparingInt(RotationProvider::getRotationPriority))
                .orElse(null);
    }

    private static void applyToLocalCamera(Rotation rotation) {
        if (mc.player == null) {
            return;
        }
        mc.player.setYRot(rotation.getYaw());
        mc.player.setXRot(rotation.getPitch());
        mc.player.yRotO = rotation.getYaw();
        mc.player.xRotO = rotation.getPitch();
    }

    private static Rotation smoothProviderRotation(RotationProvider provider) {
        return PROVIDER_SMOOTHER.update(
                provider.getRotation(),
                provider.getSmoothMode(),
                provider.getSmoothDurationTicks(),
                provider.getSmoothSteepness(),
                provider.getMaxYawSpeed(),
                provider.getMaxPitchSpeed(),
                provider.getMinStep(),
                provider.getRotationEpsilon(),
                provider.shouldHumanizeRotation());
    }

    private static void clearActiveProvider() {
        activeProvider = null;
        PROVIDER_SMOOTHER.reset();
    }

    private static void clearRotationState() {
        RotationHandler.clearActiveProvider();
        isRotating = false;
        movementFixing = false;
        activeApplyMode = RotationApplyMode.OFF;
        targetRotation = null;
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent worldChangeEvent) {
        prevRotation = null;
        targetRotation = null;
        RotationHandler.clearActiveProvider();
        movementFixing = false;
        activeApplyMode = RotationApplyMode.OFF;
    }

    @EventTarget(value=0)
    public void onTick(TickEvent tickEvent) {
        TickTimer.tickAll();
    }

    @EventTarget(value=0)
    public void onPacket(PacketEvent packetEvent) {
        ServerboundMovePlayerPacket serverboundMovePlayerPacket;
        Object object = packetEvent.getPacket();
        if (object instanceof ServerboundMovePlayerPacket movePacket
                && movePacket.getYRot(0.0f) < 360.0f && movePacket.getYRot(0.0f) > -360.0f) {
            ReflectionUtil.setYRot(movePacket, movePacket.getYRot(0.0f) + 720.0f);
        }
        Object packet2 = packetEvent.getPacket();
        if (packet2 instanceof ServerboundChatPacket chatPacket) {
            ChatEvent event = new ChatEvent(chatPacket.message());
            if (ZenClient.isReady()) {
                ZenClient.getInstance().getEventBus().call(event);
                if (event.isCancelled()) {
                    packetEvent.setCancelled(true);
                }
            }
        }
    }

    @EventTarget(value=4)
    public void onTickHigh(TickEvent tickEvent) {
        if (mc.player != null) {
            KillAura killAura = KillAura.INSTANCE;
            Scaffold scaffold = Scaffold.INSTANCE;
            CrystalAura crystalAura = CrystalAura.INSTANCE;
            AutoMLG autoMLG = AutoMLG.INSTANCE;
            FireballBlink fireballBlink = FireballBlink.INSTANCE;
            AntiTNT antiTNT = AntiTNT.INSTANCE;
            Helper helper = Helper.INSTANCE;
            AntiWeb antiWeb = AntiWeb.INSTANCE;
            AutoWebPlace autoWebPlace = AutoWebPlace.INSTANCE;
            AutoThrow autoThrow = AutoThrow.INSTANCE;
            AntiKB antiKB = AntiKB.INSTANCE;
            MidPearl midPearl = MidPearl.INSTANCE;
            RotationProvider provider = RotationHandler.resolveProvider();
            isRotating = true;
            if (provider != null) {
                activeProvider = provider;
                Rotation smoothed = RotationHandler.smoothProviderRotation(provider);
                if (smoothed != null) {
                    RotationHandler.setTargetRotation(smoothed, provider.shouldFixMovement(), provider.getApplyMode());
                } else {
                    RotationHandler.clearRotationState();
                }
            } else if (autoMLG != null && autoMLG.isEnabled() && autoMLG.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(autoMLG.targetRotation);
                autoMLG.targetRotation = null;
            } else if (crystalAura != null && crystalAura.isEnabled() && CrystalAura.aimRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(CrystalAura.aimRotation);
            } else if (fireballBlink != null && fireballBlink.isEnabled() && FireballBlink.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(FireballBlink.rotation);
            } else if (midPearl != null && midPearl.isEnabled() && MidPearl.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(MidPearl.targetRotation);
            } else if (antiTNT != null && antiTNT.isEnabled() && AntiTNT.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(AntiTNT.targetRotation);
            } else if (helper != null && helper.isEnabled() && helper.hasTargetRotation() && Helper.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(Helper.targetRotation);
            } else if (antiWeb != null && antiWeb.isEnabled() && AntiWeb.currentPhase != AntiWeb.Phase.IDLE && AntiWeb.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(AntiWeb.targetRotation);
            } else if (autoWebPlace != null && autoWebPlace.isEnabled() && AutoWebPlace.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(AutoWebPlace.targetRotation);
            } else if (autoThrow != null && autoThrow.isEnabled() && autoThrow.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(autoThrow.targetRotation);
            } else if (scaffold != null && scaffold.isEnabled() && scaffold.rots != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(scaffold.rots);
            } else if (killAura != null && killAura.isEnabled() && KillAura.target != null && killAura.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(new Rotation(killAura.rotation.getYaw(), killAura.rotation.getPitch()));
            } else if (antiKB != null && antiKB.isEnabled() && AntiKB.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setTargetRotation(AntiKB.rotation);
            } else {
                RotationHandler.clearRotationState();
            }
        }
    }

    @EventTarget
    public void onHeadTurn(RotationAnimationEvent e) {
        if (sentRotation != null && prevSentRotation != null && mc.player != null && isRotating) {
            e.setYaw(sentRotation.getYaw());
            e.setLastYaw(prevSentRotation.getYaw());

            e.setPitch(sentRotation.getPitch());
            e.setLastPitch(prevSentRotation.getPitch());
        }
    }

    @EventTarget
    public void onCameraPitch(CameraPitchEvent cameraPitchEvent) {
        if (sentRotation != null && prevSentRotation != null) {
            cameraPitchEvent.setPitch(sentRotation.getPitch());
        }
    }

    @EventTarget(value=4)
    public void onMotion(MotionEvent e) {
        if (e.isPost()) {
            if (mc.player != null && mc.player.tickCount <= 1 && ZenClient.isReady()) {
                ZenClient.getInstance().getEventBus().call(new WorldChangeEvent());
            }
            if (mc.player == null) {
                return;
            }
            if (targetRotation == null || prevRotation == null) {
                targetRotation = prevRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());
            }
            prevSentRotation = sentRotation;
            float yaw = targetRotation.getYaw();
            float pitch = targetRotation.getPitch();
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch) && isRotating) {
                e.setYaw(yaw);
                e.setPitch(pitch);
            }
            ClientBase.yaw = targetRotation.getYaw();
            sentRotation = new Rotation(e.getYaw(), e.getPitch());
            prevRotation = new Rotation(e.getYaw(), e.getPitch());
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (isRotating && movementFixing && targetRotation != null) {
            float yaw = targetRotation.getYaw();
            MovementUtil.handleStrafe(strafeEvent, yaw);
        }
    }

    @EventTarget
    public void onRayTrace(RayTraceEvent rayTraceEvent) {
        if (targetRotation != null && rayTraceEvent.entity == mc.player && isRotating) {
            rayTraceEvent.setYaw(targetRotation.getYaw());
            rayTraceEvent.setPitch(targetRotation.getPitch());
        }
    }

    @EventTarget
    public void onUseItemRayTrace(UseItemRayTraceEvent useItemRayTraceEvent) {
        if (targetRotation != null && isRotating) {
            useItemRayTraceEvent.setYaw(targetRotation.getYaw());
            useItemRayTraceEvent.setPitch(targetRotation.getPitch());
        }
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        if (isRotating && movementFixing && targetRotation != null) {
            rotationEvent.setYaw(targetRotation.getYaw());
        }
    }

    @EventTarget
    public void onJump(JumpMarkerEvent jumpMarkerEvent) {
        if (isRotating && movementFixing && targetRotation != null) {
            jumpMarkerEvent.setYaw(targetRotation.getYaw());
        }
    }

    @EventTarget
    public void onFallFlying(FallFlyingEvent fallFlyingEvent) {
        if (isRotating && targetRotation != null) {
            fallFlyingEvent.setPitch(targetRotation.getPitch());
        }
    }

    static {
        isRotating = false;
        movementFixing = false;
        activeApplyMode = RotationApplyMode.OFF;
    }
}
