package shit.zen.music.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, List<LyricLine>> lyrics = new ConcurrentHashMap<>();
    private final Map<String, Boolean> lyricRequested = new ConcurrentHashMap<>();
    private Page page = Page.SEARCH;
    private String searchText = "";
    private String selectedSource;
    private MusicService.SearchType searchType = MusicService.SearchType.ALL;
    private volatile List<MusicTrack> searchResults = List.of();
    private volatile boolean searching;
    private boolean searchFocused;
    private boolean searchDirty;
    private boolean agreementChecked;
    private long lastSearchEditMs;
    private float searchScroll;
    private float queueScroll;
    private float lyricScroll;
    private DragTarget dragTarget = DragTarget.NONE;
    private Rectangle lastProgressRect;
    private Rectangle lastVolumeRect;

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
        float panelH = Math.min(420.0f, this.height - 36.0f);
        float x = (this.width - panelW) * 0.5f;
        float y = (this.height - panelH) * 0.5f;
        this.drawPanel(context, x, y, panelW, panelH, 12.0f, true);
        if (LOGO != null) {
            context.drawTexture(new Texture(LOGO, 200, 200), Rectangle.ofXYWH(0, 0, 200, 200),
                    Rectangle.ofXYWH(x + panelW * 0.5f - 28.0f, y + 22.0f, 56.0f, 56.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune Music Usage Notice", x + panelW * 0.5f, y + 92.0f, TITLE, WHITE, Align.CENTER);
        this.text(context, "Please read before continuing", x + panelW * 0.5f, y + 124.0f, BODY, MUTED, Align.CENTER);

        float itemX = x + 52.0f;
        float itemY = y + 166.0f;
        this.noticeLine(context, itemX, itemY, "Experimental Platform",
                "Features may change, be incomplete, or behave unexpectedly.");
        this.noticeLine(context, itemX, itemY + 58.0f, "Acknowledgement of GD Music",
                "Mizulune Music uses GD Music API for searching and playback.");
        this.noticeLine(context, itemX, itemY + 116.0f, "Not a Distributor",
                "The client does not upload, host, or distribute music content.");
        this.noticeLine(context, itemX, itemY + 174.0f, "Personal Learning and Testing Only",
                "Do not use it for commercial purposes or redistribution.");

        float checkY = y + panelH - 94.0f;
        this.drawButton(context, x + 32.0f, checkY, panelW - 64.0f, 32.0f,
                (this.agreementChecked ? "[x] " : "[ ] ") + "I have read and agree to the usage notice",
                mouseX, mouseY, false, () -> this.agreementChecked = !this.agreementChecked);
        this.drawButton(context, x + 32.0f, y + panelH - 50.0f, panelW * 0.5f - 40.0f, 34.0f,
                "Exit", mouseX, mouseY, false, this::onClose);
        this.drawButton(context, x + panelW * 0.5f + 8.0f, y + panelH - 50.0f, panelW * 0.5f - 40.0f, 34.0f,
                "Continue", mouseX, mouseY, !this.agreementChecked, () -> {
                    this.service.acceptDisclaimer();
                    ConfigManager.requestSaveIfReady();
                });
    }

    private void noticeLine(DrawContext context, float x, float y, String title, String body) {
        this.text(context, "*", x, y + 2.0f, H1, WHITE, Align.LEFT);
        this.text(context, title, x + 34.0f, y, BODY, WHITE, Align.LEFT);
        this.text(context, body, x + 34.0f, y + 24.0f, SMALL, MUTED, Align.LEFT);
    }

    private void renderMain(DrawContext context, int mouseX, int mouseY) {
        float outerW = Math.max(320.0f, Math.min(this.width - 28.0f, 1080.0f));
        float outerH = Math.max(260.0f, Math.min(this.height - 24.0f, 620.0f));
        float x = (this.width - outerW) * 0.5f;
        float y = (this.height - outerH) * 0.5f;
        float sidebarW = Math.min(188.0f, Math.max(136.0f, outerW * 0.23f));
        float bottomH = 76.0f;
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
                    Rectangle.ofXYWH(x + 24.0f, y + 26.0f, 52.0f, 52.0f), paint(0xFFFFFFFF));
        }
        this.text(context, "Mizulune", x + 82.0f, y + 34.0f, BODY, WHITE, Align.LEFT);
        this.text(context, "Music", x + 82.0f, y + 58.0f, SMALL, MUTED, Align.LEFT);
        float navY = y + 126.0f;
        this.navItem(context, x + 18.0f, navY, width - 36.0f, Page.PLAYER, "Player", "Now playing", mouseX, mouseY);
        this.navItem(context, x + 18.0f, navY + 54.0f, width - 36.0f, Page.SEARCH, "Search", "GD Music API", mouseX, mouseY);
        this.navItem(context, x + 18.0f, navY + 108.0f, width - 36.0f, Page.QUEUE, "Queue", "Current list", mouseX, mouseY);
        this.navItem(context, x + 18.0f, navY + 162.0f, width - 36.0f, Page.ABOUT, "About", "Thanks", mouseX, mouseY);
        this.text(context, this.module.useLiquidGlass() ? "Liquid Glass" : "Frosted Glass",
                x + 24.0f, y + height - 28.0f, SMALL, DIM, Align.LEFT);
    }

    private void navItem(DrawContext context, float x, float y, float width, Page target, String title,
                         String subtitle, int mouseX, int mouseY) {
        boolean active = this.page == target;
        boolean hover = contains(mouseX, mouseY, x, y, width, 42.0f);
        int fill = active ? 0x882A2A2A : hover ? 0x55202020 : 0x22181818;
        this.rounded(context, x, y, width, 42.0f, 7.0f, fill);
        if (active) {
            this.stroke(context, x, y, width, 42.0f, 7.0f, LINE_STRONG, 1.0f);
        }
        this.text(context, title, x + 18.0f, y + 7.0f, BODY, active ? WHITE : MUTED, Align.LEFT);
        this.text(context, subtitle, x + 18.0f, y + 26.0f, SMALL, active ? MUTED : DIM, Align.LEFT);
        this.clickAreas.add(new ClickArea(x, y, width, 42.0f, () -> this.page = target));
    }

    private void renderSearch(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        this.text(context, "Music Player", x + 28.0f, y + 28.0f, H1, WHITE, Align.LEFT);
        float inputX = x + 28.0f;
        float inputY = y + 68.0f;
        float buttonW = 96.0f;
        this.rounded(context, inputX, inputY, width - 56.0f, 38.0f, 7.0f, INPUT);
        this.stroke(context, inputX, inputY, width - 56.0f, 38.0f, 7.0f, this.searchFocused ? LINE_STRONG : LINE, 1.0f);
        String value = this.searchText.isEmpty() && !this.searchFocused ? "Search music" : this.searchText;
        this.text(context, value, inputX + 18.0f, inputY + 9.0f, BODY,
                this.searchText.isEmpty() && !this.searchFocused ? DIM : WHITE, Align.LEFT);
        this.clickAreas.add(new ClickArea(inputX, inputY, width - 56.0f - buttonW, 38.0f, () -> this.searchFocused = true));
        this.drawButton(context, inputX + width - 56.0f - buttonW, inputY + 3.0f, buttonW - 4.0f, 32.0f,
                this.searching ? "Searching" : "Search", mouseX, mouseY, false, this::startSearch);

        float chipX = inputX;
        float chipY = inputY + 52.0f;
        for (String source : this.service.config().getEnabledSources()) {
            float chipW = 70.0f;
            this.drawChip(context, chipX, chipY, chipW, source, source.equals(this.selectedSource), mouseX, mouseY,
                    () -> {
                        this.selectedSource = source;
                        this.startSearch();
                    });
            chipX += chipW + 8.0f;
        }
        chipX += 12.0f;
        for (MusicService.SearchType type : MusicService.SearchType.values()) {
            float chipW = Math.max(58.0f, this.measure(type.displayName(), SMALL) + 22.0f);
            this.drawChip(context, chipX, chipY, chipW, type.displayName(),
                    this.searchType == type, mouseX, mouseY, !type.isSupported(), () -> {
                        this.searchType = type;
                        this.startSearch();
                    });
            chipX += chipW + 8.0f;
        }

        float listX = inputX;
        float listY = chipY + 48.0f;
        float rowH = 58.0f;
        float listH = height - (listY - y) - 14.0f;
        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, width - 56.0f, listH));
        float yy = listY - this.searchScroll;
        if (this.searchResults.isEmpty()) {
            String message = this.searching ? "Searching GD Music API..." :
                    this.service.lastMessage().isBlank() ? "Type a keyword and search." : this.service.lastMessage();
            this.text(context, message, listX + 8.0f, listY + 18.0f, BODY, MUTED, Align.LEFT);
        } else {
            for (MusicTrack track : this.searchResults) {
                this.renderTrackRow(context, track, -1, listX, yy, width - 56.0f, rowH, mouseX, mouseY, true);
                yy += rowH + 6.0f;
            }
        }
        context.restore();
    }

    private void renderPlayer(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        this.text(context, "Now Playing", x + 28.0f, y + 28.0f, H1, WHITE, Align.LEFT);
        float coverSize = Math.min(210.0f, Math.max(128.0f, width * 0.28f));
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
            this.text(context, state.isCached() ? "Cached in .mizulune/music" : "Temporary cache while playing",
                    coverX, coverY + coverSize + 106.0f, SMALL, DIM, Align.LEFT);
        }

        float lyricsX = coverX + coverSize + 32.0f;
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
        this.drawButton(context, x + width - 252.0f, y + 28.0f, 108.0f, 32.0f,
                "Clear Queue", mouseX, mouseY, false, this.service::clearQueue);
        this.drawButton(context, x + width - 134.0f, y + 28.0f, 106.0f, 32.0f,
                "Mode: " + this.service.playbackState().getPlayMode().name(), mouseX, mouseY, false, this.service::cyclePlayMode);
        List<MusicTrack> queue = this.service.queueSnapshot();
        float listX = x + 28.0f;
        float listY = y + 92.0f;
        float listW = width - 56.0f;
        float listH = height - 116.0f;
        float rowH = 62.0f;
        context.save();
        context.clip(Rectangle.ofXYWH(listX, listY, listW, listH));
        if (queue.isEmpty()) {
            this.text(context, "Queue is empty.", listX, listY + 18.0f, BODY, MUTED, Align.LEFT);
        } else {
            float yy = listY - this.queueScroll;
            for (int i = 0; i < queue.size(); i++) {
                this.renderTrackRow(context, queue.get(i), i, listX, yy, listW, rowH, mouseX, mouseY, false);
                yy += rowH + 8.0f;
            }
        }
        context.restore();
        this.text(context, queue.size() + " tracks", x + width * 0.5f, y + height - 24.0f, SMALL, DIM, Align.CENTER);
    }

    private void renderAbout(DrawContext context, float x, float y, float width, float height) {
        this.text(context, "About Mizulune Music", x + 28.0f, y + 32.0f, H1, WHITE, Align.LEFT);
        float cardY = y + 82.0f;
        float half = (width - 74.0f) * 0.5f;
        this.aboutBlock(context, x + 28.0f, cardY, half, 140.0f, "Special Thanks",
                "Thanks to GD-Studio / GD Music Platform.\nBilibili creator / GD\u97f3\u4e50\u53f0.\nMusic resources make this module possible.");
        this.aboutBlock(context, x + 46.0f + half, cardY, half, 140.0f, "Project",
                "An experimental music player module for the Mizulune client.\nSearch, cache, queue, and play music inside Minecraft.");
        this.aboutBlock(context, x + 28.0f, cardY + 166.0f, width - 56.0f, 96.0f, "Author",
                "GitHub: Castorice1337\nMizulune client experimental platform.");
        this.aboutBlock(context, x + 28.0f, cardY + 286.0f, width - 56.0f, 86.0f, "Notice",
                "For personal learning and testing only.\nThe client is not a music distributor and does not host music content.");
    }

    private void aboutBlock(DrawContext context, float x, float y, float width, float height, String title, String body) {
        this.rounded(context, x, y, width, height, 9.0f, 0x66151515);
        this.stroke(context, x, y, width, height, 9.0f, LINE, 1.0f);
        this.text(context, title, x + 20.0f, y + 18.0f, BODY, WHITE, Align.LEFT);
        String[] lines = body.split("\\n");
        float yy = y + 52.0f;
        for (String line : lines) {
            this.text(context, line, x + 20.0f, yy, SMALL, MUTED, Align.LEFT);
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
        this.drawCover(context, track, x + 8.0f, y + 8.0f, cover);
        float textX = x + cover + 22.0f;
        this.text(context, ellipsize(track.getName(), 42), textX, y + 10.0f, BODY, WHITE, Align.LEFT);
        this.text(context, ellipsize(track.artistsText(), 52), textX, y + 34.0f, SMALL, MUTED, Align.LEFT);
        if (!searchResult) {
            this.text(context, Integer.toString(index + 1), x + width - 172.0f, y + 21.0f, BODY, DIM, Align.CENTER);
        }
        float bx = x + width - (searchResult ? 142.0f : 104.0f);
        this.iconButton(context, bx, y + 14.0f, ">", mouseX, mouseY, () -> {
            if (searchResult) {
                this.service.playTrack(track);
            } else {
                this.service.playQueueIndex(index);
            }
        });
        if (searchResult) {
            this.iconButton(context, bx + 46.0f, y + 14.0f, "v", mouseX, mouseY, () -> this.service.cacheAndPlayTrack(track));
            this.iconButton(context, bx + 92.0f, y + 14.0f, "+", mouseX, mouseY, () -> this.service.addToQueue(track));
        } else {
            this.iconButton(context, bx + 52.0f, y + 14.0f, "x", mouseX, mouseY, () -> this.service.removeQueueIndex(index));
        }
    }

    private void renderBottomBar(DrawContext context, float x, float y, float width, float height, int mouseX, int mouseY) {
        MusicPlaybackState state = this.service.playbackState();
        MusicTrack track = state.getCurrentTrack();
        float cover = 48.0f;
        this.drawCover(context, track, x + 18.0f, y + 14.0f, cover);
        this.text(context, track == null ? "No track" : ellipsize(track.getName(), 22), x + 78.0f, y + 17.0f, BODY, WHITE, Align.LEFT);
        this.text(context, track == null ? "Mizulune Music" : ellipsize(track.artistsText(), 26), x + 78.0f, y + 40.0f, SMALL, MUTED, Align.LEFT);

        float progressX = x + Math.min(280.0f, width * 0.30f);
        float progressW = Math.max(170.0f, width * 0.28f);
        float barY = y + 38.0f;
        this.text(context, timestamp(state.getPositionMs()), progressX - 52.0f, barY - 8.0f, SMALL, MUTED, Align.LEFT);
        this.drawSlider(context, progressX, barY, progressW, state.getDurationMs() <= 0L ? 0.0f : (float) state.getPositionMs() / state.getDurationMs());
        this.text(context, timestamp(state.getDurationMs()), progressX + progressW + 16.0f, barY - 8.0f, SMALL, MUTED, Align.LEFT);
        this.lastProgressRect = Rectangle.ofXYWH(progressX, barY - 10.0f, progressW, 22.0f);
        this.clickAreas.add(new ClickArea(progressX, barY - 10.0f, progressW, 22.0f, () -> this.dragTarget = DragTarget.PROGRESS));

        float controlsX = x + width * 0.58f;
        this.iconButton(context, controlsX - 64.0f, y + 22.0f, "|<", mouseX, mouseY, this.service::previous);
        this.iconButton(context, controlsX - 18.0f, y + 16.0f, state.isPlaying() ? "||" : ">", mouseX, mouseY, this.service::togglePlay, 38.0f);
        this.iconButton(context, controlsX + 36.0f, y + 22.0f, ">|", mouseX, mouseY, this.service::next);
        this.drawButton(context, controlsX + 86.0f, y + 18.0f, 92.0f, 34.0f, modeLabel(state.getPlayMode()),
                mouseX, mouseY, false, this.service::cyclePlayMode);

        float volumeX = x + width - 156.0f;
        this.text(context, "Vol", volumeX - 34.0f, barY - 8.0f, SMALL, MUTED, Align.LEFT);
        this.drawSlider(context, volumeX, barY, 112.0f, state.getVolume());
        this.lastVolumeRect = Rectangle.ofXYWH(volumeX, barY - 10.0f, 112.0f, 22.0f);
        this.clickAreas.add(new ClickArea(volumeX, barY - 10.0f, 112.0f, 22.0f, () -> this.dragTarget = DragTarget.VOLUME));
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
                context.drawTexture(new Texture(file, 300, 300), Rectangle.ofXYWH(0, 0, 300, 300),
                        Rectangle.ofXYWH(x, y, size, size), paint(0xFFFFFFFF));
                context.restore();
                return;
            }
        }
        this.stroke(context, x, y, size, size, 8.0f, LINE, 1.0f);
        this.text(context, "M", x + size * 0.5f, y + size * 0.5f - 10.0f, H1, MUTED, Align.CENTER);
    }

    private Path coverFor(MusicTrack track) {
        String key = track.stableKey();
        Path file = this.coverFiles.get(key);
        if (file != null) {
            return file;
        }
        if (this.coverRequested.putIfAbsent(key, Boolean.TRUE) == null) {
            this.service.cover(track).thenAccept(path -> {
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
            this.service.lyrics(track).thenAccept(value -> this.lyrics.put(key, value == null ? List.of() : value));
        }
        return List.of();
    }

    private void startSearch() {
        this.searchDirty = false;
        String query = this.searchText.trim();
        if (query.isEmpty()) {
            this.searchResults = List.of();
            this.searching = false;
            return;
        }
        this.searching = true;
        CompletableFuture<List<MusicTrack>> future = this.service.search(this.selectedSource, this.searchType, query);
        future.thenAccept(results -> {
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
        this.text(context, icon, x + size * 0.5f, y + size * 0.5f - 8.0f, BODY, hover ? WHITE : MUTED, Align.CENTER);
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
        this.dragTarget = DragTarget.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (this.page == Page.SEARCH) {
            this.searchScroll = Math.max(0.0f, this.searchScroll - (float) scrollDelta * 28.0f);
            return true;
        }
        if (this.page == Page.QUEUE) {
            this.queueScroll = Math.max(0.0f, this.queueScroll - (float) scrollDelta * 28.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.searchFocused || codePoint < 32 || codePoint == 127) {
            return super.charTyped(codePoint, modifiers);
        }
        this.searchText += codePoint;
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
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.searchText.isEmpty()) {
                this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
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
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        this.service.seek((long) (state.getDurationMs() * ratio));
    }

    private void updateVolume(double mouseX) {
        if (this.lastVolumeRect == null) {
            return;
        }
        float ratio = (float) ((mouseX - this.lastVolumeRect.getX()) / this.lastVolumeRect.getWidth());
        this.service.setVolume(Math.max(0.0f, Math.min(1.0f, ratio)));
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
