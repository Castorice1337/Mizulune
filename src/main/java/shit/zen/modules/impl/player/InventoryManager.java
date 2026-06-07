package shit.zen.modules.impl.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import org.apache.commons.lang3.tuple.Pair;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.GuiMove;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.ItemUtil;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.event.EventTarget;

public class InventoryManager extends Module {
    public static InventoryManager INSTANCE;

    private final NumberSetting actionDelaySetting = new NumberSetting("Delay", 200, 0, 500, 10);
    private final NumberSetting sprintDelayTicksSetting = new NumberSetting("Open Delay", 2, 0, 10, 1);
    private final NumberSetting dropDelaySetting = new NumberSetting("Drop Delay", 200, 0, 500, 10);
    private final BooleanSetting autoArmorSetting = new BooleanSetting("Auto Armor", true);
    private final BooleanSetting throwItemsSetting = new BooleanSetting("Throw Items", true);
    private final ModeSetting offhandItemSetting = new ModeSetting("Offhand Items", "Golden Apple", "Fishing Rod", "None").withDefault("Projectile");
    private final ModeSetting bowPrioritySetting = new ModeSetting("Bow Priority", "Crossbow", "Punch Bow").withDefault("Crossbow");
    private final BooleanSetting inventoryOnlySetting = new BooleanSetting("Inventory Only", true);
    private final BooleanSetting fastThrowSetting = new BooleanSetting("Fast Throw", false);
    private final NumberSetting maxEggsSnowballsSetting = new NumberSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16);
    public final NumberSetting maxBlockSizeSetting = new NumberSetting("Max Block Size", 256, 64, 512, 64);
    private final NumberSetting maxFoodSizeSetting = new NumberSetting("Max Food Size", 128, 32, 256, 32);
    private final NumberSetting maxRodSizeSetting = new NumberSetting("Max Rod Size", 1, 1, 16, 1);
    private final NumberSetting swordSlotSetting = new NumberSetting("Sword Slot", 0, 0, 9, 1);
    private final NumberSetting blockSlotSetting = new NumberSetting("Block Slot", 0, 0, 9, 1);
    private final NumberSetting axeSlotSetting = new NumberSetting("Axe Slot", 0, 0, 9, 1);
    private final NumberSetting pickaxeSlotSetting = new NumberSetting("Pickaxe Slot", 0, 0, 9, 1);
    private final NumberSetting bowSlotSetting = new NumberSetting("Bow Slot", 0, 0, 9, 1);
    private final NumberSetting waterBucketSlotSetting = new NumberSetting("Water Bucket Slot", 0, 0, 9, 1);
    private final NumberSetting pearlSlotSetting = new NumberSetting("Ender Pearl Slot", 0, 0, 9, 1);
    private final NumberSetting goldenAppleSlotSetting = new NumberSetting("Golden Apple Slot", 0, 0, 9, 1);
    private final NumberSetting eggsSnowballsSlotSetting = new NumberSetting("Eggs & Snowballs Slot", 0, 0, 9, 1);
    private final NumberSetting slimeBallSlotSetting = new NumberSetting("Slime Ball Slot", 0, 0, 9, 1);
    private final NumberSetting crystalSlotSetting = new NumberSetting("Crystal Slot", 0, 0, 9, 1);

    private static final Timer actionTimer = new Timer();

    private boolean didInventoryAction = false;
    private boolean pendingOffhandPlace = false;
    private int idleTicks = 0;
    private int sprintWaitTicks = 0;
    public static boolean isPerformingAction = false;
    private boolean wasSprinting = false;
    private boolean skipNextTick = false;
    private boolean justClosedInventory = false;
    private final Queue<Packet<ServerGamePacketListener>> pendingPackets = new ConcurrentLinkedQueue<>();
    private int sprintDelayTicks = 0;

    public InventoryManager() {
        super("InventoryManager", Category.PLAYER, 66);
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        this.sprintDelayTicks = 0;
        isPerformingAction = false;
        this.skipNextTick = false;
        this.justClosedInventory = false;
        this.wasSprinting = false;
        super.onDisable();
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (!this.inventoryOnlySetting.getValue()
                && GuiMove.INSTANCE.isEnabled()
                && (!this.pendingPackets.isEmpty() || isPerformingAction)
                && mc.player != null) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (!event.isIncomingRaw() || mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket) {
            this.didInventoryAction = false;
            this.sprintWaitTicks = 0;
        }

        if (!GuiMove.INSTANCE.isEnabled()) {
            if (this.didInventoryAction && !this.inventoryOnlySetting.getValue()) {
                if (packet instanceof ServerboundMovePlayerPacket) {
                    if (MovementUtil.isInputActive()) {
                        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                    }
                } else if (packet instanceof ServerboundUseItemOnPacket
                        || packet instanceof ServerboundUseItemPacket
                        || packet instanceof ServerboundInteractPacket
                        || packet instanceof ServerboundPlayerActionPacket) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            }
            return;
        }

        if (packet instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                this.wasSprinting = true;
            } else if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                this.wasSprinting = false;
            }
        }

        if (isPerformingAction && packet instanceof ServerboundPlayerInputPacket input) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                event.setCancelled(true);
                PacketUtil.sendQueued(new ServerboundPlayerInputPacket(
                        input.getXxa(), input.getZza(), input.isJumping(), input.isShiftKeyDown()));
                return;
            }
        }

        // If another container is on screen (chest / furnace / etc.) leave its
        // packets alone - we only proxy our own InventoryMenu traffic.
        boolean externalContainerOpen = mc.screen instanceof AbstractContainerScreen acs
                && acs.getMenu().containerId != mc.player.inventoryMenu.containerId;
        if (!externalContainerOpen
                && (packet instanceof ServerboundContainerClickPacket
                        || packet instanceof ServerboundContainerClosePacket)) {
            ChatUtil.print("Cancelled Inventory Packet: " + packet.getClass().getName());
            event.setCancelled(true);
            Packet<ServerGamePacketListener> typed = (Packet<ServerGamePacketListener>) packet;
            this.pendingPackets.add(typed);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPost()
                || this.inventoryOnlySetting.getValue()
                || !GuiMove.INSTANCE.isEnabled()
                || mc.player == null) {
            return;
        }
        if (this.pendingPackets.isEmpty()) {
            this.sprintDelayTicks = 0;
            if (this.justClosedInventory) {
                this.justClosedInventory = false;
            }
            return;
        }
        if (this.wasSprinting || mc.player.isSprinting()) {
            this.skipNextTick = true;
            this.sprintDelayTicks = 2 + this.sprintDelayTicksSetting.getValue().intValue();
            return;
        }
        if (this.sprintDelayTicks > 0) {
            this.sprintDelayTicks--;
            return;
        }
        while (!this.pendingPackets.isEmpty()) {
            Packet<ServerGamePacketListener> packet = this.pendingPackets.poll();
            ChatUtil.print("Releasing Packet: " + packet.getClass().getName());
            PacketUtil.sendQueued(packet);
        }
        PacketUtil.sendQueued(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
        this.justClosedInventory = true;
    }

    private boolean validateSlotConfig() {
        List<Pair<Boolean, NumberSetting>> entries = new ArrayList<>();
        entries.add(Pair.of(this.swordSlotSetting.getValue().intValue() != 0, this.swordSlotSetting));
        entries.add(Pair.of(this.axeSlotSetting.getValue().intValue() != 0, this.axeSlotSetting));
        entries.add(Pair.of(this.pickaxeSlotSetting.getValue().intValue() != 0, this.pickaxeSlotSetting));
        entries.add(Pair.of(this.bowSlotSetting.getValue().intValue() != 0, this.bowSlotSetting));
        entries.add(Pair.of(this.waterBucketSlotSetting.getValue().intValue() != 0, this.waterBucketSlotSetting));
        entries.add(Pair.of(this.pearlSlotSetting.getValue().intValue() != 0, this.pearlSlotSetting));
        entries.add(Pair.of(this.slimeBallSlotSetting.getValue().intValue() != 0, this.slimeBallSlotSetting));
        entries.add(Pair.of(this.crystalSlotSetting.getValue().intValue() != 0, this.crystalSlotSetting));
        entries.add(Pair.of(this.eggsSnowballsSlotSetting.getValue().intValue() != 0, this.eggsSnowballsSlotSetting));
        if (!"Golden Apple".equals(this.offhandItemSetting.getValue())) {
            entries.add(Pair.of(this.goldenAppleSlotSetting.getValue().intValue() != 0, this.goldenAppleSlotSetting));
        }
        if (!"Block".equals(this.offhandItemSetting.getValue())) {
            entries.add(Pair.of(this.blockSlotSetting.getValue().intValue() != 0, this.blockSlotSetting));
        }
        HashSet<Integer> usedSlots = new HashSet<>();
        for (Pair<Boolean, NumberSetting> entry : entries) {
            if (!entry.getKey()) continue;
            int slot = entry.getValue().getValue().intValue() - 1;
            if (!usedSlots.add(slot)) return false;
        }
        return true;
    }

    @EventTarget
    public void onMotionManage(MotionEvent event) {
        if (!event.isPost() || mc.player == null || mc.getConnection() == null || mc.gameMode == null) {
            return;
        }
        if (!this.validateSlotConfig()) {
            isPerformingAction = false;
            this.setEnabled(false);
            this.skipNextTick = true;
            return;
        }
        if (ItemUtil.hasServerItem()) {
            isPerformingAction = false;
            this.skipNextTick = true;
            return;
        }

        if (MovementUtil.isInputActive()) {
            this.idleTicks = 0;
        } else {
            this.idleTicks++;
        }

        boolean externalContainerOpen = false;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (mc.screen instanceof ContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            String chest = Component.translatable("container.chest").getString();
            String chestDouble = Component.translatable("container.chestDouble").getString();
            if (title.equals(chest) || title.equals(chestDouble) || title.equals("Chest")) {
                externalContainerOpen = true;
            }
        }
        if (menu instanceof FurnaceMenu || menu instanceof BrewingStandMenu) {
            externalContainerOpen = true;
        }

        boolean blockedByMode = this.inventoryOnlySetting.getValue()
                ? !(mc.screen instanceof InventoryScreen)
                : !GuiMove.INSTANCE.isEnabled() && this.idleTicks <= 1;

        if (externalContainerOpen
                || ChestStealer.isRateLimited()
                || Scaffold.INSTANCE.isEnabled()
                || blockedByMode) {
            this.pendingOffhandPlace = false;
            this.sprintWaitTicks = 0;
            isPerformingAction = false;
            this.skipNextTick = true;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen acs
                && acs.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            return;
        }

        if (this.inventoryOnlySetting.getValue() && mc.screen instanceof InventoryScreen) {
            this.sprintWaitTicks++;
            if (this.sprintWaitTicks < this.sprintDelayTicksSetting.getValue().intValue()) {
                return;
            }
        }

        if (this.performInventoryAction()) {
            isPerformingAction = true;
        } else {
            isPerformingAction = false;
            this.skipNextTick = true;
        }
    }

    private boolean performInventoryAction() {
        // --- auto armor: drop bad armor we're wearing ---
        if (this.autoArmorSetting.getValue()) {
            for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                ItemStack equipped = mc.player.getInventory().armor.get(i);
                if (equipped.getItem() instanceof ArmorItem armor
                        && !equipped.isEmpty()
                        && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())
                        && ItemUtil.getBestArmorScore(armor.getEquipmentSlot()) > ItemUtil.getArmorScore(equipped)) {
                    mc.gameMode.handleInventoryMouseClick(
                            mc.player.inventoryMenu.containerId,
                            4 + (4 - i), 1, ClickType.THROW, mc.player);
                    this.didInventoryAction = true;
                    actionTimer.reset();
                    return true;
                }
            }
            // --- auto armor: equip best armor in inventory ---
            for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                ItemStack candidate = mc.player.getInventory().items.get(i);
                if (candidate.isEmpty() || !(candidate.getItem() instanceof ArmorItem armor)) continue;
                float candidateScore = ItemUtil.getArmorScore(candidate);
                boolean isBest = ItemUtil.getBestArmorScore(armor.getEquipmentSlot()) == candidateScore;
                boolean betterThanEquipped = ItemUtil.getEquippedArmorScore(armor.getEquipmentSlot()) < candidateScore;
                if (isBest && betterThanEquipped
                        && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
                    int target = i < 9 ? i + 36 : i;
                    mc.gameMode.handleInventoryMouseClick(
                            mc.player.inventoryMenu.containerId,
                            target, 0, ClickType.QUICK_MOVE, mc.player);
                    this.didInventoryAction = true;
                    actionTimer.reset();
                    return true;
                }
            }
        }

        // --- finish a pending offhand swap from the previous tick ---
        if (this.pendingOffhandPlace
                && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                    45, 0, ClickType.PICKUP, mc.player);
            this.didInventoryAction = true;
            this.pendingOffhandPlace = false;
            actionTimer.reset();
        }

        // --- offhand preference ---
        String offhandPref = this.offhandItemSetting.getValue();
        if ("Golden Apple".equals(offhandPref)) {
            ItemStack offhand = mc.player.getInventory().offhand.get(0);
            int slot = ItemUtil.getSlot(Items.GOLDEN_APPLE);
            if (slot != -1 && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
                if (offhand.getItem() != Items.GOLDEN_APPLE) {
                    this.moveToOffhand(slot);
                    return true;
                }
                ItemStack invStack = mc.player.getInventory().items.get(slot);
                if (offhand.getCount() + invStack.getCount() <= 64) {
                    int target = slot < 9 ? slot + 36 : slot;
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                            target, 0, ClickType.PICKUP, mc.player);
                    this.didInventoryAction = true;
                    this.pendingOffhandPlace = true;
                    actionTimer.reset();
                    return true;
                }
            }
        } else if ("Projectile".equals(offhandPref)) {
            ItemStack offhand = mc.player.getInventory().offhand.get(0);
            ItemStack bestProjectile = ItemUtil.getBestProjectile();
            if (bestProjectile != null) {
                int slot = ItemUtil.getSlot(bestProjectile);
                boolean shouldSwap;
                if (offhand.getItem() != Items.EGG && offhand.getItem() != Items.SNOWBALL) {
                    shouldSwap = true;
                } else {
                    shouldSwap = offhand.getCount() < bestProjectile.getCount();
                }
                if (shouldSwap && slot != -1
                        && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
                    this.moveToOffhand(slot);
                    return true;
                }
            }
        } else if ("Fishing Rod".equals(offhandPref)) {
            ItemStack offhand = mc.player.getInventory().offhand.get(0);
            int slot = ItemUtil.getSlot(Items.FISHING_ROD);
            if (slot != -1
                    && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())
                    && offhand.getItem() != Items.FISHING_ROD) {
                this.moveToOffhand(slot);
                return true;
            }
        } else if ("Block".equals(offhandPref)) {
            ItemStack offhand = mc.player.getInventory().offhand.get(0);
            ItemStack bestBlock = ItemUtil.getBestBlock();
            if (bestBlock != null) {
                int slot = ItemUtil.getSlot(bestBlock);
                boolean shouldSwap;
                if (BlockUtil.isPlaceable(offhand)) {
                    shouldSwap = offhand.getCount() < bestBlock.getCount();
                } else {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1
                        && actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
                    this.moveToOffhand(slot);
                    return true;
                }
            }
        }

        // --- hotbar slot assignments ---
        if (!"Golden Apple".equals(this.offhandItemSetting.getValue())
                && this.goldenAppleSlotSetting.getValue().intValue() != 0) {
            this.swapItemToSlot(this.goldenAppleSlotSetting.getValue().intValue() - 1, Items.GOLDEN_APPLE);
        }

        if (this.blockSlotSetting.getValue().intValue() != 0) {
            int slot = this.blockSlotSetting.getValue().intValue() - 1;
            ItemStack current = mc.player.getInventory().items.get(slot);
            ItemStack bestBlock = ItemUtil.getBestBlock();
            if (bestBlock != null
                    && (bestBlock.getCount() > current.getCount() || !BlockUtil.isPlaceable(current))
                    && !"Block".equals(this.offhandItemSetting.getValue())
                    && this.swapToSlot(slot, bestBlock)) {
                return true;
            }
        }

        if (ItemUtil.countBlocks() > this.maxBlockSizeSetting.getValue().intValue()) {
            if (this.throwItem(ItemUtil.getWorstBlock())) return true;
        }
        if (ItemUtil.countFood() > this.maxFoodSizeSetting.getValue().intValue()) {
            if (this.throwItem(ItemUtil.getBestFoodStack())) return true;
        }
        if (ItemUtil.countFishingRods() > this.maxRodSizeSetting.getValue().intValue()) {
            if (this.throwItem(ItemUtil.getFishingRodStack())) return true;
        }
        if (ItemUtil.countItem(Items.EGG) + ItemUtil.countItem(Items.SNOWBALL)
                > this.maxEggsSnowballsSetting.getValue().intValue()) {
            if (this.throwItem(ItemUtil.getWorstProjectile())) return true;
        }

        if (this.swordSlotSetting.getValue().intValue() != 0) {
            ItemStack bestSword = ItemUtil.getBestSword();
            int slot = this.swordSlotSetting.getValue().intValue() - 1;
            ItemStack current = mc.player.getInventory().items.get(slot);
            ItemStack bestSharpAxe = ItemUtil.getBestSharpAxe();
            if (ItemUtil.getAxeDamage(bestSharpAxe) > ItemUtil.getSwordDamage(bestSword)) {
                bestSword = bestSharpAxe;
            }
            if (bestSword != null) {
                float currentDamage = current.getItem() instanceof SwordItem
                        ? ItemUtil.getSwordDamage(current)
                        : ItemUtil.getAxeDamage(current);
                float candidateDamage = bestSword.getItem() instanceof SwordItem
                        ? ItemUtil.getSwordDamage(bestSword)
                        : ItemUtil.getAxeDamage(bestSword);
                if (candidateDamage > currentDamage && this.swapToSlot(slot, bestSword)) {
                    return true;
                }
            }
        }

        if (this.pickaxeSlotSetting.getValue().intValue() != 0) {
            int slot = this.pickaxeSlotSetting.getValue().intValue() - 1;
            ItemStack bestPickaxe = ItemUtil.getBestPickaxe();
            ItemStack current = mc.player.getInventory().items.get(slot);
            if (bestPickaxe != null
                    && bestPickaxe.getItem() instanceof PickaxeItem
                    && (ItemUtil.getDigSpeed(bestPickaxe) > ItemUtil.getDigSpeed(current)
                            || !(current.getItem() instanceof PickaxeItem))
                    && this.swapToSlot(slot, bestPickaxe)) {
                return true;
            }
        }

        if (this.bowSlotSetting.getValue().intValue() != 0) {
            int slot = this.bowSlotSetting.getValue().intValue() - 1;
            ItemStack current = mc.player.getInventory().items.get(slot);
            ItemStack bestBow;
            float bestScore;
            float currentScore;
            if ("Crossbow".equals(this.bowPrioritySetting.getValue())) {
                bestBow = ItemUtil.getBestCrossbow();
                bestScore = ItemUtil.getCrossbowScore(bestBow);
                currentScore = ItemUtil.getCrossbowScore(current);
            } else if ("Power Bow".equals(this.bowPrioritySetting.getValue())) {
                bestBow = ItemUtil.getBestBowAlt();
                bestScore = ItemUtil.getBowScoreAlt(bestBow);
                currentScore = ItemUtil.getBowScoreAlt(current);
            } else {
                bestBow = ItemUtil.getBestBow();
                bestScore = ItemUtil.getBowScore(bestBow);
                currentScore = ItemUtil.getBowScore(current);
            }
            if (bestBow == null) {
                bestBow = ItemUtil.getBestCrossbow();
                bestScore = ItemUtil.getCrossbowScore(bestBow);
                currentScore = ItemUtil.getCrossbowScore(current);
            }
            if (bestBow == null) {
                bestBow = ItemUtil.getBestBowAlt();
                bestScore = ItemUtil.getBowScoreAlt(bestBow);
                currentScore = ItemUtil.getBowScoreAlt(current);
            }
            if (bestBow == null) {
                bestBow = ItemUtil.getBestBow();
                bestScore = ItemUtil.getBowScore(bestBow);
                currentScore = ItemUtil.getBowScore(current);
            }
            if (bestBow != null && bestScore > currentScore && this.swapToSlot(slot, bestBow)) {
                return true;
            }
            if (ItemUtil.countItem(Items.ARROW) > 256) {
                if (this.throwItem(ItemUtil.getArrowStack())) return true;
            }
        }

        if (this.axeSlotSetting.getValue().intValue() != 0) {
            ItemStack bestAxe = ItemUtil.getBestAxe();
            if (this.swapToSlot(this.axeSlotSetting.getValue().intValue() - 1, bestAxe)) {
                return true;
            }
        }

        if (this.eggsSnowballsSlotSetting.getValue().intValue() != 0
                && this.swapToSlot(this.eggsSnowballsSlotSetting.getValue().intValue() - 1,
                        ItemUtil.getBestProjectile())) {
            return true;
        }
        if (this.pearlSlotSetting.getValue().intValue() != 0
                && this.swapItemToSlot(this.pearlSlotSetting.getValue().intValue() - 1, Items.ENDER_PEARL)) {
            return true;
        }
        if (this.waterBucketSlotSetting.getValue().intValue() != 0
                && this.swapItemToSlot(this.waterBucketSlotSetting.getValue().intValue() - 1, Items.WATER_BUCKET)) {
            return true;
        }
        if (this.slimeBallSlotSetting.getValue().intValue() != 0
                && this.swapItemToSlot(this.slimeBallSlotSetting.getValue().intValue() - 1, Items.SLIME_BALL)) {
            return true;
        }
        if (this.crystalSlotSetting.getValue().intValue() != 0
                && this.swapItemToSlot(this.crystalSlotSetting.getValue().intValue() - 1, Items.END_CRYSTAL)) {
            return true;
        }

        // --- last resort: drop the first useless thing we run into ---
        List<Integer> order = IntStream.range(0, mc.player.getInventory().items.size())
                .boxed().collect(Collectors.toList());
        Collections.shuffle(order);
        for (Integer idx : order) {
            ItemStack stack = mc.player.getInventory().items.get(idx);
            if (!stack.isEmpty() && !this.isUsefulItem(stack)) {
                this.throwItem(stack);
                return true;
            }
        }
        return false;
    }

    private void moveToOffhand(int slot) {
        if (mc.gameMode == null || mc.player == null) return;
        int source = slot < 9 ? slot + 36 : slot;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                source, 40, ClickType.SWAP, mc.player);
        this.didInventoryAction = true;
        actionTimer.reset();
    }

    private boolean throwItem(ItemStack stack) {
        if (mc.gameMode == null || mc.player == null) return false;
        if (!this.throwItemsSetting.getValue() || !ItemUtil.isUsable(stack)) return false;
        if (!actionTimer.hasPassed(this.dropDelaySetting.getValue().intValue())
                && !this.fastThrowSetting.getValue()) {
            return false;
        }
        int slot = ItemUtil.getSlot(stack);
        if (slot == -1) return false;
        int source = slot < 9 ? slot + 36 : slot;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                source, 1, ClickType.THROW, mc.player);
        this.didInventoryAction = true;
        actionTimer.reset();
        return true;
    }

    private boolean swapToSlot(int targetSlot, ItemStack stack) {
        if (mc.gameMode == null || mc.player == null) return false;
        ItemStack current = mc.player.getInventory().items.get(targetSlot);
        if (!ItemUtil.isUsable(current) || stack == current
                || !actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
            return false;
        }
        int source = ItemUtil.getSlot(stack);
        if (source == -1) return false;
        int from = source < 9 ? source + 36 : source;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                from, targetSlot, ClickType.SWAP, mc.player);
        this.didInventoryAction = true;
        actionTimer.reset();
        return true;
    }

    private boolean swapItemToSlot(int targetSlot, Item item) {
        if (mc.gameMode == null || mc.player == null) return false;
        ItemStack current = mc.player.getInventory().items.get(targetSlot);
        if (!ItemUtil.isUsable(current)
                || !actionTimer.hasPassed(this.actionDelaySetting.getValue().intValue())) {
            return false;
        }
        int source = ItemUtil.getSlot(item);
        if (source == -1) return false;
        ItemStack sourceStack = mc.player.getInventory().items.get(source);
        if (current.getItem() != item
                || (current.getItem() == item && current.getCount() < sourceStack.getCount())) {
            int from = source < 9 ? source + 36 : source;
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                    from, targetSlot, ClickType.SWAP, mc.player);
            this.didInventoryAction = true;
            actionTimer.reset();
            return true;
        }
        return false;
    }

    public static int getMaxBlockSize() {
        return INSTANCE.maxBlockSizeSetting.getValue().intValue();
    }

    public static int getMaxEggsSnowballsSize() {
        return INSTANCE.maxEggsSnowballsSetting.getValue().intValue();
    }

    public static int getMaxArrows() {
        return 256;
    }

    public static int getMaxWaterBuckets() {
        return 1;
    }

    public static int getMaxLavaBuckets() {
        return 1;
    }

    public boolean isUsefulItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (ItemUtil.isWeaponItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (stack.getItem() == Items.COBWEB) return true;
        if (stack.getItem() instanceof ArmorItem armor) {
            float score = ItemUtil.getArmorScore(stack);
            if (ItemUtil.getEquippedArmorScore(armor.getEquipmentSlot()) >= score) return false;
            return score >= ItemUtil.getBestArmorScore(armor.getEquipmentSlot());
        }
        if (stack.getItem() instanceof SwordItem)   return ItemUtil.getBestSword() == stack;
        if (stack.getItem() instanceof PickaxeItem) return ItemUtil.getBestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !ItemUtil.isLegitAxe(stack)) {
            return ItemUtil.getBestAxe() == stack;
        }
        if (stack.getItem() instanceof ShovelItem)   return ItemUtil.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return ItemUtil.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && ItemUtil.isGoodBow(stack))    return ItemUtil.getBestBow() == stack;
        if (stack.getItem() instanceof BowItem && ItemUtil.isGoodBowAlt(stack)) return ItemUtil.getBestBowAlt() == stack;
        if (stack.getItem() instanceof BowItem && ItemUtil.countItem(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && ItemUtil.countItem(Items.WATER_BUCKET) > getMaxWaterBuckets()) return false;
        if (stack.getItem() == Items.LAVA_BUCKET && ItemUtil.countItem(Items.LAVA_BUCKET)   > getMaxLavaBuckets())  return false;
        if (stack.getItem() instanceof FishingRodItem && ItemUtil.countItem(Items.FISHING_ROD) > 1) return false;
        if (stack.getItem() instanceof ItemNameBlockItem) return false;
        return ItemUtil.isUsableItem(stack);
    }
}
