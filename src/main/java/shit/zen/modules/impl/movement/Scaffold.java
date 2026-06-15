package shit.zen.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.RandomUtils;
import shit.zen.ClientBase;
import shit.zen.event.impl.JumpEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.world.BlockIn;
import shit.zen.modules.impl.render.DynamicIsland;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;
import shit.zen.utils.game.BlockPlacementOptions;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.BlockPlacementUtil;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.MotionSimulator;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.game.RayTraceUtil;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.math.MathUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.event.EventTarget;

public class Scaffold extends Module {
    public static Scaffold INSTANCE;

    public final ModeValue mode = new ModeValue("Mode", "Normal", "Telly Bridge", "Old Telly", "Keep Y").withDefault("Normal");
    public final BooleanValue eagle = new BooleanValue("Eagle", true, () -> this.mode.is("Normal"));
    public final BooleanValue sneak = new BooleanValue("Sneak", true);
    public final BooleanValue snap = new BooleanValue("Snap", true, () -> this.mode.is("Normal"));
    public final BooleanValue renderItemSpoof = new BooleanValue("Render Item Spoof", true);
    public final BooleanValue scaffoldCounterOnIsland = new BooleanValue("Scaffold Counter On Island", false);
    public final NumberValue rotationTick = new NumberValue("Rotation Tick", 3, 1, 6, 1);
    public final BooleanValue clutch = new BooleanValue("Clutch", true);

    public Rotation correctRotation = new Rotation();
    public Rotation rots = new Rotation();
    public Rotation lastRots = new Rotation();
    public int targetYLevel = -1;
    public int velocityDelay = 0;

