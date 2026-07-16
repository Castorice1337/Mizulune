package shit.zen.music.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.impl.world.MizuluneMusic;
import shit.zen.music.MusicService;
import shit.zen.music.model.LyricLine;
import shit.zen.music.model.MusicPlaybackState;
import shit.zen.music.model.MusicTrack;
import shit.zen.music.model.PlayMode;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlyphMetrics;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Rectangle;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.Argb;

public class MusicScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.tryParse("mizulune:textures/gui/mizulune_logo.png");
    private static final FontRenderer TITLE = FontPresets.pingfang(24.0f);
    private static final FontRenderer H1 = FontPresets.pingfang(20.0f);
    private static final FontRenderer BODY = FontPresets.pingfang(15.0f);
    private static final FontRenderer SMALL = FontPresets.pingfang(13.0f);
    private static final FontRenderer ICON = FontPresets.materialIcons(18.0f);
    private static final FontRenderer ICON_SMALL = FontPresets.materialIcons(15.0f);
    private static final FontRenderer ICON_LARGE = FontPresets.materialIcons(22.0f);

    private static final String ICON_PLAYER = "\uE405";
    private static final String ICON_SEARCH = "\uE8B6";
    private static final String ICON_QUEUE = "\uE03D";
    private static final String ICON_INFO = "\uE88E";
    private static final String ICON_PLAY = "\uE037";
    private static final String ICON_PAUSE = "\uE034";
    private static final String ICON_PREVIOUS = "\uE045";
    private static final String ICON_NEXT = "\uE044";
    private static final String ICON_DOWNLOAD = "\uE2C4";
    private static final String ICON_ADD = "\uE145";
    private static final String ICON_CLOSE = "\uE5CD";
    private static final String ICON_REPEAT = "\uE040";
    private static final String ICON_SHUFFLE = "\uE043";
    private static final String ICON_VOLUME = "\uE050";
    private static final String ICON_FOLDER = "\uE2C7";
    private static final String ICON_CHECK = "\uE5CA";
    private static final String ICON_HEART = "\uE87D";
    private static final String ICON_PERSON = "\uE7FD";
    private static final String ICON_LAB = "\uEA4B";
    private static final String ICON_RULES = "\uE894";

    private static final int WHITE = 0xFFF2F2F2;
    private static final int MUTED = 0xFFB5B5B5;
    private static final int DIM = 0xFF777777;
    private static final int LINE = 0x1CFFFFFF;
    private static final int LINE_HOVER = 0x3AFFFFFF;
    private static final int LINE_ACTIVE = 0x99FFFFFF;
    private static final int OUTER_GLASS = 0x18000000;
    private static final int SIDEBAR_GLASS = 0x94030303;
    private static final int CONTENT_GLASS = 0x24FFFFFF;
    private static final int BOTTOM_GLASS = 0x78101010;
    private static final int ROW_BASE = 0x24141414;
    private static final int ROW_HOVER = 0x36FFFFFF;
    private static final int INPUT = 0x26FFFFFF;
    private static final float MATERIAL_ICON_OPTICAL_Y = 2.5f;

    private final MusicService service;
    private final MizuluneMusic module;
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private final Map<String, Path> coverFiles = new ConcurrentHashMap<>();
    private final Map<String, Boolean> coverRequested = new ConcurrentHashMap<>();
    private final Map<String, Texture> coverTextures = new ConcurrentHashMap<>();
    private final Map<String, List<LyricLine>> lyrics = new ConcurrentHashMap<>();
    private final Map<String, Boolean> lyricRequested = new ConcurrentHashMap<>();
    private final Map<String, SmoothAnimationTimer> hoverTimers = new ConcurrentHashMap<>();
    private final SmoothAnimationTimer openTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer pageTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer navSelectionTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer searchScrollTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer queueScrollTimer = new SmoothAnimationTimer();
    private final AtomicLong searchRequestSequence = new AtomicLong();

    private Page page = Page.SEARCH;
    private String searchText = "";
    private String selectedSource;
    private MusicService.SearchType searchType = MusicService.SearchType.ALL;
    private volatile List<MusicTrack> searchResults = List.of();
    private volatile boolean searching;
    private boolean searchFocused;
    private boolean searchDirty;
    private boolean searchSelectAll;
    private boolean agreementChecked;
    private long lastSearchEditMs;
    private float searchScrollTarget;
    private float queueScrollTarget;
    private float maxSearchScroll;
    private float maxQueueScroll;
    private float lyricScroll;
    private DragTarget dragTarget = DragTarget.NONE;
    private Rectangle lastProgressRect;
    private Rectangle lastVolumeRect;
    private long pendingSeekMs = -1L;
    private float pendingVolume = -1.0f;
    private float alpha = 1.0f;

    public MusicScreen(MusicService service, MizuluneMusic module) {
        super(Component.literal("Mizulune Music"));
        this.service = service;
        this.module = module;
        this.selectedSource = service.config().getDefaultSource();
        this.openTimer.setCurrentValue(0.0);
        this.openTimer.setToValue(0.0);
        this.pageTimer.setCurrentValue(1.0);
        this.pageTimer.setToValue(1.0);
        double initialNavOffset = this.page.ordinal() * 42.0;
        this.navSelectionTimer.setCurrentValue(initialNavOffset);
        this.navSelectionTimer.setFromValue(initialNavOffset);
        this.navSelectionTimer.setToValue(initialNavOffset);
    }

    @Override
    public void tick() {
        if (this.searchDirty && this.searchFocused
                && System.currentTimeMillis() - this.lastSearchEditMs >= this.service.config().getSearchDebounceMs()) {
            this.startSearch();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.clickAreas.clear();
        this.openTimer.animate(1.0, 0.28, Easings.BACK_OUT);
        this.openTimer.tick();
        float open = clamp(this.openTimer.getValueF(), 0.0f, 1.0f);
        this.alpha = open;
        Renderer.render(guiGraphics, context -> {
            context.save();
            float scale = 0.92f + 0.08f * open;
            context.translate(0.0f, (1.0f - open) * 14.0f);
            context.translate(this.width * 0.5f, this.height * 0.5f);
            context.scale(scale, scale);
            context.translate(-this.width * 0.5f, -this.height * 0.5f);
            if (!this.service.config().isDisclaimerAccepted()) {
                this.renderAgreement(context, mouseX, mouseY);
            } else {
                this.renderMain(context, mouseX, mouseY);
            }
            context.restore();
        });
        this.alpha = 1.0f;
    }

    private void renderAgreement(DrawContext context, int mouseX, int mouseY) {
        float panelW = Math.min(this.width - 28.0f, 560.0f);
        float panelH = Math.min(this.height - 28.0f, 430.0f);
        float x = (this.width - panelW) * 0.5f;
        float y = (this.height - panelH) * 0.5f;
        this.drawGlass(context, x, y, panelW, panelH, 13.0f, OUTER_GLASS, true);
        if (LOGO != null) {
            context.drawTexture(new Texture(LOGO, 200, 200), Rectangle.ofXYWH(0, 0, 200, 200),
                    Rectangle.ofXYWH(x + panelW * 0.5f - 22.0f, y + 18.0f, 44.0f, 44.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune Music Usage Notice", x + panelW * 0.5f, y + 76.0f, TITLE, WHITE, Align.CENTER);
        this.text(context, "Please read before continuing", x + panelW * 0.5f, y + 104.0f, BODY, MUTED, Align.CENTER);

        float boxX = x + 26.0f;
        float boxY = y + 132.0f;
        float boxW = panelW - 52.0f;
        float boxH = panelH - 236.0f;
        this.rounded(context, boxX, boxY, boxW, boxH, 8.0f, 0x42141414);
        float itemX = boxX + 34.0f;
        float itemY = boxY + 20.0f;
        this.noticeLine(context, itemX, itemY, ICON_LAB, "Experimental Platform",
                "Features may change, be incomplete, or behave unexpectedly.");
        this.noticeLine(context, itemX, itemY + 39.0f, ICON_PLAYER, "Acknowledgement of GD\u97f3\u4e50\u53f0",
                "Access to GD\u97f3\u4e50\u53f0 (music.gdstudio.xyz) for search and playback.");
        this.noticeLine(context, itemX, itemY + 78.0f, ICON_INFO, "Not a Distributor",
                "The client does not upload, host, or distribute music content.");
        this.noticeLine(context, itemX, itemY + 117.0f, ICON_PERSON, "Personal Learning and Testing Only",
                "Do not use it for commercial purposes or redistribution.");
        this.noticeLine(context, itemX, itemY + 156.0f, ICON_RULES, "Comply with Third-Party Rules",
                "Comply with GD\u97f3\u4e50\u53f0 and third-party source rules.");

        float checkY = y + panelH - 94.0f;
        boolean checkHover = contains(mouseX, mouseY, x + 26.0f, checkY, panelW - 52.0f, 30.0f);
        float checkAnim = this.hover("agree", checkHover);
        this.rounded(context, x + 26.0f, checkY, panelW - 52.0f, 30.0f, 7.0f,
                Argb.interpolate(0x32161616, 0x55262626, checkAnim));
        this.stroke(context, x + 26.0f, checkY, panelW - 52.0f, 30.0f, 7.0f,
                Argb.interpolate(LINE, LINE_HOVER, checkAnim), 1.0f);
        this.rounded(context, x + 40.0f, checkY + 7.0f, 16.0f, 16.0f, 4.0f,
                this.agreementChecked ? 0xFFEAEAEA : 0x101A1A1A);
        this.stroke(context, x + 40.0f, checkY + 7.0f, 16.0f, 16.0f, 4.0f, LINE_ACTIVE, 1.0f);
        if (this.agreementChecked) {
            this.iconCenterY(context, ICON_CHECK, x + 48.0f, checkY + 15.0f, ICON_SMALL, 0xFF101010, Align.CENTER);
        }
        this.textCenterY(context, "I have read and agree to the usage notice", x + 66.0f,
                checkY + 15.0f, SMALL, WHITE, Align.LEFT);
        this.clickAreas.add(new ClickArea(x + 26.0f, checkY, panelW - 52.0f, 30.0f,
                () -> this.agreementChecked = !this.agreementChecked));

        this.pillButton(context, "agree-exit", x + 26.0f, y + panelH - 50.0f,
                panelW * 0.5f - 34.0f, 30.0f, "Exit", mouseX, mouseY, false, this::onClose);
        this.pillButton(context, "agree-continue", x + panelW * 0.5f + 8.0f, y + panelH - 50.0f,
                panelW * 0.5f - 34.0f, 30.0f, "Continue", mouseX, mouseY, !this.agreementChecked, () -> {
                    this.service.acceptDisclaimer();
                    ConfigManager.requestSaveIfReady();
                });
    }

    private void noticeLine(DrawContext context, float x, float y, String icon, String title, String body) {
        this.iconCenterY(context, icon, x, y + 8.0f, ICON, MUTED, Align.CENTER);
        this.textCenterY(context, title, x + 28.0f, y + 6.5f, BODY, WHITE, Align.LEFT);
        this.textCenterY(context, ellipsize(body, 76), x + 28.0f, y + 24.0f, SMALL, MUTED, Align.LEFT);
    }

    private void renderMain(DrawContext context, int mouseX, int mouseY) {
        float outerW = Math.min(this.width - 24.0f, clamp(this.width * 0.72f, 620.0f, 820.0f));
        float outerH = Math.min(this.height - 24.0f, clamp(this.height * 0.68f, 360.0f, 455.0f));
        outerW = Math.max(340.0f, outerW);
        outerH = Math.max(260.0f, outerH);
        float x = (this.width - outerW) * 0.5f;
        float y = (this.height - outerH) * 0.5f;
        float sidebarW = Math.min(142.0f, Math.max(118.0f, outerW * 0.23f));
        float bottomH = 54.0f;
        float contentX = x + sidebarW;
        float contentW = outerW - sidebarW;
        float contentH = outerH - bottomH;

        this.drawChrome(context, x, y, outerW, outerH, sidebarW, bottomH);
        this.renderSidebar(context, x, y, sidebarW, contentH, mouseX, mouseY);

        this.pageTimer.animate(1.0, 0.2, Easings.EASE_OUT_POW2);
        this.pageTimer.tick();
        float pageProgress = clamp(this.pageTimer.getValueF(), 0.0f, 1.0f);
        context.save();
        context.clip(Rectangle.ofXYWH(contentX, y, contentW, contentH));
        context.translate((1.0f - pageProgress) * 14.0f, (1.0f - pageProgress) * 2.0f);
        switch (this.page) {
            case PLAYER -> this.renderPlayer(context, contentX, y, contentW, contentH, mouseX, mouseY);
            case SEARCH -> this.renderSearch(context, contentX, y, contentW, contentH, mouseX, mouseY);
            case QUEUE -> this.renderQueue(context, contentX, y, contentW, contentH, mouseX, mouseY);
            case ABOUT -> this.renderAbout(context, contentX, y, contentW, contentH);
        }
        context.restore();
        this.renderBottomBar(context, x, y + contentH, outerW, bottomH, mouseX, mouseY);
    }

    private void drawChrome(DrawContext context, float x, float y, float outerW, float outerH, float sidebarW, float bottomH) {
        float radius = 13.0f;
        this.drawGlass(context, x, y, outerW, outerH, radius, OUTER_GLASS, true);
        context.save();
        context.clipRoundedRect(RoundedRectangle.ofXYWHR(x, y, outerW, outerH, radius), true);
        if (!this.module.useLiquidGlass()) {
            this.rect(context, x, y, sidebarW, outerH - bottomH, SIDEBAR_GLASS);
            this.rect(context, x + sidebarW, y, outerW - sidebarW, outerH - bottomH, CONTENT_GLASS);
        }
        this.rect(context, x, y + outerH - bottomH, outerW, bottomH, BOTTOM_GLASS);
        context.restore();
        this.line(context, x + sidebarW, y + 10.0f,
                x + sidebarW, y + outerH - bottomH - 8.0f, LINE);
        this.line(context, x + 8.0f, y + outerH - bottomH, x + outerW - 8.0f, y + outerH - bottomH, LINE);
    }

    private void renderSidebar(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        if (LOGO != null) {
            context.drawTexture(new Texture(LOGO, 200, 200), Rectangle.ofXYWH(0, 0, 200, 200),
                    Rectangle.ofXYWH(x + 23.0f, y + 24.0f, 38.0f, 38.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune", x + 70.0f, y + 27.0f, BODY, WHITE, Align.LEFT);
        this.text(context, "Music", x + 70.0f, y + 48.0f, SMALL, MUTED, Align.LEFT);

        float navX = x + 12.0f;
        float navY = y + 104.0f;
        float navW = width - 24.0f;
        this.navSelectionTimer.tick();
        float selectionY = navY + this.navSelectionTimer.getValueF();
        this.rounded(context, navX, selectionY, navW, 30.0f, 7.0f, 0x56303030);
        this.rounded(context, navX + 2.0f, selectionY + 7.0f, 2.0f, 16.0f, 1.0f, 0xFFFFFFFF);
        this.navItem(context, navX, navY, navW, Page.PLAYER, ICON_PLAYER, "Player", mouseX, mouseY);
        this.navItem(context, navX, navY + 42.0f, navW, Page.SEARCH, ICON_SEARCH, "Search", mouseX, mouseY);
        this.navItem(context, navX, navY + 84.0f, navW, Page.QUEUE, ICON_QUEUE, "Queue", mouseX, mouseY);
        this.navItem(context, navX, navY + 126.0f, navW, Page.ABOUT, ICON_INFO, "About", mouseX, mouseY);
    }

    private void navItem(DrawContext context, float x, float y, float width, Page target, String icon, String title,
                         int mouseX, int mouseY) {
        boolean active = this.page == target;
        boolean hovered = contains(mouseX, mouseY, x, y, width, 30.0f);
        float hover = this.hover("nav:" + target.name(), hovered);
        if (hover > 0.001f) {
            this.rounded(context, x, y, width, 30.0f, 7.0f,
                    Argb.interpolate(0x00181818, 0x282D2D2D, hover));
        }
        float shift = hover * 2.5f;
        this.iconCenterY(context, icon, x + 23.0f + shift, y + 15.0f, ICON, active ? WHITE : MUTED, Align.CENTER);
        this.textCenterY(context, title, x + 45.0f + shift, y + 15.0f, BODY, active ? WHITE : MUTED, Align.LEFT);
        this.clickAreas.add(new ClickArea(x, y, width, 30.0f, () -> this.setPage(target)));
    }

    private void renderSearch(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        float pad = 22.0f;
        this.text(context, "Music Player", x + pad, y + 22.0f, H1, WHITE, Align.LEFT);
        float inputX = x + pad;
        float inputY = y + 56.0f;
        float inputW = width - pad * 2.0f;
        float buttonW = 70.0f;
        this.rounded(context, inputX, inputY, inputW, 28.0f, 7.0f, INPUT);
        this.stroke(context, inputX, inputY, inputW, 28.0f, 7.0f, this.searchFocused ? LINE_ACTIVE : LINE, 1.0f);
        this.iconCenterY(context, ICON_SEARCH, inputX + 17.0f, inputY + 14.0f, ICON, MUTED, Align.CENTER);
        String value = this.searchText.isEmpty() && !this.searchFocused ? "Search music" : this.searchText;
        if (this.searchFocused && this.searchSelectAll && !this.searchText.isEmpty()) {
            float selectedW = Math.min(inputW - buttonW - 50.0f, this.measure(this.searchText, BODY) + 6.0f);
            this.rounded(context, inputX + 34.0f, inputY + 6.0f, selectedW, 16.0f, 4.0f, 0x38FFFFFF);
        }
        this.textCenterY(context, ellipsize(value, 48), inputX + 36.0f, inputY + 14.0f, BODY,
                this.searchText.isEmpty() && !this.searchFocused ? DIM : WHITE, Align.LEFT);
        if (this.searchFocused && !this.searchSelectAll) {
            float cursorX = Math.min(inputX + inputW - buttonW - 10.0f, inputX + 36.0f + this.measure(value, BODY) + 3.0f);
            this.line(context, cursorX, inputY + 7.0f, cursorX, inputY + 21.0f, 0xCCFFFFFF);
        }
        this.clickAreas.add(new ClickArea(inputX, inputY, inputW - buttonW, 28.0f, () -> {
            this.searchFocused = true;
            this.searchSelectAll = false;
        }));
        this.pillButton(context, "search:submit", inputX + inputW - buttonW + 3.0f, inputY + 3.0f,
                buttonW - 6.0f, 22.0f, this.searching ? "..." : "Search", mouseX, mouseY, false, this::startSearch);

        float chipX = inputX;
        float chipY = inputY + 38.0f;
        for (MusicService.SearchType type : searchChips()) {
            float chipW = Math.max(50.0f, this.measure(type.displayName(), SMALL) + 18.0f);
            boolean disabled = !type.isSupported();
            this.drawChip(context, "type:" + type.name(), chipX, chipY, chipW, 22.0f, type.displayName(),
                    this.searchType == type, disabled, mouseX, mouseY, () -> {
                        if (type.isSupported()) {
                            this.searchType = type;
                            this.startSearch();
                        }
                    });
            chipX += chipW + 6.0f;
        }

        String sourceLabel = this.selectedSource == null ? "source" : this.selectedSource;
        float sourceW = Math.max(54.0f, this.measure(sourceLabel, SMALL) + 16.0f);
        this.drawChip(context, "src:cycle", inputX + inputW - sourceW, chipY, sourceW, 22.0f, sourceLabel,
                true, false, mouseX, mouseY, this::cycleSource);

        float listX = inputX;
        float listY = chipY + 34.0f;
        float rowH = 46.0f;
        float listW = inputW;
        float listH = height - (listY - y) - 12.0f;
        this.maxSearchScroll = Math.max(0.0f, this.searchResults.size() * (rowH + 4.0f) - 4.0f - listH);
        this.searchScrollTarget = clamp(this.searchScrollTarget, 0.0f, this.maxSearchScroll);
        this.searchScrollTimer.animate(this.searchScrollTarget, 0.22, Easings.EASE_OUT_POW2);
        this.searchScrollTimer.tick();
        float scroll = this.searchScrollTimer.getValueF();

        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, listW, listH));
        if (this.searchResults.isEmpty()) {
            String message = this.searching ? "Searching GD Music API..." :
                    this.service.lastMessage().isBlank() ? "Type a keyword and search." : this.service.lastMessage();
            this.textCenterY(context, message, listX + 4.0f, listY + 20.0f, BODY, MUTED, Align.LEFT);
        } else {
            float yy = listY - scroll;
            for (int i = 0; i < this.searchResults.size(); i++) {
                this.renderTrackRow(context, this.searchResults.get(i), i, -1, listX, yy, listW, rowH, mouseX, mouseY, true);
                yy += rowH + 4.0f;
            }
        }
        context.restore();
    }

    private void renderPlayer(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        float pad = 22.0f;
        this.text(context, "Now Playing", x + pad, y + 22.0f, H1, WHITE, Align.LEFT);
        float coverSize = Math.min(136.0f, Math.max(108.0f, width * 0.25f));
        float coverX = x + pad;
        float coverY = y + 62.0f;
        this.drawCover(context, track, coverX, coverY, coverSize);
        if (track == null) {
            this.text(context, "No track playing", coverX, coverY + coverSize + 22.0f, H1, WHITE, Align.LEFT);
            this.text(context, "Search a track or pick one from Queue.", coverX, coverY + coverSize + 45.0f, SMALL, MUTED, Align.LEFT);
        } else {
            this.text(context, ellipsize(track.getName(), 24), coverX, coverY + coverSize + 20.0f, H1, WHITE, Align.LEFT);
            this.text(context, ellipsize(track.artistsText(), 30), coverX, coverY + coverSize + 43.0f, BODY, MUTED, Align.LEFT);
            this.text(context, "Source: " + MusicService.normalizeSource(track.getSource()), coverX, coverY + coverSize + 68.0f, SMALL, DIM, Align.LEFT);
            this.iconCenterY(context, ICON_FOLDER, coverX + 8.0f, coverY + coverSize + 92.0f, ICON_SMALL, DIM, Align.CENTER);
            this.textCenterY(context, state.isCached() ? "Cached in .mizulune/music" : "Temporary cache",
                    coverX + 22.0f, coverY + coverSize + 92.0f, SMALL, DIM, Align.LEFT);
        }

        float lyricsX = coverX + coverSize + 24.0f;
        float lyricsY = coverY;
        float lyricsW = width - (lyricsX - x) - pad;
        float lyricsH = height - 86.0f;
        this.rounded(context, lyricsX, lyricsY, lyricsW, lyricsH, 8.0f, 0x30151515);
        this.renderLyrics(context, track, state, lyricsX + 16.0f, lyricsY + 14.0f, lyricsW - 32.0f, lyricsH - 28.0f);
    }

    private void renderLyrics(DrawContext context, MusicTrack track, MusicPlaybackState state,
                              float x, float y, float width, float height) {
        if (track == null) {
            this.text(context, "Lyrics will appear here when a track is playing.", x, y, BODY, MUTED, Align.LEFT);
            return;
        }
        List<LyricLine> lines = this.lyricsFor(track);
        if (lines.isEmpty()) {
            this.text(context, "No lyrics available.", x, y, BODY, MUTED, Align.LEFT);
            return;
        }
        int current = shit.zen.music.lyrics.LyricParser.currentLineIndex(lines, state.getPositionMs());
        float lineH = 25.0f;
        float targetScroll = Math.max(0.0f, current * lineH - height * 0.42f);
        this.lyricScroll += (targetScroll - this.lyricScroll) * 0.18f;
        context.save();
        context.clip(Rectangle.ofXYWH(x, y, width, height));
        float yy = y - this.lyricScroll;
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (yy > y - lineH && yy < y + height + lineH) {
                boolean active = i == current;
                String text = timestamp(line.timeMs()) + "   " + line.text();
                this.textCenterY(context, ellipsize(text, 64), x, yy + lineH * 0.5f,
                        active ? BODY : SMALL, active ? WHITE : MUTED, Align.LEFT);
            }
            yy += lineH;
        }
        context.restore();
    }

    private void renderQueue(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        float pad = 22.0f;
        this.text(context, "Playback Queue", x + pad, y + 22.0f, H1, WHITE, Align.LEFT);
        this.text(context, "Current list", x + pad, y + 46.0f, BODY, MUTED, Align.LEFT);
        this.pillButton(context, "queue:clear", x + width - 172.0f, y + 24.0f, 70.0f, 24.0f,
                "Clear", mouseX, mouseY, false, this.service::clearQueue);
        this.pillButton(context, "queue:mode", x + width - 94.0f, y + 24.0f, 72.0f, 24.0f,
                modeLabel(this.service.playbackState().getPlayMode()), mouseX, mouseY, false, this.service::cyclePlayMode);

        List<MusicTrack> queue = this.service.queueSnapshot();
        float listX = x + pad;
        float listY = y + 74.0f;
        float listW = width - pad * 2.0f;
        float listH = height - 100.0f;
        float rowH = 46.0f;
        this.maxQueueScroll = Math.max(0.0f, queue.size() * (rowH + 6.0f) - 6.0f - listH);
        this.queueScrollTarget = clamp(this.queueScrollTarget, 0.0f, this.maxQueueScroll);
        this.queueScrollTimer.animate(this.queueScrollTarget, 0.22, Easings.EASE_OUT_POW2);
        this.queueScrollTimer.tick();
        float scroll = this.queueScrollTimer.getValueF();

        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, listW, listH));
        if (queue.isEmpty()) {
            this.textCenterY(context, "Queue is empty.", listX + 4.0f, listY + 20.0f, BODY, MUTED, Align.LEFT);
        } else {
            float yy = listY - scroll;
            for (int i = 0; i < queue.size(); i++) {
                this.renderTrackRow(context, queue.get(i), i, i, listX, yy, listW, rowH, mouseX, mouseY, false);
                yy += rowH + 6.0f;
            }
        }
        context.restore();
        this.text(context, queue.size() + " tracks", x + width * 0.5f, y + height - 22.0f, SMALL, DIM, Align.CENTER);
    }

    private void renderAbout(DrawContext context, float x, float y, float width, float height) {
        float pad = 22.0f;
        this.text(context, "About Mizulune Music", x + pad, y + 24.0f, H1, WHITE, Align.LEFT);
        float cardY = y + 66.0f;
        float half = (width - pad * 2.0f - 14.0f) * 0.5f;
        this.aboutBlock(context, x + pad, cardY, half, 112.0f, ICON_HEART, "Special Thanks",
                "GD-Studio\nBilibili creator / GD\u97f3\u4e50\u53f0\nmusic.gdstudio.xyz");
        this.aboutBlock(context, x + pad + half + 14.0f, cardY, half, 112.0f, ICON_LAB, "Project",
                "Experimental music player module.\nSearch, cache, queue, and play\ninside Minecraft.");
        this.aboutBlock(context, x + pad, cardY + 132.0f, width - pad * 2.0f, 70.0f, ICON_PERSON, "Author",
                "GitHub: Castorice1337\nMizulune client experimental platform.");
        this.aboutBlock(context, x + pad, cardY + 218.0f, width - pad * 2.0f, 70.0f, ICON_INFO, "Notice",
                "For personal learning and testing only.\nThe client is not a music distributor.");
    }

    private void aboutBlock(DrawContext context, float x, float y, float width, float height, String icon, String title, String body) {
        this.rounded(context, x, y, width, height, 8.0f, 0x30151515);
        this.iconCenterY(context, icon, x + 18.0f, y + 22.0f, ICON, MUTED, Align.CENTER);
        this.textCenterY(context, title, x + 38.0f, y + 22.0f, BODY, WHITE, Align.LEFT);
        String[] lines = body.split("\\n");
        float yy = y + 50.0f;
        for (String line : lines) {
            this.text(context, ellipsize(line, width > 360.0f ? 82 : 38), x + 18.0f, yy, SMALL, MUTED, Align.LEFT);
            yy += 17.0f;
        }
    }

    private void renderTrackRow(DrawContext context, MusicTrack track, int visualIndex, int queueIndex, float x, float y, float width,
                                float height, int mouseX, int mouseY, boolean searchResult) {
        if (y + height < 0.0f || y > this.height) {
            return;
        }
        MusicTrack playing = this.service.playbackState().getCurrentTrack();
        boolean current = !searchResult && playing != null && playing.stableKey().equals(track.stableKey());
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        float hover = this.hover((searchResult ? "search:" : "queue:") + visualIndex + ":" + track.stableKey(), hovered || current);
        this.rounded(context, x, y, width, height, 8.0f, Argb.interpolate(ROW_BASE, ROW_HOVER, hover));
        if (current) {
            this.rounded(context, x + 2.0f, y + 8.0f, 2.0f, height - 16.0f, 1.0f, 0xCCFFFFFF);
        }
        if (hovered && !current) {
            this.stroke(context, x, y, width, height, 8.0f, LINE_HOVER, 1.0f);
        }

        float cover = 34.0f;
        float contentShift = hover * 2.0f;
        float coverX = (searchResult ? x + 8.0f : x + 42.0f) + contentShift;
        if (!searchResult) {
            if (current) {
                this.iconCenterY(context, ICON_VOLUME, x + 22.0f, y + height * 0.5f, ICON_SMALL, WHITE, Align.CENTER);
            } else {
                this.textCenterY(context, String.format(Locale.ROOT, "%02d", visualIndex + 1), x + 22.0f,
                        y + height * 0.5f, SMALL, DIM, Align.CENTER);
            }
        }
        this.drawCover(context, track, coverX, y + (height - cover) * 0.5f, cover);
        float textX = coverX + cover + 12.0f;
        float actionW = searchResult ? 98.0f : 74.0f;
        float textW = width - (textX - x) - actionW - 18.0f;
        float rowCenter = y + height * 0.5f;
        this.textCenterY(context, ellipsize(track.getName(), Math.max(12, (int)(textW / 7.0f))),
                textX, rowCenter - 8.0f, BODY, WHITE, Align.LEFT);
        this.textCenterY(context, ellipsize(track.artistsText(), Math.max(14, (int)(textW / 6.0f))),
                textX, rowCenter + 9.0f, SMALL, MUTED, Align.LEFT);
        if (!searchResult) {
            this.textCenterY(context, timestamp(track.getDurationMs()), x + width - 100.0f, y + height * 0.5f, SMALL, DIM, Align.RIGHT);
        }

        float bx = x + width - (searchResult ? 96.0f : 60.0f);
        this.iconButton(context, "row-play:" + visualIndex + ":" + track.stableKey(), bx, y + 10.0f, 26.0f,
                ICON_PLAY, mouseX, mouseY, () -> {
                    if (searchResult) {
                        this.service.playTrack(track);
                    } else {
                        this.service.playQueueIndex(queueIndex);
                    }
                });
        if (searchResult) {
            this.iconButton(context, "row-cache:" + visualIndex + ":" + track.stableKey(), bx + 32.0f, y + 10.0f, 26.0f,
                    ICON_DOWNLOAD, mouseX, mouseY, () -> this.service.cacheAndPlayTrack(track));
            this.iconButton(context, "row-add:" + visualIndex + ":" + track.stableKey(), bx + 64.0f, y + 10.0f, 26.0f,
                    ICON_ADD, mouseX, mouseY, () -> this.service.addToQueue(track));
        } else {
            this.iconButton(context, "row-remove:" + visualIndex + ":" + track.stableKey(), bx + 32.0f, y + 10.0f, 26.0f,
                    ICON_CLOSE, mouseX, mouseY, () -> this.service.removeQueueIndex(queueIndex));
        }
    }

    private void renderBottomBar(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        float infoX = x + 14.0f;
        float infoW = width < 700.0f ? 150.0f : 190.0f;
        float cover = 36.0f;
        this.drawCover(context, track, infoX, y + 9.0f, cover);
        float infoCenter = y + height * 0.5f;
        this.textCenterY(context, track == null ? "No track" : ellipsize(track.getName(), width < 700.0f ? 14 : 20),
                infoX + 46.0f, infoCenter - 8.0f, BODY, WHITE, Align.LEFT);
        this.textCenterY(context, track == null ? "Mizulune Music" : ellipsize(track.artistsText(), width < 700.0f ? 16 : 24),
                infoX + 46.0f, infoCenter + 9.0f, SMALL, MUTED, Align.LEFT);

        boolean compact = width < 740.0f;
        float rightW = compact ? 70.0f : 126.0f;
        float controlsW = 96.0f;
        float rightX = x + width - rightW - 14.0f;
        float controlsX = rightX - controlsW - 16.0f;
        float progressX = infoX + infoW + 14.0f;
        float progressW = Math.max(64.0f, controlsX - progressX - 16.0f);
        float sliderY = y + 33.0f;
        long displayPosition = this.dragTarget == DragTarget.PROGRESS && this.pendingSeekMs >= 0L
                ? this.pendingSeekMs
                : state.getPositionMs();
        this.textCenterY(context, timestamp(displayPosition), progressX - 38.0f, sliderY + 2.0f, SMALL, MUTED, Align.LEFT);
        this.drawSlider(context, progressX, sliderY, progressW,
                state.getDurationMs() <= 0L ? 0.0f : (float) displayPosition / state.getDurationMs());
        this.textCenterY(context, timestamp(state.getDurationMs()), progressX + progressW + 10.0f, sliderY + 2.0f, SMALL, MUTED, Align.LEFT);
        this.lastProgressRect = Rectangle.ofXYWH(progressX, sliderY - 8.0f, progressW, 18.0f);
        this.clickAreas.add(new ClickArea(progressX, sliderY - 8.0f, progressW, 18.0f, () -> this.dragTarget = DragTarget.PROGRESS));

        this.iconButton(context, "bottom-prev", controlsX, y + 14.0f, 26.0f, ICON_PREVIOUS, mouseX, mouseY, this.service::previous);
        this.iconButton(context, "bottom-play", controlsX + 35.0f, y + 10.0f, 34.0f,
                state.isPlaying() ? ICON_PAUSE : ICON_PLAY, mouseX, mouseY, this.service::togglePlay);
        this.iconButton(context, "bottom-next", controlsX + 78.0f, y + 14.0f, 26.0f, ICON_NEXT, mouseX, mouseY, this.service::next);

        this.iconButton(context, "bottom-mode", rightX, y + 14.0f, 26.0f,
                modeIcon(state.getPlayMode()), mouseX, mouseY, this.service::cyclePlayMode);
        if (!compact) {
            float volumeValue = this.dragTarget == DragTarget.VOLUME && this.pendingVolume >= 0.0f
                    ? this.pendingVolume
                    : state.getVolume();
            this.iconCenterY(context, ICON_VOLUME, rightX + 36.0f, y + 27.0f, ICON_SMALL, MUTED, Align.CENTER);
            this.drawSlider(context, rightX + 50.0f, sliderY, 66.0f, volumeValue);
            this.lastVolumeRect = Rectangle.ofXYWH(rightX + 50.0f, sliderY - 8.0f, 66.0f, 18.0f);
            this.clickAreas.add(new ClickArea(rightX + 50.0f, sliderY - 8.0f, 66.0f, 18.0f,
                    () -> this.dragTarget = DragTarget.VOLUME));
        } else {
            this.iconButton(context, "bottom-volume", rightX + 34.0f, y + 14.0f, 26.0f,
                    ICON_VOLUME, mouseX, mouseY, () -> {
                    });
            this.lastVolumeRect = null;
        }

        if (!state.getError().isBlank()) {
            this.text(context, ellipsize(state.getError(), 42), x + width * 0.5f, y + height - 12.0f, SMALL, MUTED, Align.CENTER);
        } else if (state.isLoading()) {
            this.text(context, "Loading...", x + width * 0.5f, y + height - 12.0f, SMALL, MUTED, Align.CENTER);
        }
    }

    private void drawCover(DrawContext context, MusicTrack track, float x, float y, float size) {
        this.rounded(context, x, y, size, size, Math.min(7.0f, size * 0.18f), 0x66202020);
        if (track != null) {
            Path file = this.coverFor(track);
            if (file != null) {
                context.save();
                context.clipRoundedRect(RoundedRectangle.ofXYWHR(x, y, size, size, Math.min(7.0f, size * 0.18f)), true);
                Texture texture = this.coverTextures.computeIfAbsent(file.toAbsolutePath().normalize().toString(),
                        ignored -> new Texture(file, 300, 300));
                context.drawTexture(texture, Rectangle.ofXYWH(0, 0, 300, 300),
                        Rectangle.ofXYWH(x, y, size, size), paint(0xFFFFFFFF));
                context.restore();
                return;
            }
        }
        this.iconCenterY(context, ICON_PLAYER, x + size * 0.5f, y + size * 0.5f,
                size > 42.0f ? ICON_LARGE : ICON, MUTED, Align.CENTER);
    }

    private Path coverFor(MusicTrack track) {
        String key = track.stableKey();
        Path file = this.coverFiles.get(key);
        if (file != null) {
            return file;
        }
        if (this.coverRequested.putIfAbsent(key, Boolean.TRUE) == null) {
            this.service.cover(track).whenComplete((path, throwable) -> {
                if (throwable != null || path == null) {
                    this.coverRequested.remove(key);
                    return;
                }
                this.coverFiles.put(key, path);
            });
        }
        return null;
    }

    private List<LyricLine> lyricsFor(MusicTrack track) {
        String key = track.stableKey();
        List<LyricLine> lines = this.lyrics.get(key);
        if (lines != null) {
            return lines;
        }
        if (this.lyricRequested.putIfAbsent(key, Boolean.TRUE) == null) {
            this.service.lyrics(track).whenComplete((value, throwable) -> {
                if (throwable != null) {
                    this.lyricRequested.remove(key);
                    return;
                }
                this.lyrics.put(key, value == null ? List.of() : value);
            });
        }
        return List.of();
    }

    private void startSearch() {
        this.searchDirty = false;
        String query = this.searchText.trim();
        long requestId = this.searchRequestSequence.incrementAndGet();
        if (query.isEmpty()) {
            this.searchResults = List.of();
            this.searching = false;
            this.searchScrollTarget = 0.0f;
            return;
        }
        this.searching = true;
        CompletableFuture<List<MusicTrack>> future = this.service.search(this.selectedSource, this.searchType, query);
        future.whenComplete((results, throwable) -> {
            if (this.searchRequestSequence.get() != requestId) {
                return;
            }
            this.searchResults = results == null ? List.of() : results;
            this.searching = false;
            this.searchScrollTarget = 0.0f;
        });
    }

    private void setPage(Page target) {
        if (this.page == target) {
            return;
        }
        this.page = target;
        this.navSelectionTimer.animate(target.ordinal() * 42.0, 0.24, Easings.EASE_OUT_POW3);
        this.pageTimer.setCurrentValue(0.0);
        this.pageTimer.setFromValue(0.0);
        this.pageTimer.setToValue(0.0);
        this.searchFocused = false;
        this.searchSelectAll = false;
    }

    private void cycleSource() {
        List<String> sources = this.service.config().getEnabledSources();
        if (sources.isEmpty()) {
            return;
        }
        int index = sources.indexOf(this.selectedSource);
        this.selectedSource = sources.get((index + 1 + sources.size()) % sources.size());
        this.startSearch();
    }

    private void pillButton(DrawContext context, String key, float x, float y, float width, float height, String label,
                            int mouseX, int mouseY, boolean disabled, Runnable action) {
        boolean hovered = !disabled && contains(mouseX, mouseY, x, y, width, height);
        float hover = this.hover("btn:" + key, hovered);
        int fill = disabled ? 0x16161616 : Argb.interpolate(0x301B1B1B, 0x68FFFFFF, hover);
        int border = disabled ? 0x16FFFFFF : Argb.interpolate(LINE, LINE_HOVER, hover);
        this.rounded(context, x, y, width, height, 7.0f, fill);
        if (hover > 0.02f) {
            this.stroke(context, x, y, width, height, 7.0f, border, 1.0f);
        }
        this.textCenterY(context, label, x + width * 0.5f, y + height * 0.5f, SMALL, disabled ? DIM : WHITE, Align.CENTER);
        if (!disabled) {
            this.clickAreas.add(new ClickArea(x, y, width, height, action));
        }
    }

    private void drawChip(DrawContext context, String key, float x, float y, float width, float height, String label,
                          boolean active, boolean disabled, int mouseX, int mouseY, Runnable action) {
        boolean hovered = !disabled && contains(mouseX, mouseY, x, y, width, height);
        float hover = this.hover("chip:" + key, hovered || active);
        int fill = active ? 0x662C2C2C : Argb.interpolate(0x26171717, 0x4A282828, hover);
        int border = active ? LINE_ACTIVE : Argb.interpolate(0x12FFFFFF, LINE_HOVER, hover);
        this.rounded(context, x, y, width, height, height * 0.5f, fill);
        if (active || hovered) {
            this.stroke(context, x, y, width, height, height * 0.5f, border, 1.0f);
        }
        this.textCenterY(context, label, x + width * 0.5f, y + height * 0.5f, SMALL,
                disabled ? DIM : active ? WHITE : MUTED, Align.CENTER);
        if (!disabled) {
            this.clickAreas.add(new ClickArea(x, y, width, height, action));
        }
    }

    private void iconButton(DrawContext context, String key, float x, float y, float size, String icon,
                            int mouseX, int mouseY, Runnable action) {
        boolean hovered = contains(mouseX, mouseY, x, y, size, size);
        float hover = this.hover("icon:" + key, hovered);
        this.rounded(context, x, y, size, size, size * 0.5f, Argb.interpolate(0x00181818, 0x68FFFFFF, hover));
        float centerX = x + size * 0.5f;
        float centerY = y + size * 0.5f;
        float scale = 0.90f + hover * 0.10f;
        context.save();
        context.translate(centerX, centerY);
        context.scale(scale, scale);
        context.translate(-centerX, -centerY);
        this.iconCenterY(context, icon, centerX, centerY,
                size > 30.0f ? ICON_LARGE : ICON, hovered ? WHITE : MUTED, Align.CENTER);
        context.restore();
        this.clickAreas.add(new ClickArea(x, y, size, size, action));
    }

    private void drawSlider(DrawContext context, float x, float y, float width, float value) {
        float progress = clamp(value, 0.0f, 1.0f);
        this.rounded(context, x, y, width, 4.0f, 2.0f, 0x55353535);
        this.rounded(context, x, y, width * progress, 4.0f, 2.0f, 0xFFE5E5E5);
        this.rounded(context, x + width * progress - 5.0f, y - 3.0f, 10.0f, 10.0f, 5.0f, 0xFFFFFFFF);
    }

    private void drawGlass(DrawContext context, float x, float y, float width, float height, float radius, int tint, boolean strong) {
        RoundedRectangle rect = RoundedRectangle.ofXYWHR(x, y, width, height, radius);
        if (this.module.useLiquidGlass()) {
            LiquidGlassStyle style = LiquidGlassStyle.builder()
                    .blurRadius(20.0f)
                    .opacity(strong ? 0.84f : 0.68f)
                    .tint(Argb.scaleAlpha(tint, 1.0f), 0.30f)
                    .darkness(0.18f)
                    .chromaStrength(0.0f)
                    .build();
            context.drawLiquidGlassPanel(rect, style);
        } else {
            context.drawBackdropBlurredRoundedRect(rect, 22.0f, strong ? 0.92f : 0.72f, color(tint));
        }
        this.stroke(context, x, y, width, height, radius, LINE_HOVER, 1.0f);
    }

    private void rect(DrawContext context, float x, float y, float width, float height, int color) {
        context.drawRect(Rectangle.ofXYWH(x, y, width, height), paint(color));
    }

    private void rounded(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        context.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, height, radius), paint(color));
    }

    private void stroke(DrawContext context, float x, float y, float width, float height, float radius, int color, float stroke) {
        context.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, height, radius),
                paint(color).setStrokeCap(Paint.StrokeCap.STROKE).setStrokeWidth(stroke));
    }

    private void line(DrawContext context, float x1, float y1, float x2, float y2, int color) {
        context.drawLine(x1, y1, x2, y2, paint(color).setStrokeWidth(1.0f));
    }

    private void text(DrawContext context, String value, float x, float y, FontRenderer font, int color, Align align) {
        float capHeight = Math.max(1.0f, font.getMetrics().capHeight());
        this.drawAlignedText(context, value, x, centeredTextY(font, y + capHeight * 0.5f), font, color, align);
    }

    private void drawAlignedText(DrawContext context, String value, float x, float y,
                                 FontRenderer font, int color, Align align) {
        float drawX = switch (align) {
            case LEFT -> x;
            case CENTER -> x - this.measure(value, font) * 0.5f;
            case RIGHT -> x - this.measure(value, font);
        };
        context.drawString(value, drawX, y, font, paint(color));
    }

    private void textCenterY(DrawContext context, String value, float x, float centerY,
                             FontRenderer font, int color, Align align) {
        this.drawAlignedText(context, value, x, centeredTextY(font, centerY), font, color, align);
    }

    private void iconCenterY(DrawContext context, String icon, float x, float centerY,
                             FontRenderer font, int color, Align align) {
        this.drawAlignedText(context, icon, x,
                centeredTextY(font, centerY + MATERIAL_ICON_OPTICAL_Y), font, color, align);
    }

    private static float centeredTextY(FontRenderer font, float centerY) {
        GlyphMetrics metrics = font.getMetrics();
        float capHeight = Math.max(1.0f, metrics.capHeight());
        float logicalAscent = (metrics.getLineGap() - metrics.ascent() - metrics.descent()) * 0.5f;
        return centerY - capHeight * 0.5f - (logicalAscent - capHeight);
    }

    private float hover(String key, boolean hovered) {
        SmoothAnimationTimer timer = this.hoverTimers.computeIfAbsent(key, ignored -> new SmoothAnimationTimer());
        timer.animate(hovered ? 1.0 : 0.0, 0.16, Easings.EASE_OUT_POW2);
        timer.tick();
        return clamp(timer.getValueF(), 0.0f, 1.0f);
    }

    private float measure(String text, FontRenderer font) {
        return Renderer.getBackend() != null ? Renderer.getBackend().measureTextWidth(text, font) : font.getWidth(text);
    }

    private static Paint paint(int color) {
        return new Paint().setColor(color);
    }

    private int color(int color) {
        return Argb.scaleAlpha(color, this.alpha);
    }

    private static boolean contains(double mx, double my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static String ellipsize(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static String timestamp(long ms) {
        long safe = Math.max(0L, ms);
        long total = safe / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", total / 60L, total % 60L);
    }

    private static String modeLabel(PlayMode mode) {
        return switch (mode) {
            case ORDER -> "Order";
            case SHUFFLE -> "Shuffle";
            case SINGLE -> "Loop";
        };
    }

    private static String modeIcon(PlayMode mode) {
        return switch (mode) {
            case ORDER -> ICON_REPEAT;
            case SHUFFLE -> ICON_SHUFFLE;
            case SINGLE -> ICON_REPEAT;
        };
    }

    private static MusicService.SearchType[] searchChips() {
        return new MusicService.SearchType[] {
                MusicService.SearchType.ALL,
                MusicService.SearchType.SINGLE,
                MusicService.SearchType.PLAYLIST,
                MusicService.SearchType.ARTIST,
                MusicService.SearchType.ALBUM
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (int i = this.clickAreas.size() - 1; i >= 0; i--) {
            ClickArea area = this.clickAreas.get(i);
            if (!area.contains(mouseX, mouseY)) {
                continue;
            }
            area.action.run();
            if (this.dragTarget == DragTarget.PROGRESS) {
                this.updateProgress(mouseX);
            } else if (this.dragTarget == DragTarget.VOLUME) {
                this.updateVolume(mouseX);
            }
            return true;
        }
        this.searchFocused = false;
        this.searchSelectAll = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragTarget == DragTarget.PROGRESS) {
            this.updateProgress(mouseX);
            return true;
        }
        if (button == 0 && this.dragTarget == DragTarget.VOLUME) {
            this.updateVolume(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragTarget == DragTarget.PROGRESS && this.pendingSeekMs >= 0L) {
            this.service.seek(this.pendingSeekMs);
            this.pendingSeekMs = -1L;
        }
        if (button == 0 && this.dragTarget == DragTarget.VOLUME && this.pendingVolume >= 0.0f) {
            this.service.setVolume(this.pendingVolume, true);
            this.pendingVolume = -1.0f;
        }
        this.dragTarget = DragTarget.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (this.page == Page.SEARCH) {
            this.searchScrollTarget = clamp(this.searchScrollTarget - (float) scrollDelta * 26.0f, 0.0f, this.maxSearchScroll);
            return true;
        }
        if (this.page == Page.QUEUE) {
            this.queueScrollTarget = clamp(this.queueScrollTarget - (float) scrollDelta * 26.0f, 0.0f, this.maxQueueScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.searchFocused || codePoint < 32 || codePoint == 127) {
            return super.charTyped(codePoint, modifiers);
        }
        this.searchText = this.searchSelectAll ? String.valueOf(codePoint) : this.searchText + codePoint;
        this.searchSelectAll = false;
        this.searchDirty = true;
        this.lastSearchEditMs = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.startSearch();
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String paste = Minecraft.getInstance().keyboardHandler.getClipboard();
                this.searchText = this.searchSelectAll ? paste : this.searchText + paste;
                this.searchSelectAll = false;
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
                this.searchSelectAll = !this.searchText.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.searchText.isEmpty()) {
                if (this.searchSelectAll) {
                    this.searchText = "";
                    this.searchSelectAll = false;
                    this.searchDirty = true;
                    this.lastSearchEditMs = System.currentTimeMillis();
                    return true;
                }
                int end = this.searchText.offsetByCodePoints(this.searchText.length(), -1);
                this.searchText = this.searchText.substring(0, end);
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                this.searchText = "";
                this.searchSelectAll = false;
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchFocused = false;
                this.searchSelectAll = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateProgress(double mouseX) {
        if (this.lastProgressRect == null) {
            return;
        }
        MusicPlaybackState state = this.service.playbackState();
        if (state.getDurationMs() <= 0L) {
            return;
        }
        float ratio = (float) ((mouseX - this.lastProgressRect.getX()) / this.lastProgressRect.getWidth());
        ratio = clamp(ratio, 0.0f, 1.0f);
        this.pendingSeekMs = (long) (state.getDurationMs() * ratio);
    }

    private void updateVolume(double mouseX) {
        if (this.lastVolumeRect == null) {
            return;
        }
        float ratio = (float) ((mouseX - this.lastVolumeRect.getX()) / this.lastVolumeRect.getWidth());
        this.pendingVolume = clamp(ratio, 0.0f, 1.0f);
        this.service.setVolume(this.pendingVolume, false);
    }

    @Override
    public void onClose() {
        ConfigManager.requestSaveIfReady();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        PLAYER,
        SEARCH,
        QUEUE,
        ABOUT
    }

    private enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum DragTarget {
        NONE,
        PROGRESS,
        VOLUME
    }

    private record ClickArea(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return MusicScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }
}
