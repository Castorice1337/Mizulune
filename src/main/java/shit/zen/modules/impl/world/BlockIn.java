/*
 * This file includes logic adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.features.module.modules.world.ModuleBlockIn
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen to use the shared placement base without
 * replacing Mizulune's existing rotation manager.
 */
package shit.zen.modules.impl.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.GodBridgeAssist;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.utils.game.BlockPlacementOptions;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.QueuedBlockPlacer;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class BlockIn extends Module {
    public static BlockIn INSTANCE;

    public final BooleanValue autoDisable = new BooleanValue("Auto Disable", true);
    public final ModeValue placeOrder = new ModeValue("Place Order", "Normal", "Random", "BottomTop", "TopBottom")
            .withDefault("Normal");
    public final BooleanValue autoBlock = new BooleanValue("Auto Block", true);
    public final BooleanValue switchBack = new BooleanValue("Switch Back", true, () -> this.autoBlock.getValue());
    public final BooleanValue useOffhand = new BooleanValue("Use Offhand", true);
    public final NumberValue range = new NumberValue("Range", 4.5, 1.0, 6.0, 0.1);
    public final NumberValue wallRange = new NumberValue("Wall Range", 4.5, 0.0, 6.0, 0.1);
    public final NumberValue cooldown = new NumberValue("Cooldown", 1, 0, 20, 1);
    public final BooleanValue constructFailResult = new BooleanValue("Construct Fail Result", true);
    public final BooleanValue considerBackFaces = new BooleanValue("Consider Back Faces", true);
    public final ModeValue rotationMode = new ModeValue("Rotation Mode", "Silent", "ChangeLook", "Off")
            .withDefault("Silent");
    public final ModeValue smoothMode = new ModeValue("Smooth Mode", "SNAP", "LINEAR", "SIGMOID").withDefault("SNAP");
    public final BooleanValue movementFix = new BooleanValue("Movement Fix", false, () -> !this.rotationMode.is("Off"));

    private final QueuedBlockPlacer placer;
    private BlockPos startPos;
    private boolean rotateClockwise;

    public BlockIn() {
        super("BlockIn", Category.EXPLOIT);
        INSTANCE = this;
        this.placer = new QueuedBlockPlacer(
                this,
                "BlockInPlacer",
                this::findSlot,
                this::getApplyMode,
                this::getSmoothMode,
                this.movementFix::getValue,
                () -> 45);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup blockIn = root.group("block_in", "BlockIn");
        blockIn.add(this.autoDisable);
        blockIn.add(this.placeOrder);

        ValueGroup placerGroup = root.group("placer", "Placer");
        placerGroup.add(this.autoBlock);
        placerGroup.add(this.switchBack);
        placerGroup.add(this.useOffhand);
        placerGroup.add(this.range);
        placerGroup.add(this.wallRange);
        placerGroup.add(this.cooldown);
        placerGroup.add(this.constructFailResult);
        placerGroup.add(this.considerBackFaces);

        ValueGroup rotation = root.group("rotation", "Rotation");
        rotation.add(this.rotationMode);
        rotation.add(this.smoothMode);
        rotation.add(this.movementFix);
    }

    @Override
    protected void onEnable() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            Scaffold.INSTANCE.setEnabled(false);
        }
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isEnabled()) {
            GodBridgeAssist.INSTANCE.setEnabled(false);
        }
        if (AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled()) {
            AutoWebPlace.INSTANCE.setEnabled(false);
        }
        if (mc.player != null) {
            this.startPos = mc.player.blockPosition();
            this.rotateClockwise = ThreadLocalRandom.current().nextBoolean();
            this.placer.updatePositions(this.generatePositions());
        }
        this.placer.enable();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.placer.disable();
        this.startPos = null;
        super.onDisable();
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return;
        }
        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(this.startPos) && !currentPos.equals(this.startPos.above())) {
            this.setEnabled(false);
            return;
        }

        this.placer.updatePositions(this.generatePositions());
        this.placer.prepare(this.getPlacementOptions());
    }

    @EventTarget(value = 5)
    public void onTickLate(TickEvent event) {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return;
        }
        this.placer.place(this.getPlacementOptions(), this.cooldown.getValue().intValue());
        if (this.autoDisable.getValue() && this.placer.isDone()) {
            this.setEnabled(false);
        }
    }

    private List<BlockPos> generatePositions() {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        if (mc.player == null || this.startPos == null) {
            return List.of();
        }

        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        result.add(this.startPos.below());

        Direction direction = mc.player.getDirection();
        for (int side = 0; side < 4; side++) {
            BlockPos value = this.startPos.relative(direction);
            for (int i = 0; i < playerHeight; i++) {
                result.add(value.above(i));
            }
            direction = this.rotateClockwise ? direction.getClockWise() : direction.getCounterClockWise();
        }

        result.add(this.startPos.above(playerHeight));
        return this.orderPositions(new ArrayList<>(result));
    }

    private List<BlockPos> orderPositions(List<BlockPos> positions) {
        if (this.placeOrder.is("Random")) {
            Collections.shuffle(positions);
        } else if (this.placeOrder.is("BottomTop")) {
            positions.sort((first, second) -> Integer.compare(first.getY(), second.getY()));
        } else if (this.placeOrder.is("TopBottom")) {
            positions.sort((first, second) -> Integer.compare(second.getY(), first.getY()));
        }
        return positions;
    }

    private BlockPlacementOptions getPlacementOptions() {
        return BlockPlacementOptions.defaults()
                .withRange(this.range.getValue().doubleValue())
                .withWallRange(this.wallRange.getValue().doubleValue())
                .withConstructFailResult(this.constructFailResult.getValue())
                .withConsiderFacingAwayFaces(this.considerBackFaces.getValue());
    }

    private RotationApplyMode getApplyMode() {
        if (this.rotationMode.is("ChangeLook")) {
            return RotationApplyMode.CHANGE_LOOK;
        }
        if (this.rotationMode.is("Off")) {
            return RotationApplyMode.OFF;
        }
        return RotationApplyMode.SILENT;
    }

    private SmoothMode getSmoothMode() {
        if (this.smoothMode.is("LINEAR")) {
            return SmoothMode.LINEAR;
        }
        if (this.smoothMode.is("SIGMOID")) {
            return SmoothMode.SIGMOID;
        }
        return SmoothMode.SNAP;
    }

    private QueuedBlockPlacer.SlotSelection findSlot(BlockPos pos) {
        if (mc.player == null) {
            return null;
        }

        int selected = mc.player.getInventory().selected;
        if (this.isHotbarPlaceableBlock(selected)) {
            return QueuedBlockPlacer.SlotSelection.hotbar(selected, false);
        }

        if (this.autoBlock.getValue()) {
            int slot = this.findBestHotbarBlockSlot();
            if (slot != -1) {
                return QueuedBlockPlacer.SlotSelection.hotbar(slot, this.switchBack.getValue());
            }
        }

        if (this.useOffhand.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
                return QueuedBlockPlacer.SlotSelection.offhand();
            }
        }
        return null;
    }

    private int findBestHotbarBlockSlot() {
        if (mc.player == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack) && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    private boolean isHotbarPlaceableBlock(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }
}
