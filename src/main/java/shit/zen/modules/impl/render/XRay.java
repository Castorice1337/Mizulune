package shit.zen.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import lombok.EqualsAndHashCode;
import shit.zen.utils.game.PlayerUtil;
import shit.zen.utils.misc.ChatUtil;

/**
 * XRay — ore locator with two visual layers:
 * <ul>
 *     <li><b>True see-through</b>: when this module is enabled, {@link shit.zen.patch.BlockPatch}
 *         hooks {@code Block.shouldRenderFace} and renders only the target blocks (the rest of the
 *         terrain becomes invisible). Toggling the module calls {@link
 *         net.minecraft.client.renderer.LevelRenderer#allChanged()} below to rebuild all chunk
 *         meshes so the effect applies/clears instantly.</li>
 *     <li><b>ESP overlay</b>: a background thread flood-fills nearby air pockets, finds adjacent
 *         ore veins, scores them to reject anti-cheat "fake" ores, and draws wireframe boxes plus
 *         on-screen tracers. A "blind scan" additionally tracks block-update packets so ores can be
 *         surfaced even outside the rendered chunk set.</li>
 * </ul>
 */
public class XRay extends Module {
    public static XRay INSTANCE;

    private final ModeSetting xrayModeSetting = new ModeSetting("Block Type", "Ores", "Valuables").withDefault("Ores");
    private final NumberSetting scanRangeSetting = new NumberSetting("Range", 48, 16, 100, 4);
    private final NumberSetting scanIntervalSetting = new NumberSetting("Scan Delay", 500, 50, 3000, 100);
    private final BooleanSetting tracersSetting = new BooleanSetting("Tracers", true);
    private final NumberSetting maxBlocksSetting = new NumberSetting("MaxBlocks", 80000, 10000, 500000, 10000);
    private final BooleanSetting antiFakeOreSetting = new BooleanSetting("Anti-Fake Ore", true);
    private final NumberSetting minAuthScoreSetting = new NumberSetting("Min Auth Score", 1.0, 1.0, 15.0, 0.5, this.antiFakeOreSetting::getValue);
    private final BooleanSetting patternAnalysisSetting = new BooleanSetting("Pattern Analysis", false, this.antiFakeOreSetting::getValue);
    private final BooleanSetting strictHostRockSetting = new BooleanSetting("Strict Host Rock Check", true, this.antiFakeOreSetting::getValue);
    private final BooleanSetting altStartSetting = new BooleanSetting("Enable Alternative Start", true);
    private final NumberSetting minAirBlocksSetting = new NumberSetting("Min Air Blocks Required", 50, 10, 1000, 50);
    private final NumberSetting altSearchRadiusSetting = new NumberSetting("Alternative Search Radius", 5, 1, 10, 1);
    private final BooleanSetting blindScanSetting = new BooleanSetting("Blind Scan", true);
    private final NumberSetting blindRenderRangeSetting = new NumberSetting("Blind Render Range", 32, 8, 1024, 4, this.blindScanSetting::getValue);
    private final NumberSetting minVeinSizeSetting = new NumberSetting("Minimum Vein Size", 1, 1, 10, 1, this.antiFakeOreSetting::getValue);
    private final BooleanSetting diamondSetting = new BooleanSetting("Diamond", true);
    private final BooleanSetting emeraldSetting = new BooleanSetting("Emerald", true);
    private final BooleanSetting redstoneSetting = new BooleanSetting("Redstone", true);
    private final BooleanSetting ironSetting = new BooleanSetting("Iron", true);
    private final BooleanSetting goldSetting = new BooleanSetting("Gold", true);
    private final BooleanSetting copperSetting = new BooleanSetting("Copper", true);
    private final BooleanSetting lapisSetting = new BooleanSetting("Lapis", true);
    private final BooleanSetting coalSetting = new BooleanSetting("Coal", true);
    private final BooleanSetting quartzSetting = new BooleanSetting("Quartz", true);
    private final BooleanSetting ancientDebrisSetting = new BooleanSetting("Ancient Debris", true);
    private final BooleanSetting spawnerSetting = new BooleanSetting("Spawner", true);
    private final BooleanSetting endPortalSetting = new BooleanSetting("EndPortalFrame", true);
    private final Color diamondColor = new Color(0, 255, 255);
    private final Color emeraldColor = new Color(0, 255, 0);
    private final Color redstoneColor = new Color(255, 0, 0);
    private final Color ironColor = new Color(210, 210, 210);
    private final Color goldColor = new Color(255, 215, 0);
    private final Color copperColor = new Color(205, 127, 50);
    private final Color lapisColor = new Color(0, 0, 255);
    private final Color coalColor = new Color(50, 50, 50);
    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();
    private volatile Map<BlockPos, Block> pendingBlocks = null;
    private long lastScanTime = 0L;
    private boolean scanInProgress = false;
    private final Map<BlockKey, BlockState> blindScanBlocks = new ConcurrentHashMap<>();
    private Level currentLevel;
    private static ThreadPoolExecutor threadPool;
    private volatile long generationId = 0L;
    private Matrix4f modelviewMatrix;
    private Matrix4f projectionMatrix;
    private final Map<String, ChunkState> chunkStateCache = new ConcurrentHashMap<>();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Set<Block> OVERWORLD_HOST_BLOCKS = Set.of(
            Blocks.STONE,
            Blocks.ANDESITE,
            Blocks.DIORITE,
            Blocks.GRANITE,
            Blocks.DEEPSLATE,
            Blocks.TUFF,
            Blocks.CALCITE,
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.PODZOL,
            Blocks.MYCELIUM,
            Blocks.GRAVEL,
            Blocks.DRIPSTONE_BLOCK,
            Blocks.SAND,
            Blocks.SANDSTONE,
            Blocks.RED_SAND,
            Blocks.RED_SANDSTONE
    );
    private static final Set<Block> NETHER_HOST_BLOCKS = Set.of(
            Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL
    );