    private int oldSlot;
    private BlockPlacementTarget currentPlacement;
    private int eagleTimer;
    private int groundTicks = 0;
    private int airTicks = 0;
    private int rotationDelay = 0;
    private final CopyOnWriteArrayList<CopyOnWriteArrayList<Packet<?>>> packetBatches = new CopyOnWriteArrayList<>();
    private int jitterCounter;
    private double yawDiff;
    private double pitchDiff;
    private double lastYawDiff = Double.NaN;
    private double lastPitchDiff = Double.NaN;
    private boolean canBuildNow;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isEnabled()) {
            GodBridgeAssist.INSTANCE.setEnabled(false);
        }
        if (BlockIn.INSTANCE != null && BlockIn.INSTANCE.isEnabled()) {
            BlockIn.INSTANCE.setEnabled(false);
        }
        if (mc.player != null) {
            this.oldSlot = mc.player.getInventory().selected;
            this.rots.setYawPitch(mc.player.getYRot() - 180.0f, mc.player.getXRot());
            this.lastRots.setYawPitch(mc.player.yRotO - 180.0f, mc.player.xRotO);
            this.currentPlacement = null;
            this.targetYLevel = 10000;
            this.velocityDelay = 0;
            this.jitterCounter = 0;
            this.yawDiff = 0.0;
            this.pitchDiff = 0.0;
            this.lastYawDiff = Double.NaN;
            this.lastPitchDiff = Double.NaN;
            this.canBuildNow = true;
            this.packetBatches.clear();
            this.packetBatches.add(new CopyOnWriteArrayList<>());
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc != null && mc.player != null) {
            this.packetBatches.forEach(this::processBatchedPackets);
            this.packetBatches.clear();
            boolean jumpDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
            boolean shiftDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
            mc.options.keyJump.setDown(jumpDown);
            mc.options.keyShift.setDown(shiftDown);
            mc.options.keyUse.setDown(false);
            mc.player.getInventory().selected = this.oldSlot;
            this.canBuildNow = true;
            ClientBase.delayPackets.clear();
        }
        super.onDisable();
    }

    private void processBatchedPackets(List<Packet<?>> batch) {
        batch.forEach(packet -> {
            batch.remove(packet);
            PacketUtil.sendQueued((Packet<ServerGamePacketListener>) packet);
        });
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            double length = new Vec3(motion.getXa() / 8000.0, 0.0, motion.getZa() / 8000.0).length();
            if (length >= 1.5) {
                this.velocityDelay = 60;
            }
        }
    }

    @EventTarget
    public void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (this.renderItemSpoof.getValue() && event.getHand() == InteractionHand.MAIN_HAND && mc.player != null) {
            event.setItemStack(mc.player.getInventory().getItem(this.oldSlot));
        }
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (!this.canBuildNow && this.currentPlacement != null && this.rotationDelay > 0) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost() && mc.player != null) {
            if (mc.player.onGround()) {
                this.airTicks = 0;
                this.groundTicks++;
            } else {
                this.groundTicks = 0;
                this.airTicks++;
            }
        }
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        this.packetBatches.add(new CopyOnWriteArrayList<>());
        if (this.velocityDelay > 0) this.velocityDelay--;
        if (mc.player.onGround() && this.velocityDelay <= 30) this.velocityDelay = 0;

        int placeableSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                placeableSlot = i;
                break;
            }
        }
        if (placeableSlot != -1 && mc.player.getInventory().selected != placeableSlot) {
            mc.player.getInventory().selected = placeableSlot;
        }

        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        if (this.targetYLevel == -1
                || this.targetYLevel > (int) Math.floor(mc.player.getY()) - 1
                || mc.player.onGround()
                || !MovementUtil.isMoving()
                || jumpHeld
                || this.mode.is("Normal")) {
            this.targetYLevel = (int) Math.floor(mc.player.getY()) - 1;
        }

        this.applyRotations();
        boolean firstGroundTick = false;
        this.canBuildNow = true;
        if (this.currentPlacement != null && placeableSlot != -1) {
            if (this.groundTicks == 1 && mc.options.keyJump.isDown()) {
                firstGroundTick = true;
            }
            if (this.clutch.getValue() && mc.player.getDeltaMovement().y < -0.1) {
                MotionSimulator sim = new MotionSimulator(mc.player);
                sim.simulateWithFriction(2);
                if (this.currentPlacement.interactedBlockPos().getY() > sim.y) {
                    this.canBuildNow = false;
                }
            }
        }
        if (mc.player.onGround()) {
            this.canBuildNow = true;
        }
        this.correctRotation = this.mode.is("Telly Bridge") && this.canBuildNow
                ? this.getTargetRotation(firstGroundTick)
                : this.getPlayerYawRotation();

        if (this.currentPlacement == null) {
            ClientBase.delayPackets.clear();
        } else if (this.clutch.getValue() && (!this.canBuildNow || this.velocityDelay > 0) && this.rotationDelay <= 8) {
            Rotation rotationToBlock = RotationUtil.rotationToBlock(this.currentPlacement.interactedBlockPos(), 1.0f);
            Rotation previousTarget = RotationHandler.targetRotation;
            this.rots.setYawPitch(rotationToBlock.getYaw(), rotationToBlock.getPitch());
            RotationHandler.setTargetRotation(this.rots);
            this.rotationDelay++;
            ClientBase.delayPackets.add(() -> {});
            ClientBase.delayPackets.add(() -> {
                RotationHandler.prevSentRotation.setYawPitch(rotationToBlock.getYaw(), rotationToBlock.getPitch());
                float yaw = rotationToBlock.getYaw();
                if (yaw > -360.0f && yaw < 360.0f) {
                    yaw += 720.0f;
                }
                if (previousTarget != null) {
                    if (previousTarget.getYaw() != rotationToBlock.getYaw()) {
                        PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, rotationToBlock.getPitch(), mc.player.onGround()));
                    }
                } else {
                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, rotationToBlock.getPitch(), mc.player.onGround()));
                }
                this.doSnap();
                this.onTick(event);
            });
        } else {
            this.canBuildNow = true;
            ClientBase.delayPackets.clear();
            this.rotationDelay = 0;
            if (this.mode.is("Normal") && this.snap.getValue()) {
                this.rots.setYaw(this.correctRotation.getYaw());
            } else {
                this.rots.setYaw(RotationUtil.moveTowards((float) this.getBlockDistance(), this.rots.getYaw(), this.correctRotation.getYaw()));
            }
            this.rots.setPitch(this.correctRotation.getPitch());
            if (this.sneak.getValue()) {
                this.eagleTimer++;
                if (this.eagleTimer == 18) {
                    if (mc.player.isSprinting()) {
                        mc.options.keySprint.setDown(false);
                        mc.player.setSprinting(false);
                    }
                    mc.options.keyShift.setDown(true);
                } else if (this.eagleTimer >= 21) {
                    mc.options.keyShift.setDown(false);
                    this.eagleTimer = 0;
                }
            }
            if (this.mode.is("Telly Bridge") || this.mode.is("Old Telly")) {
                mc.options.keyJump.setDown(MovementUtil.isMoving() || jumpHeld);
                if (this.airTicks < 1 && MovementUtil.isMoving()) {
                    if (this.mode.is("Old Telly")) {
                        this.rots.setYaw(mc.player.getYRot());
                    }
                    this.lastRots.setYawPitch(this.rots.getYaw(), this.rots.getPitch());
                    return;
                }
            } else if (this.mode.is("Keep Y")) {
                mc.options.keyJump.setDown(MovementUtil.isMoving() || jumpHeld);
            } else {
                if (this.eagle.getValue()) {
                    mc.options.keyShift.setDown(mc.player.onGround() && isOnBlockEdge(0.3f));
                }
                if (this.snap.getValue() && !jumpHeld) {
                    this.resetSnap();
                }
            }
        }
        this.lastRots.setYawPitch(this.rots.getYaw(), this.rots.getPitch());
    }

    private double getBlockDistance() {
        if (this.mode.is("Old Telly")) return 180.0;
        double base = Math.max(60.0, 360.0 / this.rotationTick.getValue().doubleValue());
        return Math.max(base, 180.0);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        event.setCancelled(true);
        if (mc.screen != null || mc.player == null || this.currentPlacement == null) return;
        if ((this.mode.is("Telly Bridge") || this.mode.is("Old Telly")) && this.airTicks < 1) return;
        boolean canRayTrace = RayTraceUtil.canRayTrace(RotationHandler.targetRotation,
                this.currentPlacement.facing(), this.currentPlacement.interactedBlockPos(), false);
        if (!this.canBuildNow && !this.isPlacementReachable(this.currentPlacement)) return;
        if (this.rotationDelay <= 0 && !this.mode.is("Old Telly") && !canRayTrace) return;
        this.doSnap();
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (this.currentPlacement == null || mc.gameRenderer == null) return;
        PoseStack poseStack = event.poseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        AABB box = new AABB(this.currentPlacement.placedBlockPos());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        Color color = new Color(74, 144, 226);
        RenderSystem.setShaderColor(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 0.25f);
        RenderUtil.drawSolidBox(box, poseStack);
        RenderSystem.setShaderColor(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 0.75f);
        RenderUtil.drawOutlineBox(box, poseStack);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (DynamicIsland.shouldRenderScaffoldCounter()) return;
        int blockCount = this.getBlockSlot();
        if (blockCount == 0) return;
        String countText = String.valueOf(blockCount);
        String suffix = " Blocks";
        GuiGraphics graphics = event.guiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        float centerX = width / 2.0f;
        float y = height / 2.0f - 20.0f;
        int textWidth = mc.font.width(countText + suffix);
        int x = (int) (centerX - textWidth / 2.0f);
        graphics.drawString(mc.font, countText, x, (int) y, -11890462);
        graphics.drawString(mc.font, suffix, x + mc.font.width(countText), (int) y, -1);
    }

    public boolean isCounterOnIslandActive() {
        return this.isEnabled() && this.scaffoldCounterOnIsland.getValue() && this.getPlaceableBlockCount() > 0;
    }

    public int getPlaceableBlockCount() {
        return this.getBlockSlot();
    }

    public ItemStack getCounterBlockItem() {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack selected = mc.player.getMainHandItem();
        if (selected.getItem() instanceof BlockItem && BlockUtil.isPlaceable(selected)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                return stack;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private int getBlockSlot() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean isPlacementReachable(BlockPlacementTarget target) {
        if (target == null || mc.player == null) return false;
        return BlockPlacementUtil.isValidPlacementTarget(target, mc.player.getMainHandItem(), this.getPlacementOptions());
    }

    private void resetSnap() {
        if (mc.player == null || this.currentPlacement == null) return;
        boolean lookingAtBlock = false;
        HitResult result = RayTraceUtil.rayTrace(1.0f, this.rots);
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            if (blockHit.getBlockPos().equals(this.currentPlacement.interactedBlockPos())
                    && blockHit.getDirection() != Direction.UP) {
                lookingAtBlock = true;
            }
        }
        if (!lookingAtBlock && mc.player.tickCount % 4 == 0) {
            this.rots.setYaw(mc.player.getYRot() + RandomUtils.nextFloat(0.0f, 0.5f) - 0.25f);
        }
    }

    private Rotation getPlayerYawRotation() {
        if (mc.player == null || this.currentPlacement == null) {
            return new Rotation(0.0f, 0.0f);
        }
        return RotationUtil.rotationToBlock(this.currentPlacement.interactedBlockPos(), 0.0f);
    }

    private void applyRotations() {
        if (mc.player == null || mc.level == null) return;
        Vec3 eye = mc.player.getEyePosition();
        if (!this.canBuildNow) {
            eye = mc.player.getEyePosition().add(mc.player.getDeltaMovement().multiply(2.0, 2.0, 2.0));
        }
        if (this.clutch.getValue() && mc.player.getDeltaMovement().y < 0.01) {
            MotionSimulator sim = new MotionSimulator(mc.player);
            sim.simulateWithFriction(2);
            eye = new Vec3(eye.x, Math.max(sim.y + mc.player.getEyeHeight(), eye.y), eye.z);
        }
        BlockPos belowFeet = BlockPos.containing(eye.x, this.targetYLevel + 0.1f, eye.z);
        int feetX = belowFeet.getX();
        int feetZ = belowFeet.getZ();
        if (mc.level.getBlockState(belowFeet).entityCanStandOn(mc.level, belowFeet, mc.player)) return;
        if (this.isAbovePlaceable(eye, belowFeet)) return;
        for (int radius = 1; radius <= 6; radius++) {
            if (this.isAbovePlaceable(eye, new BlockPos(feetX, this.targetYLevel - radius, feetZ))) return;
            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    int yOff = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            BlockPos test = new BlockPos(
                                    feetX + (signX == 0 ? x : -x),
                                    this.targetYLevel - yOff,
                                    feetZ + (signZ == 0 ? z : -z));
                            if (this.isAbovePlaceable(eye, test)) return;
                        }
                    }
                }
            }
        }
    }

    private boolean isAbovePlaceable(Vec3 from, BlockPos pos) {
        if (mc.level == null || mc.player == null) return false;
        if (!(mc.level.getBlockState(pos).getBlock() instanceof AirBlock)) return false;
        BlockPlacementTarget target = BlockPlacementUtil.findBestPlacementTarget(
                pos, mc.player.getMainHandItem(), this.getPlacementOptions());
        if (target != null && from.distanceToSqr(target.targetPoint()) <= 20.25) {
            this.currentPlacement = target;
            return true;
        }
        return false;
    }

    private boolean shouldBuild() {
        if (mc.player == null || mc.level == null) return false;
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        return mc.level.isEmptyBlock(below) && BlockUtil.isPlaceable(mc.player.getMainHandItem());
    }

    private void doSnap() {
        if (this.currentPlacement == null || mc.player == null || mc.gameMode == null) return;
        if (!BlockUtil.isPlaceable(mc.player.getMainHandItem())) return;
        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        if (this.currentPlacement.facing() == Direction.UP && !mc.player.onGround() && MovementUtil.isMoving() && !jumpHeld && !this.mode.is("Normal")) {
            return;
        }
        if (!this.shouldBuild()) return;
        BlockPlacementUtil.place(this.currentPlacement, InteractionHand.MAIN_HAND, this.rots,
                mc.player.getMainHandItem(), this.getPlacementOptions());
    }

    private BlockPlacementOptions getPlacementOptions() {
        return BlockPlacementOptions.defaults()
                .withRange(4.5)
                .withWallRange(4.5)
                .withConstructFailResult(true)
                .withConsiderFacingAwayFaces(true);
    }

    public static boolean isOnBlockEdge(float inflate) {
        if (mc.level == null || mc.player == null) return false;
        return !mc.level.getCollisions(mc.player,
                mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-inflate, 0.0, -inflate))
                .iterator().hasNext();
    }

    private Rotation getTargetRotation(boolean firstGround) {
        if (mc.player == null) return null;
        if (!MovementUtil.isInputActive()) return this.getPlayerYawRotation();
        if (this.currentPlacement == null) return new Rotation(mc.player.getYRot(), mc.player.getXRot());
        Vec3 hitVec = this.currentPlacement.targetPoint();
        Rotation rotation = RotationUtil.rotationFromVec(hitVec);
        double yawDelta = RotationUtil.angleDiffDouble(rotation.getYaw(), RotationHandler.prevRotation.getYaw());
        if (this.groundTicks > 0) {
            if (!mc.options.keyJump.isDown()) {
                return new Rotation(mc.player.getYRot(), 75.5f);
            }
            switch (this.groundTicks) {
                case 1:
                    if (!firstGround) {
                        rotation.setYaw(RotationHandler.prevRotation.getYaw()
                                + RotationUtil.clampAngle((float) yawDelta, (float) (yawDelta / 2.0)));
                        rotation.setPitch(75.5f);
                    } else {
                        rotation = RotationUtil.rotationFromVec(hitVec);
                    }
                    ReflectionUtil.setJumpDelay(2);
                    break;
                case 2:
                    return new Rotation(mc.player.getYRot(), 75.5f);
                default:
                    break;
            }
        } else {
            float clampLimit = this.airTicks == 1 ? 90.0f : 50.0f;
            clampLimit -= RandomUtils.nextFloat(0.001f, 0.006f);
            rotation.setYaw(RotationHandler.prevRotation.getYaw()
                    + RotationUtil.clampAngle((float) yawDelta, clampLimit));
        }
        rotation = this.findValidRotation(rotation, firstGround);
        return this.getSnappedRotation(rotation);
    }

    private Rotation findValidRotation(Rotation rotation, boolean firstGround) {
        if (mc.player == null || firstGround) return rotation;
        Rotation reference = RotationHandler.prevRotation != null
                ? RotationHandler.prevRotation
                : (RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : new Rotation(mc.player.getYRot(), mc.player.getXRot()));
        Rotation optimal = this.getOptimalRotation(rotation, reference.getYaw());
        double maxStep = Math.max(45.0, 180.0 / Math.max(1.0, this.rotationTick.getValue().doubleValue()));
        if (this.mode.is("Telly Bridge")) {
            maxStep = Math.max(maxStep, 75.0);
        }
        return RotationUtil.smoothRotation(reference, optimal, maxStep);
    }

    private Rotation getSnappedRotation(Rotation rotation) {
        if (mc.player == null) return rotation;
        Rotation reference = RotationHandler.prevRotation != null
                ? RotationHandler.prevRotation
                : (RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : new Rotation(mc.player.getYRot(), mc.player.getXRot()));
        this.yawDiff = Math.abs(Mth.wrapDegrees(rotation.getYaw() - reference.getYaw()));
        this.pitchDiff = Math.abs(rotation.getPitch() - reference.getPitch());
        boolean stuckPitch = this.pitchDiff > 2.0
                && !Double.isNaN(this.lastPitchDiff)
                && Math.abs(this.pitchDiff - this.lastPitchDiff) < 1.0e-4;
        boolean stuckYaw = this.yawDiff > 2.0
                && !Double.isNaN(this.lastYawDiff)
                && Math.abs(this.yawDiff - this.lastYawDiff) < 1.0e-4;
        if (stuckPitch || stuckYaw) {
            rotation = rotation.clone();
            float jitterYaw = MathUtil.randomFloat(0.095f, 0.19f);
            float jitterPitch = MathUtil.randomFloat(0.016f, 0.055f);
            if ((this.jitterCounter++ & 1) == 0) {
                jitterYaw = -jitterYaw;
            }
            rotation.setYaw(rotation.getYaw() + jitterYaw);
            rotation.setPitch(Mth.clamp(rotation.getPitch() + jitterPitch, -89.5f, 89.5f));
            this.yawDiff = Math.abs(Mth.wrapDegrees(rotation.getYaw() - reference.getYaw()));
            this.pitchDiff = Math.abs(rotation.getPitch() - reference.getPitch());
        }
        this.lastYawDiff = this.yawDiff;
        this.lastPitchDiff = this.pitchDiff;
        return rotation;
    }

    private Rotation getOptimalRotation(Rotation rotation, float referenceYaw) {
        if (mc.player == null) return rotation;
        Rotation optimal = rotation.clone();
        double limit = 90.0;
        double delta = Mth.wrapDegrees(optimal.getYaw() - referenceYaw);
        if (Math.abs(delta) > limit) {
            optimal.setYaw((float) (referenceYaw + Math.copySign(limit, delta)));
        }
        return optimal;
    }

    public static Vec3 getHitVec(BlockPos pos, Direction direction) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        if (direction != Direction.UP && direction != Direction.DOWN) {
            y += MathUtil.randomDouble(0.3, -0.3);
        } else {
            x += MathUtil.randomDouble(0.3, -0.3);
            z += MathUtil.randomDouble(0.3, -0.3);
        }
        if (direction == Direction.WEST || direction == Direction.EAST) {
            z += MathUtil.randomDouble(0.3, -0.3);
        }
        if (direction == Direction.SOUTH || direction == Direction.NORTH) {
            x += MathUtil.randomDouble(0.3, -0.3);
        }
        return new Vec3(x, y, z);
    }

}
