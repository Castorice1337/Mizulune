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
import shit.zen.render.FontFormat;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.Fonts;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Rectangle;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;

public class MusicScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.tryParse("mizulune:textures/gui/mizulune_logo.png");
    private static final FontRenderer TITLE = Fonts.getRenderer("pingfang_sc_regular.ttf", 34.0f, FontFormat.TTF);
    private static final FontRenderer H1 = Fonts.getRenderer("pingfang_sc_regular.ttf", 26.0f, FontFormat.TTF);
    private static final FontRenderer BODY = Fonts.getRenderer("pingfang_sc_regular.ttf", 19.0f, FontFormat.TTF);
    private static final FontRenderer SMALL = Fonts.getRenderer("pingfang_sc_regular.ttf", 16.0f, FontFormat.TTF);
    private static final FontRenderer ICON = FontPresets.materialIcons(22.0f);
    private static final FontRenderer ICON_SMALL = FontPresets.materialIcons(18.0f);
    private static final FontRenderer ICON_LARGE = FontPresets.materialIcons(28.0f);
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
    private static final String ICON_ONE_LOOP = "\uE040";
    private static final String ICON_VOLUME = "\uE050";
    private static final String ICON_FOLDER = "\uE2C7";
    private static final String ICON_CHECK = "\uE5CA";
    private static final String ICON_HEART = "\uE87D";
    private static final String ICON_PERSON = "\uE7FD";
    private static final String ICON_LAB = "\uEA4B";
    private static final String ICON_RULES = "\uE894";
    private static final int WHITE = 0xFFF2F2F2;
    private static final int MUTED = 0xFFB6B6B6;
    private static final int DIM = 0xFF737373;
    private static final int LINE = 0x44FFFFFF;
    private static final int LINE_STRONG = 0x99FFFFFF;
    private static final int PANEL = 0xD60A0A0A;
    private static final int PANEL_SOFT = 0xA6121212;
    private static final int ROW = 0x661A1A1A;
    private static final int ROW_HOVER = 0x88232323;
    private static final int INPUT = 0x991C1C1C;

    private final MusicService service;
    private final MizuluneMusic module;
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private final Map<String, Path> coverFiles = new ConcurrentHashMap<>();
    private final Map<String, Boolean> coverRequested = new ConcurrentHashMap<>();
    private final Map<String, Texture> coverTextures = new ConcurrentHashMap<>();
    private final Map<String, List<LyricLine>> lyrics = new ConcurrentHashMap<>();
    private final Map<String, Boolean> lyricRequested = new ConcurrentHashMap<>();
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
    private float searchScroll;
    private float queueScroll;
    private float maxSearchScroll;
    private float maxQueueScroll;
    private float lyricScroll;
    private DragTarget dragTarget = DragTarget.NONE;
    private Rectangle lastProgressRect;
    private Rectangle lastVolumeRect;
    private long pendingSeekMs = -1L;
    private float pendingVolume = -1.0f;

    public MusicScreen(MusicService service, MizuluneMusic module) {
        super(Component.literal("Mizulune Music"));
        this.service = service;
        this.module = module;
        this.selectedSource = service.config().getDefaultSource();
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
        Renderer.render(guiGraphics, context -> {
            if (!this.service.config().isDisclaimerAccepted()) {
                this.renderAgreement(context, mouseX, mouseY);
            } else {
                this.renderMain(context, mouseX, mouseY);
            }
        });
    }

    private void renderAgreement(DrawContext context, int mouseX, int mouseY) {
        float panelW = Math.min(620.0f, this.width - 36.0f);
        float panelH = Math.min(500.0f, this.height - 36.0f);
        float x = (this.width - panelW) * 0.5f;
        float y = (this.height - panelH) * 0.5f;
        this.drawPanel(context, x, y, panelW, panelH, 12.0f, true);
        if (LOGO != null) {
            context.drawTexture(new Texture(LOGO, 200, 200), Rectangle.ofXYWH(0, 0, 200, 200),
                    Rectangle.ofXYWH(x + panelW * 0.5f - 26.0f, y + 20.0f, 52.0f, 52.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune Music Usage Notice", x + panelW * 0.5f, y + 86.0f, TITLE, WHITE, Align.CENTER);
        this.text(context, "Please read before continuing", x + panelW * 0.5f, y + 116.0f, BODY, MUTED, Align.CENTER);

        float boxX = x + 28.0f;
        float boxY = y + 150.0f;
        float boxW = panelW - 56.0f;
        float boxH = Math.max(230.0f, panelH - 252.0f);
        this.rounded(context, boxX, boxY, boxW, boxH, 9.0f, 0x52141414);
        this.stroke(context, boxX, boxY, boxW, boxH, 9.0f, LINE, 1.0f);
        float itemX = boxX + 34.0f;
        float itemY = boxY + 24.0f;
        this.noticeLine(context, itemX, itemY, ICON_LAB, "Experimental Platform",
                "Features may change, be incomplete, or behave unexpectedly.");
        this.noticeLine(context, itemX, itemY + 44.0f, ICON_PLAYER, "Acknowledgement of GD\u97f3\u4e50\u53f0 Functionality",
                "Mizulune Music provides access to GD\u97f3\u4e50\u53f0 (music.gdstudio.xyz) for search and playback.");
        this.noticeLine(context, itemX, itemY + 88.0f, ICON_INFO, "Not a Distributor",
                "The client does not upload, host, or distribute music content.");
        this.noticeLine(context, itemX, itemY + 132.0f, ICON_PERSON, "Personal Learning and Testing Only",
                "This software is intended for personal learning, research, and testing only.");
        this.noticeLine(context, itemX, itemY + 176.0f, ICON_RULES, "Comply with Third-Party Rules",
                "You agree to comply with GD\u97f3\u4e50\u53f0 and third-party source rules.");

        float checkY = y + panelH - 112.0f;
        boolean checkHover = contains(mouseX, mouseY, x + 32.0f, checkY, panelW - 64.0f, 34.0f);
        this.rounded(context, x + 32.0f, checkY, panelW - 64.0f, 34.0f, 7.0f, checkHover ? 0x66303030 : 0x44181818);
        this.stroke(context, x + 32.0f, checkY, panelW - 64.0f, 34.0f, 7.0f, checkHover ? LINE_STRONG : LINE, 1.0f);
        this.rounded(context, x + 48.0f, checkY + 8.0f, 18.0f, 18.0f, 4.0f, this.agreementChecked ? 0xFFEAEAEA : 0x221A1A1A);
        this.stroke(context, x + 48.0f, checkY + 8.0f, 18.0f, 18.0f, 4.0f, LINE_STRONG, 1.0f);
        if (this.agreementChecked) {
            this.icon(context, ICON_CHECK, x + 57.0f, checkY + 7.0f, ICON_SMALL, 0xFF101010, Align.CENTER);
        }
        this.text(context, "I have read and agree to the usage notice", x + 78.0f, checkY + 8.0f, SMALL, WHITE, Align.LEFT);
        this.clickAreas.add(new ClickArea(x + 32.0f, checkY, panelW - 64.0f, 34.0f,
                () -> this.agreementChecked = !this.agreementChecked));

        this.drawButton(context, x + 32.0f, y + panelH - 60.0f, panelW * 0.5f - 40.0f, 36.0f,
                "Exit", mouseX, mouseY, false, this::onClose);
        this.drawButton(context, x + panelW * 0.5f + 8.0f, y + panelH - 60.0f, panelW * 0.5f - 40.0f, 36.0f,
                "Continue", mouseX, mouseY, !this.agreementChecked, () -> {
                    this.service.acceptDisclaimer();
                    ConfigManager.requestSaveIfReady();
                });
    }

    private void noticeLine(DrawContext context, float x, float y, String icon, String title, String body) {
        this.icon(context, icon, x, y + 4.0f, ICON, MUTED, Align.CENTER);
        this.text(context, title, x + 34.0f, y, BODY, WHITE, Align.LEFT);
        this.text(context, ellipsize(body, 82), x + 34.0f, y + 22.0f, SMALL, MUTED, Align.LEFT);
    }

    private void renderMain(DrawContext context, int mouseX, int mouseY) {
        float outerW = Math.max(320.0f, Math.min(this.width - 36.0f, 960.0f));
        float outerH = Math.max(260.0f, Math.min(this.height - 34.0f, 540.0f));
        float x = (this.width - outerW) * 0.5f;
        float y = (this.height - outerH) * 0.5f;
        float sidebarW = Math.min(170.0f, Math.max(136.0f, outerW * 0.21f));
        float bottomH = 66.0f;
        this.drawPanel(context, x, y, outerW, outerH, 12.0f, true);
        this.renderSidebar(context, x, y, sidebarW, outerH - bottomH, mouseX, mouseY);
        float contentX = x + sidebarW;
        float contentY = y;
        float contentW = outerW - sidebarW;
        float contentH = outerH - bottomH;
        this.line(context, contentX, y, contentX, y + outerH, LINE);
        this.line(context, x, y + contentH, x + outerW, y + contentH, LINE);
        switch (this.page) {
            case PLAYER -> this.renderPlayer(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case SEARCH -> this.renderSearch(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case QUEUE -> this.renderQueue(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case ABOUT -> this.renderAbout(context, contentX, contentY, contentW, contentH);
        }
        this.renderBottomBar(context, x, y + contentH, outerW, bottomH, mouseX, mouseY);
    }

    private void renderSidebar(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        if (LOGO != null) {
            context.drawTexture(new Texture(LOGO, 200, 200), Rectangle.ofXYWH(0, 0, 200, 200),
                    Rectangle.ofXYWH(x + 20.0f, y + 24.0f, 46.0f, 46.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune", x + 72.0f, y + 32.0f, BODY, WHITE, Align.LEFT);
        this.text(context, "Music", x + 72.0f, y + 54.0f, SMALL, MUTED, Align.LEFT);
        float navY = y + 112.0f;
        this.navItem(context, x + 14.0f, navY, width - 28.0f, Page.PLAYER, ICON_PLAYER, "Player", mouseX, mouseY);
        this.navItem(context, x + 14.0f, navY + 46.0f, width - 28.0f, Page.SEARCH, ICON_SEARCH, "Search", mouseX, mouseY);
        this.navItem(context, x + 14.0f, navY + 92.0f, width - 28.0f, Page.QUEUE, ICON_QUEUE, "Queue", mouseX, mouseY);
        this.navItem(context, x + 14.0f, navY + 138.0f, width - 28.0f, Page.ABOUT, ICON_INFO, "About", mouseX, mouseY);
        this.text(context, this.module.useLiquidGlass() ? "Liquid Glass" : "Frosted Glass",
                x + 24.0f, y + height - 28.0f, SMALL, DIM, Align.LEFT);
    }

    private void navItem(DrawContext context, float x, float y, float width, Page target, String icon, String title,
                         int mouseX, int mouseY) {
        boolean active = this.page == target;
        boolean hover = contains(mouseX, mouseY, x, y, width, 36.0f);
        int fill = active ? 0x882A2A2A : hover ? 0x4F242424 : 0x16181818;
        this.rounded(context, x, y, width, 36.0f, 7.0f, fill);
        if (active) {
            this.stroke(context, x, y, width, 36.0f, 7.0f, LINE_STRONG, 1.0f);
        }
        this.icon(context, icon, x + 22.0f, y + 8.0f, ICON, active ? WHITE : MUTED, Align.CENTER);
        this.text(context, title, x + 48.0f, y + 8.0f, BODY, active ? WHITE : MUTED, Align.LEFT);
        this.clickAreas.add(new ClickArea(x, y, width, 36.0f, () -> this.page = target));
    }

    private void renderSearch(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        this.text(context, "Music Player", x + 28.0f, y + 28.0f, H1, WHITE, Align.LEFT);
        float inputX = x + 28.0f;
        float inputY = y + 64.0f;
        float buttonW = 88.0f;
        float inputW = width - 56.0f;
        this.rounded(context, inputX, inputY, inputW, 34.0f, 7.0f, INPUT);
        this.stroke(context, inputX, inputY, inputW, 34.0f, 7.0f, this.searchFocused ? LINE_STRONG : LINE, 1.0f);
        this.icon(context, ICON_SEARCH, inputX + 21.0f, inputY + 6.0f, ICON, MUTED, Align.CENTER);
        String value = this.searchText.isEmpty() && !this.searchFocused ? "Search music" : this.searchText;
        if (this.searchFocused) {
            float cursorX = Math.min(inputX + inputW - buttonW - 14.0f, inputX + 42.0f + this.measure(value, BODY) + 3.0f);
            if (this.searchSelectAll && !this.searchText.isEmpty()) {
                float selectedW = Math.min(inputW - buttonW - 58.0f, this.measure(this.searchText, BODY) + 6.0f);
                this.rounded(context, inputX + 39.0f, inputY + 7.0f, selectedW, 20.0f, 4.0f, 0x44FFFFFF);
            } else {
                this.line(context, cursorX, inputY + 9.0f, cursorX, inputY + 25.0f, 0xCCFFFFFF);
            }
        }
        this.text(context, ellipsize(value, 48), inputX + 42.0f, inputY + 7.0f, BODY,
                this.searchText.isEmpty() && !this.searchFocused ? DIM : WHITE, Align.LEFT);
        this.clickAreas.add(new ClickArea(inputX, inputY, inputW - buttonW, 34.0f, () -> {
            this.searchFocused = true;
            this.searchSelectAll = false;
        }));
        this.drawButton(context, inputX + inputW - buttonW + 3.0f, inputY + 3.0f, buttonW - 6.0f, 28.0f,
                this.searching ? "Searching" : "Search", mouseX, mouseY, false, this::startSearch);

        float chipX = inputX;
        float chipY = inputY + 46.0f;
        for (MusicService.SearchType type : supportedSearchTypes()) {
            float chipW = Math.max(58.0f, this.measure(type.displayName(), SMALL) + 22.0f);
            this.drawChip(context, chipX, chipY, chipW, type.displayName(),
                    this.searchType == type, mouseX, mouseY, false, () -> {
                        this.searchType = type;
                        this.startSearch();
                    });
            chipX += chipW + 8.0f;
        }
        float sourceX = inputX + inputW;
        List<String> sources = this.service.config().getEnabledSources();
        for (int i = sources.size() - 1; i >= 0; i--) {
            String source = sources.get(i);
            float chipW = Math.max(52.0f, this.measure(source, SMALL) + 18.0f);
            sourceX -= chipW;
            this.drawChip(context, sourceX, chipY, chipW, source, source.equals(this.selectedSource), mouseX, mouseY,
                    false, () -> {
                        this.selectedSource = source;
                        this.startSearch();
                    });
            sourceX -= 6.0f;
        }

        float listX = inputX;
        float listY = chipY + 42.0f;
        float rowH = 50.0f;
        float listW = width - 56.0f;
        float listH = height - (listY - y) - 12.0f;
        this.maxSearchScroll = Math.max(0.0f, this.searchResults.size() * (rowH + 5.0f) - 5.0f - listH);
        this.searchScroll = clamp(this.searchScroll, 0.0f, this.maxSearchScroll);
        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, listW, listH));
        float yy = listY - this.searchScroll;
        if (this.searchResults.isEmpty()) {
            String message = this.searching ? "Searching GD Music API..." :
                    this.service.lastMessage().isBlank() ? "Type a keyword and search." : this.service.lastMessage();
            this.text(context, message, listX + 8.0f, listY + 18.0f, BODY, MUTED, Align.LEFT);
        } else {
            for (MusicTrack track : this.searchResults) {
                this.renderTrackRow(context, track, -1, listX, yy, listW, rowH, mouseX, mouseY, true);
                yy += rowH + 5.0f;
            }
        }
        context.restore();
    }

    private void renderPlayer(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        this.text(context, "Now Playing", x + 28.0f, y + 28.0f, H1, WHITE, Align.LEFT);
        float coverSize = Math.min(178.0f, Math.max(118.0f, width * 0.26f));
        float coverX = x + 28.0f;
        float coverY = y + 74.0f;
        this.drawCover(context, track, coverX, coverY, coverSize);
        if (track == null) {
            this.text(context, "No track playing", coverX, coverY + coverSize + 28.0f, H1, WHITE, Align.LEFT);
            this.text(context, "Search a track or pick one from Queue.", coverX, coverY + coverSize + 58.0f, BODY, MUTED, Align.LEFT);
        } else {
            this.text(context, ellipsize(track.getName(), 26), coverX, coverY + coverSize + 26.0f, H1, WHITE, Align.LEFT);
            this.text(context, ellipsize(track.artistsText(), 32), coverX, coverY + coverSize + 54.0f, BODY, MUTED, Align.LEFT);
            this.text(context, "Source: " + MusicService.normalizeSource(track.getSource()), coverX, coverY + coverSize + 84.0f, SMALL, DIM, Align.LEFT);
            this.icon(context, ICON_FOLDER, coverX + 10.0f, coverY + coverSize + 108.0f, ICON_SMALL, DIM, Align.CENTER);
            this.text(context, state.isCached() ? "Cached in .mizulune/music" : "Temporary cache while playing",
                    coverX + 26.0f, coverY + coverSize + 104.0f, SMALL, DIM, Align.LEFT);
        }

        float lyricsX = coverX + coverSize + 28.0f;
        float lyricsY = y + 74.0f;
        float lyricsW = width - (lyricsX - x) - 28.0f;
        float lyricsH = height - 104.0f;
        this.rounded(context, lyricsX, lyricsY, lyricsW, lyricsH, 10.0f, 0x66131313);
        this.stroke(context, lyricsX, lyricsY, lyricsW, lyricsH, 10.0f, LINE, 1.0f);
        this.renderLyrics(context, track, state, lyricsX + 22.0f, lyricsY + 18.0f, lyricsW - 44.0f, lyricsH - 36.0f);
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
        float lineH = 32.0f;
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
                this.text(context, ellipsize(text, 72), x, yy, active ? BODY : SMALL, active ? WHITE : MUTED, Align.LEFT);
            }
            yy += lineH;
        }
        context.restore();
    }

    private void renderQueue(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        this.text(context, "Playback Queue", x + 28.0f, y + 28.0f, H1, WHITE, Align.LEFT);
        this.text(context, "Current list", x + 28.0f, y + 58.0f, BODY, MUTED, Align.LEFT);
        this.drawButton(context, x + width - 238.0f, y + 28.0f, 104.0f, 30.0f,
                "Clear Queue", mouseX, mouseY, false, this.service::clearQueue);
        this.drawButton(context, x + width - 124.0f, y + 28.0f, 96.0f, 30.0f,
                modeLabel(this.service.playbackState().getPlayMode()), mouseX, mouseY, false, this.service::cyclePlayMode);
        List<MusicTrack> queue = this.service.queueSnapshot();
        float listX = x + 28.0f;
        float listY = y + 86.0f;
        float listW = width - 56.0f;
        float listH = height - 108.0f;
        float rowH = 54.0f;
        this.maxQueueScroll = Math.max(0.0f, queue.size() * (rowH + 6.0f) - 6.0f - listH);
        this.queueScroll = clamp(this.queueScroll, 0.0f, this.maxQueueScroll);
        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, listW, listH));
        if (queue.isEmpty()) {
            this.text(context, "Queue is empty.", listX, listY + 18.0f, BODY, MUTED, Align.LEFT);
        } else {
            float yy = listY - this.queueScroll;
            for (int i = 0; i < queue.size(); i++) {
                this.renderTrackRow(context, queue.get(i), i, listX, yy, listW, rowH, mouseX, mouseY, false);
                yy += rowH + 6.0f;
            }
        }
        context.restore();
        this.text(context, queue.size() + " tracks", x + width * 0.5f, y + height - 24.0f, SMALL, DIM, Align.CENTER);
    }

    private void renderAbout(DrawContext context, float x, float y, float width, float height) {
        this.text(context, "About Mizulune Music", x + 28.0f, y + 32.0f, H1, WHITE, Align.LEFT);
        float cardY = y + 78.0f;
        float half = (width - 74.0f) * 0.5f;
        this.aboutBlock(context, x + 28.0f, cardY, half, 132.0f, ICON_HEART, "Special Thanks",
                "Thanks to GD-Studio.\nBilibili creator / GD\u97f3\u4e50\u53f0 (music.gdstudio.xyz).\nMusic resources make this module possible.");
        this.aboutBlock(context, x + 46.0f + half, cardY, half, 132.0f, ICON_LAB, "Project",
                "An experimental music player module for the Mizulune client.\nSearch, cache, queue, and play music inside Minecraft.");
        this.aboutBlock(context, x + 28.0f, cardY + 154.0f, width - 56.0f, 82.0f, ICON_PERSON, "Author",
                "GitHub: Castorice1337\nMizulune client experimental platform.");
        this.aboutBlock(context, x + 28.0f, cardY + 258.0f, width - 56.0f, 82.0f, ICON_INFO, "Notice",
                "For personal learning and testing only.\nThe client is not a music distributor and does not host music content.");
    }

    private void aboutBlock(DrawContext context, float x, float y, float width, float height, String icon, String title, String body) {
        this.rounded(context, x, y, width, height, 9.0f, 0x66151515);
        this.stroke(context, x, y, width, height, 9.0f, LINE, 1.0f);
        this.icon(context, icon, x + 24.0f, y + 17.0f, ICON, MUTED, Align.CENTER);
        this.text(context, title, x + 48.0f, y + 18.0f, BODY, WHITE, Align.LEFT);
        String[] lines = body.split("\\n");
        float yy = y + 52.0f;
        for (String line : lines) {
            this.text(context, ellipsize(line, width > 400.0f ? 92 : 44), x + 24.0f, yy, SMALL, MUTED, Align.LEFT);
            yy += 23.0f;
        }
    }

    private void renderTrackRow(DrawContext context, MusicTrack track, int index, float x, float y, float width,
                                float height, int mouseX, int mouseY, boolean searchResult) {
        if (y + height < 0.0f || y > this.height) {
            return;
        }
        boolean current = !searchResult && index == this.service.currentQueueIndex();
        boolean hover = contains(mouseX, mouseY, x, y, width, height);
        this.rounded(context, x, y, width, height, 8.0f, current ? 0x88262626 : hover ? ROW_HOVER : ROW);
        this.stroke(context, x, y, width, height, 8.0f, current ? LINE_STRONG : LINE, 1.0f);
        float cover = height - 16.0f;
        float coverX = searchResult ? x + 8.0f : x + 44.0f;
        if (!searchResult) {
            if (current) {
                this.icon(context, ICON_VOLUME, x + 22.0f, y + 16.0f, ICON_SMALL, WHITE, Align.CENTER);
            } else {
                this.text(context, Integer.toString(index + 1), x + 22.0f, y + 17.0f, SMALL, DIM, Align.CENTER);
            }
        }
        this.drawCover(context, track, coverX, y + 8.0f, cover);
        float textX = coverX + cover + 14.0f;
        int titleChars = searchResult ? 42 : 34;
        this.text(context, ellipsize(track.getName(), titleChars), textX, y + 8.0f, BODY, WHITE, Align.LEFT);
        this.text(context, ellipsize(track.artistsText(), titleChars + 10), textX, y + 30.0f, SMALL, MUTED, Align.LEFT);
        if (!searchResult) {
            this.text(context, timestamp(track.getDurationMs()), x + width - 118.0f, y + 18.0f, SMALL, DIM, Align.RIGHT);
        }
        float bx = x + width - (searchResult ? 122.0f : 72.0f);
        this.iconButton(context, bx, y + 11.0f, ICON_PLAY, mouseX, mouseY, () -> {
            if (searchResult) {
                this.service.playTrack(track);
            } else {
                this.service.playQueueIndex(index);
            }
        });
        if (searchResult) {
            this.iconButton(context, bx + 40.0f, y + 11.0f, ICON_DOWNLOAD, mouseX, mouseY, () -> this.service.cacheAndPlayTrack(track));
            this.iconButton(context, bx + 80.0f, y + 11.0f, ICON_ADD, mouseX, mouseY, () -> this.service.addToQueue(track));
        } else {
            this.iconButton(context, bx + 40.0f, y + 11.0f, ICON_CLOSE, mouseX, mouseY, () -> this.service.removeQueueIndex(index));
        }
    }

    private void renderBottomBar(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        float cover = 42.0f;
        this.drawCover(context, track, x + 18.0f, y + 12.0f, cover);
        this.text(context, track == null ? "No track" : ellipsize(track.getName(), 20), x + 72.0f, y + 13.0f, BODY, WHITE, Align.LEFT);
        this.text(context, track == null ? "Mizulune Music" : ellipsize(track.artistsText(), 24), x + 72.0f, y + 36.0f, SMALL, MUTED, Align.LEFT);

        long displayPosition = this.dragTarget == DragTarget.PROGRESS && this.pendingSeekMs >= 0L
                ? this.pendingSeekMs
                : state.getPositionMs();
        float progressX = x + Math.min(270.0f, width * 0.29f);
        float progressW = Math.max(170.0f, width * 0.28f);
        float barY = y + 34.0f;
        this.text(context, timestamp(displayPosition), progressX - 50.0f, barY - 8.0f, SMALL, MUTED, Align.LEFT);
        this.drawSlider(context, progressX, barY, progressW,
                state.getDurationMs() <= 0L ? 0.0f : (float) displayPosition / state.getDurationMs());
        this.text(context, timestamp(state.getDurationMs()), progressX + progressW + 14.0f, barY - 8.0f, SMALL, MUTED, Align.LEFT);
        this.lastProgressRect = Rectangle.ofXYWH(progressX, barY - 10.0f, progressW, 22.0f);
        this.clickAreas.add(new ClickArea(progressX, barY - 10.0f, progressW, 22.0f, () -> this.dragTarget = DragTarget.PROGRESS));

        float controlsX = x + width * 0.58f;
        this.iconButton(context, controlsX - 66.0f, y + 18.0f, ICON_PREVIOUS, mouseX, mouseY, this.service::previous);
        this.iconButton(context, controlsX - 20.0f, y + 12.0f, state.isPlaying() ? ICON_PAUSE : ICON_PLAY, mouseX, mouseY, this.service::togglePlay, 40.0f);
        this.iconButton(context, controlsX + 36.0f, y + 18.0f, ICON_NEXT, mouseX, mouseY, this.service::next);
        this.iconTextButton(context, controlsX + 84.0f, y + 16.0f, 98.0f, 34.0f, modeIcon(state.getPlayMode()), modeLabel(state.getPlayMode()),
                mouseX, mouseY, false, this.service::cyclePlayMode);

        float volumeX = x + width - 136.0f;
        float volumeValue = this.dragTarget == DragTarget.VOLUME && this.pendingVolume >= 0.0f
                ? this.pendingVolume
                : state.getVolume();
        this.icon(context, ICON_VOLUME, volumeX - 26.0f, barY - 12.0f, ICON, MUTED, Align.CENTER);
        this.drawSlider(context, volumeX, barY, 100.0f, volumeValue);
        this.lastVolumeRect = Rectangle.ofXYWH(volumeX, barY - 10.0f, 100.0f, 22.0f);
        this.clickAreas.add(new ClickArea(volumeX, barY - 10.0f, 100.0f, 22.0f, () -> this.dragTarget = DragTarget.VOLUME));
        if (!state.getError().isBlank()) {
            this.text(context, ellipsize(state.getError(), 48), x + width * 0.5f, y + height - 18.0f, SMALL, MUTED, Align.CENTER);
        } else if (state.isLoading()) {
            this.text(context, "Loading...", x + width * 0.5f, y + height - 18.0f, SMALL, MUTED, Align.CENTER);
        }
    }

    private void drawCover(DrawContext context, MusicTrack track, float x, float y, float size) {
        this.rounded(context, x, y, size, size, 8.0f, 0x88222222);
        if (track != null) {
            Path file = this.coverFor(track);
            if (file != null) {
                context.save();
                context.clipRoundedRect(RoundedRectangle.ofXYWHR(x, y, size, size, 8.0f), true);
                Texture texture = this.coverTextures.computeIfAbsent(file.toAbsolutePath().normalize().toString(),
                        ignored -> new Texture(file, 300, 300));
                context.drawTexture(texture, Rectangle.ofXYWH(0, 0, 300, 300),
                        Rectangle.ofXYWH(x, y, size, size), paint(0xFFFFFFFF));
                context.restore();
                return;
            }
        }
        this.stroke(context, x, y, size, size, 8.0f, LINE, 1.0f);
        this.icon(context, ICON_PLAYER, x + size * 0.5f, y + size * 0.5f - 12.0f,
                size > 70.0f ? ICON_LARGE : ICON, MUTED, Align.CENTER);
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
                if (path != null) {
                    this.coverFiles.put(key, path);
                }
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
            this.searchScroll = 0.0f;
        });
    }

    private void drawButton(DrawContext context, float x, float y, float width, float height, String label,
                            int mouseX, int mouseY, boolean disabled, Runnable action) {
        boolean hover = !disabled && contains(mouseX, mouseY, x, y, width, height);
        this.rounded(context, x, y, width, height, 7.0f, disabled ? 0x33161616 : hover ? 0x88303030 : 0x661D1D1D);
        this.stroke(context, x, y, width, height, 7.0f, disabled ? 0x22FFFFFF : hover ? LINE_STRONG : LINE, 1.0f);
        this.text(context, label, x + width * 0.5f, y + height * 0.5f - 8.0f, SMALL, disabled ? DIM : WHITE, Align.CENTER);
        if (!disabled) {
            this.clickAreas.add(new ClickArea(x, y, width, height, action));
        }
    }

    private void iconTextButton(DrawContext context, float x, float y, float width, float height, String icon, String label,
                                int mouseX, int mouseY, boolean disabled, Runnable action) {
        boolean hover = !disabled && contains(mouseX, mouseY, x, y, width, height);
        this.rounded(context, x, y, width, height, 7.0f, disabled ? 0x33161616 : hover ? 0x88303030 : 0x661D1D1D);
        this.stroke(context, x, y, width, height, 7.0f, disabled ? 0x22FFFFFF : hover ? LINE_STRONG : LINE, 1.0f);
        float contentW = this.measure(label, SMALL) + 22.0f;
        float startX = x + (width - contentW) * 0.5f;
        this.icon(context, icon, startX + 8.0f, y + height * 0.5f - 10.0f, ICON_SMALL, disabled ? DIM : MUTED, Align.CENTER);
        this.text(context, label, startX + 22.0f, y + height * 0.5f - 8.0f, SMALL, disabled ? DIM : WHITE, Align.LEFT);
        if (!disabled) {
            this.clickAreas.add(new ClickArea(x, y, width, height, action));
        }
    }

    private void drawChip(DrawContext context, float x, float y, float width, String label, boolean active,
                          int mouseX, int mouseY, Runnable action) {
        this.drawChip(context, x, y, width, label, active, mouseX, mouseY, false, action);
    }

    private void drawChip(DrawContext context, float x, float y, float width, String label, boolean active,
                          int mouseX, int mouseY, boolean disabled, Runnable action) {
        boolean hover = !disabled && contains(mouseX, mouseY, x, y, width, 28.0f);
        this.rounded(context, x, y, width, 28.0f, 8.0f, active ? 0x99303030 : hover ? 0x66303030 : 0x441A1A1A);
        this.stroke(context, x, y, width, 28.0f, 8.0f, active ? LINE_STRONG : LINE, 1.0f);
        this.text(context, label, x + width * 0.5f, y + 7.0f, SMALL, disabled ? DIM : active ? WHITE : MUTED, Align.CENTER);
        if (!disabled) {
            this.clickAreas.add(new ClickArea(x, y, width, 28.0f, action));
        }
    }

    private void iconButton(DrawContext context, float x, float y, String icon, int mouseX, int mouseY, Runnable action) {
        this.iconButton(context, x, y, icon, mouseX, mouseY, action, 32.0f);
    }

    private void iconButton(DrawContext context, float x, float y, String icon, int mouseX, int mouseY, Runnable action, float size) {
        boolean hover = contains(mouseX, mouseY, x, y, size, size);
        this.rounded(context, x, y, size, size, size * 0.5f, hover ? 0x88404040 : 0x00111111);
        this.icon(context, icon, x + size * 0.5f, y + size * 0.5f - 10.0f,
                size >= 38.0f ? ICON_LARGE : ICON, hover ? WHITE : MUTED, Align.CENTER);
        this.clickAreas.add(new ClickArea(x, y, size, size, action));
    }

    private void drawSlider(DrawContext context, float x, float y, float width, float value) {
        float progress = Math.max(0.0f, Math.min(1.0f, value));
        this.rounded(context, x, y, width, 5.0f, 2.5f, 0x66383838);
        this.rounded(context, x, y, width * progress, 5.0f, 2.5f, 0xFFE1E1E1);
        this.rounded(context, x + width * progress - 5.0f, y - 3.0f, 10.0f, 10.0f, 5.0f, 0xFFFFFFFF);
    }

    private void drawPanel(DrawContext context, float x, float y, float width, float height, float radius, boolean strong) {
        RoundedRectangle rect = RoundedRectangle.ofXYWHR(x, y, width, height, radius);
        if (this.module.useLiquidGlass()) {
            LiquidGlassStyle style = LiquidGlassStyle.builder()
                    .blurRadius(18.0f)
                    .opacity(strong ? 0.88f : 0.72f)
                    .tint(0xAA000000, 0.34f)
                    .darkness(0.32f)
                    .chromaStrength(0.0f)
                    .build();
            context.drawLiquidGlassPanel(rect, style);
        } else {
            context.drawBackdropBlurredRoundedRect(rect, 18.0f, strong ? 0.92f : 0.78f, strong ? PANEL : PANEL_SOFT);
        }
        this.stroke(context, x, y, width, height, radius, LINE, 1.0f);
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

    private void text(DrawContext context, String text, float x, float y, FontRenderer font, int color, Align align) {
        float drawX = switch (align) {
            case LEFT -> x;
            case CENTER -> x - this.measure(text, font) * 0.5f;
            case RIGHT -> x - this.measure(text, font);
        };
        context.drawString(text, drawX, y, font, paint(color));
    }

    private void icon(DrawContext context, String icon, float x, float y, FontRenderer font, int color, Align align) {
        this.text(context, icon, x, y, font, color, align);
    }

    private float measure(String text, FontRenderer font) {
        return Renderer.getBackend() != null ? Renderer.getBackend().measureTextWidth(text, font) : font.getWidth(text);
    }

    private static Paint paint(int color) {
        return new Paint().setColor(color);
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
            case SINGLE -> "One Loop";
        };
    }

    private static String modeIcon(PlayMode mode) {
        return switch (mode) {
            case ORDER -> ICON_REPEAT;
            case SHUFFLE -> ICON_SHUFFLE;
            case SINGLE -> ICON_ONE_LOOP;
        };
    }

    private static MusicService.SearchType[] supportedSearchTypes() {
        return new MusicService.SearchType[] {
                MusicService.SearchType.ALL,
                MusicService.SearchType.SINGLE,
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
            this.searchScroll = clamp(this.searchScroll - (float) scrollDelta * 28.0f, 0.0f, this.maxSearchScroll);
            return true;
        }
        if (this.page == Page.QUEUE) {
            this.queueScroll = clamp(this.queueScroll - (float) scrollDelta * 28.0f, 0.0f, this.maxQueueScroll);
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