    public XRay() {
        super("XRay", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.clearState();
        this.currentLevel = null;
        if (threadPool == null || threadPool.isShutdown()) {
            threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        }
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    public void onDisable() {
        this.clearState();
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    private void clearState() {
        this.foundBlocks.clear();
        this.pendingBlocks = null;
        this.scanInProgress = false;
        this.blindScanBlocks.clear();
        this.modelviewMatrix = null;
        this.projectionMatrix = null;
        this.chunkStateCache.clear();
        this.generationId++;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!ZenClient.isReady()) {
            return;
        }
        if (mc.level != this.currentLevel) {
            this.resetForNewWorld();
        }
        if (this.pendingBlocks != null) {
            this.foundBlocks.clear();
            this.foundBlocks.putAll(this.pendingBlocks);
            this.pendingBlocks = null;
        }
        if (System.currentTimeMillis() - this.lastScanTime > this.scanIntervalSetting.getValue().longValue() && !this.scanInProgress) {
            this.scanInProgress = true;
            long generation = this.generationId;
            threadPool.execute(() -> {
                try {
                    Map<BlockPos, Block> result = new ConcurrentHashMap<>();
                    this.scanFromPos(mc.player.blockPosition(), result);
                    if (generation == this.generationId) {
                        this.pendingBlocks = result;
                    }
                } finally {
                    this.scanInProgress = false;
                    this.lastScanTime = System.currentTimeMillis();
                }
            });
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!ZenClient.isReady()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            this.foundBlocks.remove(blockUpdate.getPos());
            if (this.blindScanSetting.getValue()) {
                this.blindScanBlocks.put(new BlockKey(blockUpdate.getPos()), blockUpdate.getBlockState());
            }
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates((pos, state) -> {
                this.foundBlocks.remove(pos);
                if (this.blindScanSetting.getValue()) {
                    this.blindScanBlocks.put(new BlockKey(pos), state);
                }
            });
        }
    }

    private void resetForNewWorld() {
        this.clearState();
        this.currentLevel = mc.level;
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        ChatUtil.print(ChatFormatting.GREEN + "Reset");
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.gameRenderer.getMainCamera().isInitialized()) {
            this.modelviewMatrix = new Matrix4f(event.poseStack().last().pose());
            this.projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        }
        if (!ZenClient.isReady()) {
            return;
        }
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.poseStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (!this.foundBlocks.isEmpty()) {
            double rangeSq = Math.pow(this.scanRangeSetting.getValue().doubleValue(), 2.0);
            for (Map.Entry<BlockPos, Block> entry : this.foundBlocks.entrySet()) {
                if (entry.getKey().distSqr(mc.player.blockPosition()) <= rangeSq) {
                    this.renderBlock(poseStack, entry.getKey(), entry.getValue().defaultBlockState(), cameraPos);
                }
            }
        }
        if (this.blindScanSetting.getValue() && !this.blindScanBlocks.isEmpty()) {
            double blindRangeSq = Math.pow(this.blindRenderRangeSetting.getValue().doubleValue(), 2.0);
            this.blindScanBlocks.forEach((key, state) -> {
                if (this.isEnabledOreBlock(state.getBlock())) {
                    BlockPos pos = key.toBlockPos();
                    if (pos.distSqr(mc.player.blockPosition()) <= blindRangeSq) {
                        this.renderBlock(poseStack, pos, state, cameraPos);
                    }
                }
            });
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderBlock(PoseStack poseStack, BlockPos pos, BlockState state, Vec3 cameraPos) {
        if (state == null || state.isAir()) {
            return;
        }
        Color color = this.getBlockColor(state.getBlock());
        if (color == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
        this.drawWireBox(poseStack, new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), color);
        poseStack.popPose();
    }

    private void scanRegion(int radius, Map<BlockPos, Block> out) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = new BlockPos(
                            (int) (mc.player.getX() + dx),
                            (int) (mc.player.getY() + dy),
                            (int) (mc.player.getZ() + dz));
                    Block block = PlayerUtil.getBlock(pos);
                    if (this.isSpecialBlock(block)) {
                        out.put(pos, block);
                    }
                }
            }
        }
    }

    private boolean isSpecialBlock(Block block) {
        return this.spawnerSetting.getValue() && block == Blocks.SPAWNER
                || this.quartzSetting.getValue() && block == Blocks.NETHER_QUARTZ_ORE
                || this.ancientDebrisSetting.getValue() && block == Blocks.ANCIENT_DEBRIS
                || this.endPortalSetting.getValue() && (block == Blocks.END_PORTAL_FRAME || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY);
    }

    private void scanFromPos(BlockPos origin, Map<BlockPos, Block> out) {
        if (mc.level == null) {
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos airPos : this.floodFillVein(origin)) {
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = airPos.relative(dir);
                if (!this.isAirOrFluid(neighbor)) {
                    Block block = PlayerUtil.getBlock(neighbor);
                    if (this.isEnabledOreBlock(block)) {
                        this.analyzeAndAddVein(neighbor, block, out, visited);
                    }
                }
            }
        }
        int range = this.scanRangeSetting.getValue().intValue();
        int extendedRange = range + 40;
        this.scanRegion(extendedRange, out);
    }

    private Color getBlockColor(Block block) {
        if (block == null) {
            return null;
        }
        if (this.isEnabledOreBlock(block) || this.isSpecialBlock(block)) {
            if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
                return this.diamondColor;
            }
            if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
                return this.emeraldColor;
            }
            if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
                return this.redstoneColor;
            }
            if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
                return this.ironColor;
            }
            if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) {
                return this.goldColor;
            }
            if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
                return this.copperColor;
            }
            if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
                return this.lapisColor;
            }
            if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
                return this.coalColor;
            }
            if (block == Blocks.NETHER_QUARTZ_ORE) {
                return new Color(255, 255, 255);
            }
            if (block == Blocks.ANCIENT_DEBRIS) {
                return new Color(255, 33, 0);
            }
            if (block == Blocks.SPAWNER) {
                return new Color(0, 85, 255);
            }
            if (block == Blocks.END_PORTAL_FRAME) {
                return new Color(253, 0, 250);
            }
        }
        return null;
    }

    private boolean isEnabledOreBlock(Block block) {
        if (block == null) {
            return false;
        }
        String mode = this.xrayModeSetting.getValue();
        if (mode.equalsIgnoreCase("Ores")) {
            return this.diamondSetting.getValue() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)
                    || this.emeraldSetting.getValue() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)
                    || this.redstoneSetting.getValue() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)
                    || this.ironSetting.getValue() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)
                    || this.goldSetting.getValue() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)
                    || this.copperSetting.getValue() && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)
                    || this.lapisSetting.getValue() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
                    || this.coalSetting.getValue() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE);
        } else if (mode.equalsIgnoreCase("Valuables")) {
            return block == Blocks.ANCIENT_DEBRIS
                    || block == Blocks.NETHER_GOLD_ORE
                    || block == Blocks.NETHER_QUARTZ_ORE
                    || block == Blocks.OBSIDIAN
                    || block == Blocks.AMETHYST_BLOCK
                    || block == Blocks.BUDDING_AMETHYST;
        } else {
            return false;
        }
    }

    /**
     * True iff {@code block} is currently a target block under the active mode and per-ore toggles.
     * Used by {@link shit.zen.patch.BlockPatch} to decide which faces survive the see-through pass,
     * so the terrain hook respects exactly the same settings as the ESP overlay.
     */
    public boolean isXrayVisible(Block block) {
        return block != null && (this.isEnabledOreBlock(block) || this.isSpecialBlock(block));
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (ZenClient.isReady()
                && (!this.foundBlocks.isEmpty() || !this.blindScanBlocks.isEmpty())
                && this.tracersSetting.getValue()
                && this.modelviewMatrix != null
                && this.projectionMatrix != null) {
            this.renderTracers(event.poseStack());
        }
    }

    private void drawWireBox(PoseStack poseStack, AABB box, Color color) {
        float r = color.getRed() / 255.0F;
        float g = color.getGreen() / 255.0F;
        float b = color.getBlue() / 255.0F;
        float a = 0.8F;
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderTracers(PoseStack poseStack) {
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0F;
        float centerY = screenHeight / 2.0F;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        double rangeSq = Math.pow(this.scanRangeSetting.getValue().doubleValue(), 2.0);
        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        for (Map.Entry<BlockPos, Block> entry : this.foundBlocks.entrySet()) {
            this.renderSingleTracer(entry.getKey(), entry.getValue().defaultBlockState(), eyePos, rangeSq, cameraPos,
                    buffer, poseStack, centerX, centerY, screenWidth, screenHeight);
        }
        if (this.blindScanSetting.getValue()) {
            double blindRangeSq = Math.pow(this.blindRenderRangeSetting.getValue().doubleValue(), 2.0);
            for (Map.Entry<BlockKey, BlockState> entry : this.blindScanBlocks.entrySet()) {
                if (this.isEnabledOreBlock(entry.getValue().getBlock())) {
                    this.renderSingleTracer(entry.getKey().toBlockPos(), entry.getValue(), eyePos, blindRangeSq, cameraPos,
                            buffer, poseStack, centerX, centerY, screenWidth, screenHeight);
                }
            }
        }
        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderSingleTracer(BlockPos pos, BlockState state, Vec3 eyePos, double rangeSq, Vec3 cameraPos,
                                    BufferBuilder buffer, PoseStack poseStack, float centerX, float centerY,
                                    float screenWidth, float screenHeight) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (center.subtract(eyePos).lengthSqr() > rangeSq) {
            return;
        }
        Vector3f projected = this.projectPosition(center, cameraPos);
        if (projected == null) {
            return;
        }
        float screenX = projected.x;
        float screenY = projected.y;
        boolean onScreen = projected.z > 0.0F;
        Color color = this.getBlockColor(state.getBlock());
        if (color == null) {
            return;
        }
        float r = color.getRed() / 255.0F;
        float g = color.getGreen() / 255.0F;
        float b = color.getBlue() / 255.0F;
        float a = 0.8F;
        if (!onScreen) {
            Vector3f clamped = this.clampToScreen(centerX, centerY, screenX, screenY, screenWidth, screenHeight);
            screenX = clamped.x;
            screenY = clamped.y;
        }
        buffer.vertex(poseStack.last().pose(), centerX, centerY, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(poseStack.last().pose(), screenX, screenY, 0.0F).color(r, g, b, a).endVertex();
    }

    private Vector3f projectPosition(Vec3 worldPos, Vec3 cameraPos) {
        if (this.modelviewMatrix == null || this.projectionMatrix == null) {
            return null;
        }
        Vector4f vec = new Vector4f(
                (float) (worldPos.x - cameraPos.x),
                (float) (worldPos.y - cameraPos.y),
                (float) (worldPos.z - cameraPos.z),
                1.0F);
        vec.mul(this.modelviewMatrix);
        vec.mul(this.projectionMatrix);
        if (vec.w() <= 0.0F) {
            return null;
        }
        vec.mul(1.0F / vec.w());
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        float screenX = (vec.x() * 0.5F + 0.5F) * width;
        float screenY = (-vec.y() * 0.5F + 0.5F) * height;
        boolean onScreen = screenX >= 0.0F && screenX <= width && screenY >= 0.0F && screenY <= height;
        return new Vector3f(screenX, screenY, onScreen ? 1.0F : -1.0F);
    }

    private Vector3f clampToScreen(float centerX, float centerY, float targetX, float targetY, float screenWidth, float screenHeight) {
        float dx = targetX - centerX;
        float dy = targetY - centerY;
        if (Math.abs(dx) < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
            return new Vector3f(centerX, centerY, 0.0F);
        }
        float t = Float.MAX_VALUE;
        if (dx > 0.0F) {
            t = Math.min(t, (screenWidth - centerX) / dx);
        }
        if (dx < 0.0F) {
            t = Math.min(t, -centerX / dx);
        }
        if (dy > 0.0F) {
            t = Math.min(t, (screenHeight - centerY) / dy);
        }
        if (dy < 0.0F) {
            t = Math.min(t, -centerY / dy);
        }
        return new Vector3f(centerX + t * dx, centerY + t * dy, 0.0F);
    }

    private Set<BlockPos> floodFillVein(BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        int maxBlocks = this.maxBlocksSetting.getValue().intValue();
        if (this.isAirOrFluid(origin) && visited.add(origin)) {
            queue.add(origin);
        }
        BlockPos above = origin.above();
        if (this.isAirOrFluid(above) && visited.add(above)) {
            queue.add(above);
        }
        if (queue.isEmpty()) {
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = origin.relative(dir);
                if (this.isAirOrFluid(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                    break;
                }
            }
        }
        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            BlockPos current = queue.poll();
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = current.relative(dir);
                if (this.isAirOrFluid(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        if (this.altStartSetting.getValue() && visited.size() < this.minAirBlocksSetting.getValue().intValue()) {
            BlockPos altStart = this.findAlternativeStart(origin);
            if (altStart != null) {
                return this.collectConnectedOre(altStart, maxBlocks);
            }
        }
        return visited;
    }

    private BlockPos findAlternativeStart(BlockPos origin) {
        int radius = this.altSearchRadiusSetting.getValue().intValue();
        int minAir = this.minAirBlocksSetting.getValue().intValue();
        BlockPos best = null;
        int bestCount = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!pos.equals(origin) && this.isAirOrFluid(pos)) {
                        int count = this.countConnectedOre(pos, minAir);
                        if (count > bestCount) {
                            bestCount = count;
                            best = pos;
                            if (count >= minAir) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return bestCount >= minAir ? best : null;
    }

    private int countConnectedOre(BlockPos origin, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        if (this.isAirOrFluid(origin)) {
            queue.add(origin);
            visited.add(origin);
        }
        int count = 0;
        while (!queue.isEmpty() && count < limit) {
            BlockPos current = queue.poll();
            count++;
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && this.isAirOrFluid(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return count;
    }

    private Set<BlockPos> collectConnectedOre(BlockPos origin, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        if (this.isAirOrFluid(origin)) {
            queue.add(origin);
            visited.add(origin);
        }
        while (!queue.isEmpty() && visited.size() < limit) {
            BlockPos current = queue.poll();
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = current.relative(dir);
                if (this.isAirOrFluid(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    private void analyzeAndAddVein(BlockPos start, Block ore, Map<BlockPos, Block> out, Set<BlockPos> visited) {
        if (visited.contains(start)) {
            return;
        }
        List<BlockPos> vein = this.collectVeinBlocks(start, ore, visited);
        if (vein.isEmpty()) {
            return;
        }
        visited.addAll(vein);
        if (this.antiFakeOreSetting.getValue()) {
            OreScore score = this.calcOreScore(vein, ore);
            if (score.isAuthentic(this.minAuthScoreSetting.getValue().doubleValue())) {
                for (BlockPos pos : vein) {
                    out.put(pos, ore);
                }
            }
        } else {
            for (BlockPos pos : vein) {
                out.put(pos, ore);
            }
        }
    }

    private List<BlockPos> collectVeinBlocks(BlockPos start, Block ore, Set<BlockPos> globalVisited) {
        List<BlockPos> vein = new ArrayList<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> localVisited = new HashSet<>();
        if (globalVisited.contains(start)) {
            return vein;
        }
        queue.add(start);
        localVisited.add(start);
        int maxVeinSize = 64;
        while (!queue.isEmpty() && vein.size() < maxVeinSize) {
            BlockPos current = queue.poll();
            vein.add(current);
            for (Direction dir : DIRECTIONS) {
                BlockPos neighbor = current.relative(dir);
                if (!globalVisited.contains(neighbor) && !localVisited.contains(neighbor)
                        && mc.level.isLoaded(neighbor) && this.isSameOreFamily(PlayerUtil.getBlock(neighbor), ore)) {
                    localVisited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return vein;
    }

    private OreScore calcOreScore(List<BlockPos> vein, Block ore) {
        if (!ZenClient.isReady() || vein.isEmpty()) {
            return new OreScore();
        }
        OreScore score = new OreScore();
        BlockPos first = vein.get(0);
        int size = vein.size();
        score.veinSizeScore = this.calcVeinSizeScore(size);
        if (size < this.minVeinSizeSetting.getValue().intValue()) {
            score.veinSizeScore = -20.0;
            return score;
        }
        score.chunkStateScore = this.calcChunkStateScore(first, ore);
        score.depthScore = this.calcDepthScore(first, ore);
        if (score.depthScore <= -10.0) {
            return score;
        }
        if (this.strictHostRockSetting.getValue()) {
            double hostSum = 0.0;
            int airExposureCount = 0;
            for (BlockPos pos : vein) {
                hostSum += this.calcHostRockScore(pos, ore);
                for (Direction dir : DIRECTIONS) {
                    if (this.isAirOrFluid(pos.relative(dir))) {
                        airExposureCount++;
                    }
                }
            }
            score.hostRockScore = hostSum / size;
            double avgAirExposure = (double) airExposureCount / size;
            if (avgAirExposure >= 3.0) {
                score.airExposureScore = 3.0;
            } else if (avgAirExposure >= 1.0) {
                score.airExposureScore = 1.5;
            } else if (avgAirExposure > 0.0) {
                score.airExposureScore = 0.5;
            } else {
                score.airExposureScore = 0.0;
            }
        }
        if (this.patternAnalysisSetting.getValue()) {
            score.patternScore = this.calcPatternScore(first, 8);
        }
        return score;
    }

    private double calcHostRockScore(BlockPos pos, Block ore) {
        Set<Block> hostBlocks = this.getHostBlockSet(ore);
        if (hostBlocks.isEmpty()) {
            return 1.0;
        }
        int hostCount = 0;
        int foreignCount = 0;
        int airCount = 0;
        for (Direction dir : DIRECTIONS) {
            BlockPos neighbor = pos.relative(dir);
            Block block = PlayerUtil.getBlock(neighbor);
            if (hostBlocks.contains(block)) {
                hostCount++;
            } else if (this.isAirOrFluid(neighbor)) {
                airCount++;
            } else if (!this.isSameOreFamily(block, ore) && !this.isOreBlock(block) && !this.isWoodOrManMade(block)) {
                foreignCount++;
            }
        }
        if (hostCount == 0 && foreignCount >= 4 && airCount <= 1) {
            return -5.0;
        } else if (airCount < 2) {
            double s = 0.0;
            if (hostCount >= 4) {
                s += 3.0;
            } else if (hostCount >= 2) {
                s += 1.0;
            } else if (hostCount == 1) {
                s += 0.5;
            }
            return s;
        } else if (hostCount >= 1) {
            return 2.0;
        } else {
            return foreignCount <= 2 ? 0.5 : -1.0;
        }
    }

    private boolean isWoodOrManMade(Block block) {
        if (block == null) {
            return false;
        }
        String path = block.builtInRegistryHolder().key().location().getPath();
        return path.contains("_planks")
                || path.contains("_log")
                || path.contains("_fence")
                || path.contains("_stairs")
                || path.contains("rail")
                || path.equals("torch")
                || path.equals("wall_torch")
                || path.equals("cobweb")
                || path.equals("ladder")
                || path.equals("chest")
                || path.equals("trapped_chest");
    }

    private Set<Block> getHostBlockSet(Block ore) {
        String path = ore.builtInRegistryHolder().key().location().getPath();
        if (path.startsWith("nether_") || path.equals("ancient_debris")) {
            return NETHER_HOST_BLOCKS;
        }
        return path.contains("_ore") ? OVERWORLD_HOST_BLOCKS : Collections.emptySet();
    }

    private double calcVeinSizeScore(int size) {
        if (size == 1) {
            return 2.0;
        } else if (size >= 2 && size <= 4) {
            return 2.5;
        } else {
            return size >= 5 && size <= 10 ? 3.5 : 3.0;
        }
    }

    private boolean isAirOrFluid(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        Block block = PlayerUtil.getBlock(pos);
        return block.defaultBlockState().isAir() || block == Blocks.WATER || block == Blocks.LAVA
                || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    private double calcChunkStateScore(BlockPos pos, Block ore) {
        if (!ZenClient.isReady()) {
            return 0.0;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        Set<Block> family = this.getOreFamilySet(ore);
        if (family.isEmpty()) {
            return 0.0;
        }
        Block familyKey = family.stream()
                .min(Comparator.comparing(candidate -> candidate.builtInRegistryHolder().key().location().toString()))
                .orElse(ore);
        String cacheKey = chunkX + ":" + chunkZ + ":" + familyKey.builtInRegistryHolder().key().location();
        ChunkState cached = this.chunkStateCache.get(cacheKey);
        if (cached == ChunkState.SUSPICIOUS) {
            return -5.0;
        } else if (cached == ChunkState.AUTHENTIC) {
            return 1.0;
        }
        BlockPos playerPos = mc.player.blockPosition();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int chunkDistance = Math.abs(playerChunkX - chunkX) + Math.abs(playerChunkZ - chunkZ);
        if (chunkDistance <= 3) {
            this.chunkStateCache.put(cacheKey, ChunkState.AUTHENTIC);
            return 1.0;
        }
        this.chunkStateCache.put(cacheKey, ChunkState.SCANNING);
        short threshold;
        if (family.contains(Blocks.DIAMOND_ORE) || family.contains(Blocks.EMERALD_ORE)) {
            threshold = 30;
        } else if (!family.contains(Blocks.GOLD_ORE) && !family.contains(Blocks.LAPIS_ORE)) {
            threshold = 200;
        } else {
            threshold = 50;
        }
        int oreCount = 0;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.level.getMinBuildHeight(); y < mc.level.getMaxBuildHeight(); y++) {
                    if (this.isSameOreFamily(mc.level.getBlockState(new BlockPos(baseX + x, y, baseZ + z)).getBlock(), ore)) {
                        oreCount++;
                    }
                }
            }
        }
        if (oreCount > threshold) {
            this.chunkStateCache.put(cacheKey, ChunkState.SUSPICIOUS);
            return -5.0;
        } else {
            this.chunkStateCache.put(cacheKey, ChunkState.AUTHENTIC);
            return 0.0;
        }
    }

    private double calcPatternScore(BlockPos center, int radius) {
        Map<Block, List<BlockPos>> oresByFamily = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (mc.level.isLoaded(pos)) {
                        Block block = PlayerUtil.getBlock(pos);
                        if (this.isOreBlock(block)) {
                            this.getOreFamilySet(block).stream().findFirst().ifPresent(
                                    familyKey -> oresByFamily.computeIfAbsent(familyKey, k -> new ArrayList<>()).add(pos));
                        }
                    }
                }
            }
        }
        for (List<BlockPos> group : oresByFamily.values()) {
            if (group.size() >= 2 && !this.isVeinConnected(group) && this.isPatternSuspicious(group)) {
                return -10.0;
            }
        }
        return 0.0;
    }

    private boolean isVeinConnected(List<BlockPos> blocks) {
        if (blocks.size() <= 1) {
            return true;
        }
        Set<BlockPos> all = new HashSet<>(blocks);
        Set<BlockPos> visited = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        BlockPos start = blocks.get(0);
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (all.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return visited.size() == blocks.size();
    }

    private double calcDepthScore(BlockPos pos, Block ore) {
        int y = pos.getY();
        Set<Block> family = this.getOreFamilySet(ore);
        if (family.contains(Blocks.DIAMOND_ORE)) {
            if (y >= -64 && y <= 16) {
                return 2.0;
            }
            return y > 16 && y <= 32 ? 0.5 : -10.0;
        } else if (family.contains(Blocks.EMERALD_ORE)) {
            return y >= -16 && y <= 320 ? 2.0 : -10.0;
        } else if (family.contains(Blocks.GOLD_ORE)) {
            if (y >= -64 && y <= 32) {
                return 2.0;
            }
            return y > 32 && y < 80 ? -1.5 : -5.0;
        } else if (!family.contains(Blocks.IRON_ORE)) {
            if (family.contains(Blocks.COAL_ORE)) {
                return y >= 0 && y <= 256 ? 1.0 : 0.0;
            } else if (family.contains(Blocks.COPPER_ORE)) {
                return y >= -16 && y <= 112 ? 1.5 : 0.5;
            } else if (family.contains(Blocks.LAPIS_ORE)) {
                return y >= -64 && y <= 64 ? 1.5 : 0.0;
            } else if (family.contains(Blocks.REDSTONE_ORE)) {
                return y >= -64 && y <= 16 ? 1.5 : 0.0;
            } else if (family.contains(Blocks.ANCIENT_DEBRIS)) {
                if (y >= 8 && y <= 22) {
                    return 2.0;
                }
                return y < 120 ? 1.0 : -15.0;
            } else {
                return 0.5;
            }
        } else {
            return (y < -24 || y > 72) && (y <= 80 || y > 320) ? 1.0 : 1.5;
        }
    }

    private boolean isOreBlock(Block block) {
        if (block == null) {
            return false;
        }
        return block.builtInRegistryHolder().key().location().getPath().contains("_ore");
    }

    private boolean isPatternSuspicious(List<BlockPos> blocks) {
        if (blocks.size() < 4) {
            return false;
        }
        Map<Integer, Integer> xGaps = new HashMap<>();
        Map<Integer, Integer> yGaps = new HashMap<>();
        Map<Integer, Integer> zGaps = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                BlockPos a = blocks.get(i);
                BlockPos b = blocks.get(j);
                int dx = Math.abs(a.getX() - b.getX());
                int dy = Math.abs(a.getY() - b.getY());
                int dz = Math.abs(a.getZ() - b.getZ());
                if (dx > 1) {
                    xGaps.put(dx, xGaps.getOrDefault(dx, 0) + 1);
                }
                if (dy > 1) {
                    yGaps.put(dy, yGaps.getOrDefault(dy, 0) + 1);
                }
                if (dz > 1) {
                    zGaps.put(dz, zGaps.getOrDefault(dz, 0) + 1);
                }
            }
        }
        int limit = blocks.size() > 5 ? 3 : 2;
        return xGaps.values().stream().anyMatch(count -> count > limit)
                || yGaps.values().stream().anyMatch(count -> count > limit)
                || zGaps.values().stream().anyMatch(count -> count > limit);
    }

    private boolean isSameOreFamily(Block a, Block b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        Set<Block> familyA = this.getOreFamilySet(a);
        Set<Block> familyB = this.getOreFamilySet(b);
        return !familyA.isEmpty() && familyA.equals(familyB);
    }

    private Set<Block> getOreFamilySet(Block block) {
        if (block == null) {
            return Collections.emptySet();
        } else if (Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE).contains(block)) {
            return Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        } else if (Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE).contains(block)) {
            return Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        } else if (Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE).contains(block)) {
            return Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        } else if (Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE).contains(block)) {
            return Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        } else if (Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE).contains(block)) {
            return Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        } else if (Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE).contains(block)) {
            return Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        } else if (Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE).contains(block)) {
            return Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        } else if (Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE).contains(block)) {
            return Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
        } else if (block == Blocks.ANCIENT_DEBRIS) {
            return Set.of(Blocks.ANCIENT_DEBRIS);
        } else {
            return block == Blocks.NETHER_QUARTZ_ORE ? Set.of(Blocks.NETHER_QUARTZ_ORE) : Collections.emptySet();
        }
    }

    /** Aggregated authenticity score for a candidate ore vein (anti "fake ore" heuristic). */
    private static final class OreScore {
        double hostRockScore = 0.0;
        double chunkStateScore = 0.0;
        double veinSizeScore = 0.0;
        double patternScore = 0.0;
        double airExposureScore = 0.0;
        double depthScore = 0.0;
        double extraScore = 0.0;

        double getTotalScore() {
            return this.hostRockScore + this.chunkStateScore + this.veinSizeScore
                    + this.patternScore + this.airExposureScore + this.depthScore + this.extraScore;
        }

        boolean isAuthentic(double minScore) {
            return this.getTotalScore() >= minScore;
        }
    }

    /** Immutable integer block coordinate, used as a hash-map key for the packet-driven blind scan. */
    @EqualsAndHashCode
    private static final class BlockKey {
        private final int x;
        private final int y;
        private final int z;

        BlockKey(BlockPos pos) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }

        BlockPos toBlockPos() {
            return new BlockPos(this.x, this.y, this.z);
        }
    }

    /** Per-chunk authenticity cache entry for {@link #calcChunkStateScore}. */
    private enum ChunkState {
        AUTHENTIC,
        SUSPICIOUS,
        SCANNING
    }
}
