package shit.zen.fantnel.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import shit.zen.fantnel.FantnelClientService;
import shit.zen.fantnel.FantnelProxySession;

/** First-party vanilla UI for FantNEL account, server, role and proxy flow. */
public final class FantnelScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 7;
    private final Screen parent;
    private final FantnelClientService service;

    private Page page = Page.STARTING;
    private FantnelClientService.HostStatus status;
    private List<FantnelClientService.Account> accounts = List.of();
    private List<FantnelClientService.Server> servers = List.of();
    private List<FantnelClientService.Role> roles = List.of();
    private FantnelClientService.Server selectedServer;
    private FantnelClientService.Role selectedRole;
    private FantnelClientService.Proxy activeProxy;
    private int serverOffset;
    private boolean busy;
    private boolean initialRequestStarted;
    private boolean startupRetryScheduled;
    private boolean captchaPrepared;
    private String notice = "正在启动 FantNEL Host...";
    private String accountType = "4399";

    private EditBox accountName;
    private EditBox credential;
    private EditBox captcha;
    private EditBox versionFilter;
    private EditBox newRole;
    private EditBox localPort;
    private ResourceLocation captchaTexture;
    private int captchaWidth;
    private int captchaHeight;

    public FantnelScreen(Screen parent) {
        this(parent, new FantnelClientService());
    }

    FantnelScreen(Screen parent, FantnelClientService service) {
        super(Component.literal("FantNEL"));
        this.parent = parent;
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected void init() {
        service.host().setGameDirectory(this.minecraft.gameDirectory.toPath());
        if (activeProxy == null) activeProxy = FantnelProxySession.activeProxy();
        int left = (this.width - PANEL_WIDTH) / 2;
        int bottom = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> onBack())
            .bounds(left, bottom, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refreshCurrent())
            .bounds(left + PANEL_WIDTH - 68, bottom, 68, 20).build());

        switch (page) {
            case STARTING -> {
                if (!initialRequestStarted) {
                    initialRequestStarted = true;
                    refreshStatus();
                }
            }
            case ACCOUNTS -> buildAccounts(left);
            case SERVERS -> buildServers(left);
            case ROLES -> buildRoles(left);
            case CONNECT -> buildConnect(left);
        }
    }

    private void buildAccounts(int left) {
        int y = 66;
        int shown = Math.min(accounts.size(), MAX_VISIBLE_ROWS - 2);
        for (int i = 0; i < shown; i++) {
            FantnelClientService.Account account = accounts.get(i);
            String label = account.name().isBlank() ? account.maskedAccount() : account.name();
            if (account.authenticated()) label += "  [当前]";
            int accountId = account.id();
            Button accountLabel = Button.builder(Component.literal(label), button -> { })
                .bounds(left, y + i * ROW_HEIGHT, 153, 20).build();
            accountLabel.active = false;
            this.addRenderableWidget(accountLabel);
            this.addRenderableWidget(Button.builder(Component.literal("登录"), button -> login(accountId))
                .bounds(left + 157, y + i * ROW_HEIGHT, 48, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("删除"), button -> deleteAccount(accountId))
                .bounds(left + 209, y + i * ROW_HEIGHT, 48, 20).build());
        }

        int formX = left + 266;
        accountName = edit(formX, 66, 164, "账号");
        credential = edit(formX, 90, 164, "凭据/密码");
        credential.setMaxLength(256);
        this.addRenderableWidget(Button.builder(Component.literal("类型: " + accountType), button -> {
            accountType = accountType.equals("4399") ? "4399com" : "4399";
            captchaPrepared = false;
            rebuildWidgets();
        }).bounds(formX, 114, 164, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("登录"), button -> loginEnteredAccount())
            .bounds(formX, 138, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("验证码"), button -> beginCaptcha())
            .bounds(formX + 84, 138, 80, 20).build());

        captcha = edit(formX, 162, 164, "手动验证码");
        this.addRenderableWidget(Button.builder(Component.literal("提交"), button -> submitCaptcha())
            .bounds(formX, 186, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("自动识别"), button -> autoCaptcha())
            .bounds(formX + 84, 186, 80, 20).build());

        if (status != null && status.authenticated()) {
            this.addRenderableWidget(Button.builder(Component.literal("进入服务器列表"), button -> loadServers(false))
                .bounds(left, 222, 257, 20).build());
        }
    }

    private void buildServers(int left) {
        versionFilter = edit(left, 54, 205, "版本筛选（可空）");
        this.addRenderableWidget(Button.builder(Component.literal("搜索"), button -> loadServers(true))
            .bounds(left + 209, 54, 58, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("上一页"), button -> {
            serverOffset = Math.max(0, serverOffset - MAX_VISIBLE_ROWS);
            loadServers(false);
        }).bounds(left + 281, 54, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("下一页"), button -> {
            serverOffset += MAX_VISIBLE_ROWS;
            loadServers(false);
        }).bounds(left + 355, 54, 75, 20).build());

        int y = 82;
        int shown = Math.min(servers.size(), MAX_VISIBLE_ROWS);
        for (int i = 0; i < shown; i++) {
            FantnelClientService.Server server = servers.get(i);
            String label = server.name() + (server.version().isBlank() ? "" : " · " + server.version())
                + (server.onlineCount() > 0 ? " · " + server.onlineCount() + " 在线" : "");
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> selectServer(server))
                .bounds(left, y + i * ROW_HEIGHT, PANEL_WIDTH, 20).build());
        }
    }

    private void buildRoles(int left) {
        int y = 72;
        int shown = Math.min(roles.size(), MAX_VISIBLE_ROWS);
        for (int i = 0; i < shown; i++) {
            FantnelClientService.Role role = roles.get(i);
            Component label = Component.literal(role.name());
            this.addRenderableWidget(Button.builder(label, button -> {
                selectedRole = role;
                page = Page.CONNECT;
                rebuildWidgets();
            }).bounds(left, y + i * ROW_HEIGHT, 265, 20).build());
        }
        newRole = edit(left + 276, 72, 154, "新角色名");
        this.addRenderableWidget(Button.builder(Component.literal("创建角色"), button -> createRole())
            .bounds(left + 276, 96, 154, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回服务器"), button -> {
            page = Page.SERVERS;
            rebuildWidgets();
        }).bounds(left + 276, 120, 154, 20).build());
    }

    private void buildConnect(int left) {
        localPort = edit(left, 108, 170, "本地端口（可空）");
        this.addRenderableWidget(Button.builder(Component.literal("启动代理并连接"), button -> startProxyAndConnect())
            .bounds(left, 140, 210, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("更换角色"), button -> {
            page = Page.ROLES;
            rebuildWidgets();
        }).bounds(left + 220, 140, 100, 20).build());
        if (activeProxy != null) {
            this.addRenderableWidget(Button.builder(Component.literal("停止代理"), button -> stopProxy())
                .bounds(left + 330, 140, 100, 20).build());
        }
    }

    private EditBox edit(int x, int y, int width, String hint) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        field.setHint(Component.literal(hint));
        this.addRenderableWidget(field);
        return field;
    }

    private void refreshStatus() {
        runAsync("正在启动 FantNEL Host...", service::status, result -> {
            status = result;
            if (!result.initialized()) {
                page = Page.STARTING;
                if (result.failed()) {
                    notice = "FantNEL Host 初始化失败: " + result.error();
                } else {
                    notice = "FantNEL Host 正在初始化，请稍候...";
                    scheduleStartupRetry();
                }
                rebuildWidgets();
                return;
            }
            if (result.authenticated()) {
                loadServers(false);
            } else {
                loadAccounts();
            }
        });
    }

    private void loadAccounts() {
        runAsync("正在读取账号...", service::accounts, result -> {
            accounts = result;
            page = Page.ACCOUNTS;
            notice = result.isEmpty() ? "尚无保存账号，请在右侧添加。" : "请选择账号登录。";
            rebuildWidgets();
        });
    }

    private void loginEnteredAccount() {
        if (accountName == null || credential == null) return;
        String type = accountType;
        String account = accountName.getValue().trim();
        String secret = credential.getValue().trim();
        if (account.isBlank() || secret.isBlank()) {
            notice = "请填写账号和凭据。";
            return;
        }

        String manual = captcha == null ? "" : captcha.getValue().trim();
        boolean requiresCaptcha = requiresCaptcha(type);
        String workingMessage = requiresCaptcha && !captchaPrepared
            ? "正在准备验证码并登录..." : "正在登录...";
        runAsync(workingMessage,
            () -> loginAfterCaptcha(requiresCaptcha, manual,
                () -> service.loginCredentials(type, account, secret)),
            this::onLoginSucceeded);
    }

    private void login(int id) {
        FantnelClientService.Account selected = accounts.stream().filter(value -> value.id() == id).findFirst().orElse(null);
        boolean requiresCaptcha = selected != null && requiresCaptcha(selected.type());
        String manual = captcha == null ? "" : captcha.getValue().trim();
        String workingMessage = requiresCaptcha && !captchaPrepared
            ? "正在准备验证码并登录..." : "正在登录...";
        runAsync(workingMessage,
            () -> loginAfterCaptcha(requiresCaptcha, manual, () -> service.login(id)),
            this::onLoginSucceeded);
    }

    private <T> CompletableFuture<T> loginAfterCaptcha(boolean required, String manual,
                                                        Supplier<CompletableFuture<T>> loginRequest) {
        return prepareCaptcha(required, manual)
            .thenCompose(ignored -> loginRequest.get())
            .whenComplete((ignored, error) -> {
                if (required && error != null) captchaPrepared = false;
            });
    }

    private CompletableFuture<Void> prepareCaptcha(boolean required, String manual) {
        if (!required || captchaPrepared) return CompletableFuture.completedFuture(null);
        if (!manual.isEmpty()) {
            return service.submitCaptcha(manual).thenRun(() -> captchaPrepared = true);
        }
        return service.autoCaptcha().thenRun(() -> captchaPrepared = true);
    }

    private void onLoginSucceeded(FantnelClientService.HostStatus result) {
        captchaPrepared = false;
        status = result;
        if (!result.authenticated()) {
            notice = "FantNEL 未返回已登录状态，请重试。";
            loadAccounts();
            return;
        }
        loadServers(false);
    }

    private static boolean requiresCaptcha(String type) {
        return type.equalsIgnoreCase("4399") || type.equalsIgnoreCase("4399com");
    }

    private void deleteAccount(int id) {
        runAsync("正在删除账号...", () -> service.deleteAccount(id), ignored -> loadAccounts());
    }

    private void beginCaptcha() {
        captchaPrepared = false;
        runAsync("正在获取验证码...", service::beginCaptcha, value -> {
            installCaptchaTexture(value.image());
            notice = "验证码已载入，可手动填写或自动识别。";
        });
    }

    private void submitCaptcha() {
        if (captcha == null) return;
        runAsync("正在提交验证码...", () -> service.submitCaptcha(captcha.getValue()),
            ignored -> {
                captchaPrepared = true;
                notice = "验证码已确认，可点击账号旁的“登录”。";
            });
    }

    private void autoCaptcha() {
        runAsync("FantNEL 正在识别验证码...", service::autoCaptcha,
            ignored -> {
                captchaPrepared = true;
                notice = "验证码已自动识别，可点击账号旁的“登录”。";
            });
    }

    private void loadServers(boolean resetPage) {
        if (resetPage) serverOffset = 0;
        String filter = versionFilter == null ? "" : versionFilter.getValue();
        runAsync("正在读取服务器列表...",
            () -> service.servers(serverOffset, MAX_VISIBLE_ROWS, filter), result -> {
            servers = result;
            page = Page.SERVERS;
            notice = "已载入 " + result.size() + " 个服务器。";
            rebuildWidgets();
        });
    }

    private void selectServer(FantnelClientService.Server server) {
        selectedServer = server;
        runAsync("正在读取角色...", () -> service.roles(server.id()), result -> {
            roles = result;
            page = Page.ROLES;
            notice = result.isEmpty() ? "该服务器暂无角色，请创建。" : "请选择角色。";
            rebuildWidgets();
        });
    }

    private void createRole() {
        if (selectedServer == null || newRole == null) return;
        runAsync("正在创建角色...", () -> service.createRole(selectedServer.id(), newRole.getValue()), ignored ->
            selectServer(selectedServer));
    }

    private void startProxyAndConnect() {
        if (selectedServer == null || selectedRole == null) return;
        Integer port = null;
        if (localPort != null && !localPort.getValue().isBlank()) {
            try {
                port = Integer.parseInt(localPort.getValue().trim());
            } catch (NumberFormatException error) {
                notice = "本地端口必须是 1-65535 的数字。";
                return;
            }
        }
        Integer requestedPort = port;
        runAsync("FantNEL 正在启动本地代理...",
            () -> service.startProxy(selectedServer.id(), selectedRole.name(), requestedPort), proxy -> {
                FantnelProxySession.activate(proxy);
                activeProxy = proxy;
                ServerData data = new ServerData(
                    selectedServer.name().isBlank() ? "FantNEL" : selectedServer.name(),
                    proxy.endpoint(), false);
                ConnectScreen.startConnecting(this, this.minecraft,
                    ServerAddress.parseString(proxy.endpoint()), data, false);
            });
    }

    private void stopProxy() {
        FantnelClientService.Proxy proxy = activeProxy;
        if (proxy == null) return;
        runAsync("正在停止代理...", FantnelProxySession::stopActive, ignored -> {
            activeProxy = null;
            notice = "代理已停止。";
            rebuildWidgets();
        });
    }

    private void refreshCurrent() {
        if (busy) return;
        switch (page) {
            case STARTING -> refreshStatus();
            case ACCOUNTS -> loadAccounts();
            case SERVERS -> loadServers(false);
            case ROLES -> {
                if (selectedServer != null) selectServer(selectedServer);
            }
            case CONNECT -> runAsync("正在检查 FantNEL Host...", service::status,
                ignored -> notice = "FantNEL Host 运行正常。");
        }
    }

    private void scheduleStartupRetry() {
        if (startupRetryScheduled) return;
        startupRetryScheduled = true;
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() ->
            Minecraft.getInstance().execute(() -> {
                startupRetryScheduled = false;
                if (this.minecraft.screen == this && page == Page.STARTING && !busy) refreshStatus();
            }));
    }

    private void onBack() {
        if (busy) return;
        switch (page) {
            case STARTING, ACCOUNTS -> this.minecraft.setScreen(parent);
            case SERVERS -> loadAccounts();
            case ROLES -> {
                page = Page.SERVERS;
                rebuildWidgets();
            }
            case CONNECT -> {
                page = Page.ROLES;
                rebuildWidgets();
            }
        }
    }

    private <T> void runAsync(String workingMessage, Supplier<CompletableFuture<T>> request,
                              Consumer<T> success) {
        if (busy) return;
        busy = true;
        notice = workingMessage;
        setWidgetsActive(false);
        CompletableFuture<T> future;
        try {
            future = Objects.requireNonNull(request.get(), "FantNEL request future");
        } catch (Throwable error) {
            busy = false;
            setWidgetsActive(true);
            notice = friendlyError(error);
            return;
        }
        future.whenComplete((value, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            setWidgetsActive(true);
            if (error != null) {
                notice = friendlyError(error);
                return;
            }
            try {
                success.accept(value);
            } catch (Exception callbackError) {
                notice = friendlyError(callbackError);
            }
        }));
    }

    private void setWidgetsActive(boolean active) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) widget.active = active;
        }
    }

    private void installCaptchaTexture(byte[] bytes) {
        releaseCaptchaTexture();
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            captchaWidth = image.getWidth();
            captchaHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            captchaTexture = this.minecraft.getTextureManager().register("fantnel-captcha", texture);
        } catch (Exception error) {
            throw new IllegalArgumentException("无法解析 FantNEL 验证码图片", error);
        }
    }

    private void releaseCaptchaTexture() {
        if (captchaTexture != null && this.minecraft != null) {
            this.minecraft.getTextureManager().release(captchaTexture);
            captchaTexture = null;
        }
    }

    @Override
    public void removed() {
        releaseCaptchaTexture();
        super.removed();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = (this.width - PANEL_WIDTH) / 2;
        graphics.fill(left - 10, 24, left + PANEL_WIDTH + 10, this.height - 40, 0xD0101010);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 32, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(page.title), this.width / 2, 46, 0xB8D8FF);

        if (page == Page.CONNECT) {
            String server = selectedServer == null ? "未选择服务器" : selectedServer.name();
            String role = selectedRole == null ? "未选择角色" : selectedRole.name();
            graphics.drawString(this.font, Component.literal("服务器: " + server), left, 72, 0xE0E0E0, false);
            graphics.drawString(this.font, Component.literal("角色: " + role), left, 88, 0xE0E0E0, false);
            if (activeProxy != null) {
                graphics.drawString(this.font, Component.literal("代理: " + activeProxy.endpoint()), left, 174, 0x80FF80, false);
            }
        }
        if (page == Page.ROLES && selectedServer != null) {
            graphics.drawString(this.font, Component.literal(selectedServer.name()), left, 56, 0xE0E0E0, false);
        }
        if (page == Page.ACCOUNTS && captchaTexture != null) {
            int drawWidth = Math.min(164, captchaWidth);
            int drawHeight = Math.max(24, (int) ((double) captchaHeight * drawWidth / Math.max(1, captchaWidth)));
            graphics.blit(captchaTexture, left + 266, 212, 0, 0, drawWidth, drawHeight,
                Math.max(1, captchaWidth), Math.max(1, captchaHeight));
        }

        graphics.drawCenteredString(this.font, Component.literal(notice), this.width / 2, this.height - 52,
            busy ? 0xFFE080 : 0xC8C8C8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String friendlyError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private enum Page {
        STARTING("启动中"),
        ACCOUNTS("账号"),
        SERVERS("服务器"),
        ROLES("角色"),
        CONNECT("连接");

        private final String title;

        Page(String title) {
            this.title = title;
        }
    }
}
