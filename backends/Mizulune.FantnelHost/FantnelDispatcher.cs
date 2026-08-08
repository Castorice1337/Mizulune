using System.Text.Json;
using Nirvana.Cipher.Cipher.Nirvana;
using Nirvana.Common;
using Nirvana.Common.Entities.Login;
using Nirvana.Common.Manager;
using Nirvana.Game.Launcher.Services.Java;
using Nirvana.Public.Entities.NEL;
using Nirvana.Public.Manager;
using Nirvana.Public.Message;
using Nirvana.WPFLauncher.Protocol;
using Serilog;

namespace Mizulune.FantnelHost;

internal sealed class FantnelDispatcher
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private static readonly Skip32Cipher RoleUuidCipher = new("SaintSteve"u8.ToArray());
    private readonly HostOptions options;
    private readonly JsonLineTransport transport;
    private readonly FantnelInitializationState initialization;
    private readonly HashSet<string> seenRequestIds = new(StringComparer.Ordinal);
    private readonly Dictionary<int, LauncherService> launchers = [];

    public FantnelDispatcher(HostOptions options, JsonLineTransport transport,
        FantnelInitializationState initialization)
    {
        this.options = options;
        this.transport = transport;
        this.initialization = initialization;
    }

    public object Status()
    {
        var account = initialization.IsReady ? TryCurrentAccount() : null;
        return new
        {
            backend = "fantnel",
            version = PublicProgram.Version,
            initialized = initialization.IsReady,
            initializing = initialization.IsInitializing,
            failed = initialization.IsFailed,
            error = initialization.IsFailed ? initialization.FailureMessage : null,
            authenticated = account is not null,
            account = account is null ? null : SafeAccount(account)
        };
    }

    public async Task RunAsync(Stream stream)
    {
        using var reader = new StreamReader(stream, new System.Text.UTF8Encoding(false), leaveOpen: true);
        while (true)
        {
            var line = await reader.ReadLineAsync();
            if (line is null) return;
            if (string.IsNullOrWhiteSpace(line)) continue;

            HostRequest? request;
            try
            {
                request = JsonSerializer.Deserialize<HostRequest>(line.TrimStart('\uFEFF'), JsonOptions);
            }
            catch (JsonException error)
            {
                await transport.SendErrorAsync(string.Empty, "invalid_json", error.Message);
                continue;
            }

            if (request is null || string.IsNullOrWhiteSpace(request.Id) || string.IsNullOrWhiteSpace(request.Method))
            {
                await transport.SendErrorAsync(request?.Id ?? string.Empty, "invalid_request", "Request id and method are required.");
                continue;
            }
            if (!seenRequestIds.Add(request.Id))
            {
                await transport.SendErrorAsync(request.Id, "duplicate_request_id", "The request id was already used.");
                continue;
            }

            try
            {
                var result = await DispatchAsync(request.Method, request.Params);
                var shuttingDown = request.Method.Equals("host.shutdown", StringComparison.Ordinal);
                try
                {
                    await transport.SendResultAsync(request.Id, result);
                }
                catch (IOException) when (shuttingDown)
                {
                    // The Java owner closes its pipe immediately during JVM
                    // teardown. A missing shutdown acknowledgement is benign.
                }
                if (shuttingDown) return;
            }
            catch (HostCommandException error)
            {
                await transport.SendErrorAsync(request.Id, error.Code, error.Message);
            }
            catch (Exception error)
            {
                var diagnosticId = Guid.NewGuid().ToString("N")[..12];
                HostBootstrapLog.Error(
                    $"Fantnel command failed: method={request.Method}, diagnosticId={diagnosticId}.",
                    error);
                await transport.SendErrorAsync(
                    request.Id,
                    "fantnel_error",
                    $"{SensitiveText.Redact(error.Message)} (diagnostic {diagnosticId})");
            }
        }
    }

    private async Task<object?> DispatchAsync(string method, JsonElement parameters)
    {
        // Status and shutdown must remain available while the frozen upstream
        // bootstrap is still running. All account/server/proxy operations are
        // gated so they cannot touch partially initialized FantNEL globals.
        if (!method.Equals("host.status", StringComparison.Ordinal)
            && !method.Equals("host.shutdown", StringComparison.Ordinal))
        {
            await initialization.EnsureReadyAsync();
        }

        switch (method)
        {
            case "host.status": return Status();
            case "host.shutdown": return new { stopped = true };
            case "account.list": return AccountMessage.GetAccountList().Select(SafeAccount).ToArray();
            case "account.available": return AccountMessage.GetLoginAccountList().Select(SafeAccount).ToArray();
            case "account.captcha.begin":
                AccountMessage.UpdateCaptcha();
                return new
                {
                    contentType = "image/png",
                    imageBase64 = Convert.ToBase64String(AccountMessage.Captcha4399Bytes ?? [])
                };
            case "account.captcha.submit":
                AccountMessage.Captcha4399 = RequiredString(parameters, "captcha");
                return new { accepted = true };
            case "account.captcha.auto":
                try
                {
                    if (AccountMessage.Captcha4399Bytes is null) AccountMessage.UpdateCaptcha();
                    AccountMessage.Captcha4399 = AccountMessage.GetCaptcha4399Content();
                    return new { accepted = true };
                }
                catch (HttpRequestException error) when ((int?)error.StatusCode >= 500)
                {
                    throw new HostCommandException(
                        "captcha_auto_unavailable",
                        "Fantnel 自动识别服务暂时不可用，请手动输入图片验证码。",
                        error);
                }
            case "account.save":
                AccountMessage.SaveAccount(AccountFrom(parameters));
                return new { saved = true };
            case "account.login.credentials":
            {
                var account = AccountFrom(parameters);
                AccountMessage.SaveAccount(account);
                var savedAccount = AccountMessage.GetAccountList().LastOrDefault();
                if (savedAccount?.Id is not int savedId)
                {
                    throw new HostCommandException(
                        "account_save_failed",
                        "FantNEL 保存账号后未能读取该账号。");
                }

                try
                {
                    AccountMessage.Login(savedId);
                    return Status();
                }
                catch
                {
                    // A manual login is one transaction: failed credentials/captcha must not
                    // leave another duplicate row behind for every retry.
                    try
                    {
                        AccountMessage.DeleteAccount(savedId);
                    }
                    catch (Exception rollbackError)
                    {
                        Log.Warning(rollbackError,
                            "Unable to roll back failed manual account login for account id {AccountId}",
                            savedId);
                    }
                    throw;
                }
            }
            case "account.update":
                AccountMessage.UpdateAccount(AccountFrom(parameters, RequiredInt(parameters, "id")));
                return new { updated = true };
            case "account.login":
                AccountMessage.Login(RequiredInt(parameters, "id"));
                return Status();
            case "account.switch":
                AccountMessage.SwitchAccount(RequiredInt(parameters, "id"));
                return Status();
            case "account.delete":
                AccountMessage.DeleteAccount(RequiredInt(parameters, "id"));
                return new { deleted = true };
            case "server.list":
            {
                var items = ServersGameMessage.GetServerListTo(
                    OptionalInt(parameters, "offset", 0),
                    OptionalInt(parameters, "pageSize", 20),
                    true,
                    OptionalString(parameters, "version") ?? string.Empty);
                return items.Select(item => new
                {
                    id = item.EntityId,
                    name = item.Name,
                    summary = item.BriefSummary,
                    onlineCount = item.OnlineCount,
                    version = item.Version,
                    titleImageUrl = item.TitleImageUrl
                }).ToArray();
            }
            case "server.detail":
            {
                var detail = await NPFLauncher.GetNetGameDetailByIdAsync(RequiredString(parameters, "gameId"));
                return new
                {
                    id = detail.EntityId,
                    name = detail.Name,
                    description = detail.DetailDescription,
                    developer = detail.DeveloperName,
                    versions = detail.McVersionList.Select(version => version.Name).ToArray(),
                    images = detail.BriefImageUrls
                };
            }
            case "server.roles":
            {
                var roles = await NPFLauncher.GetNetGameCharactersAsync(RequiredString(parameters, "gameId"));
                return roles.Select(role => new
                {
                    gameId = role.GameId,
                    userId = role.UserId,
                    name = role.Name,
                    createTime = role.CreateTime,
                    expireTime = role.ExpireTime
                }).ToArray();
            }
            case "server.role.create":
                await NPFLauncher.CreateCharacterAsync(
                    RequiredString(parameters, "gameId"),
                    RequiredString(parameters, "roleName"));
                return new { created = true };
            case "launch.start": return await StartLaunchAsync(parameters);
            case "launch.list": return launchers.Values.Select(SafeLauncher).ToArray();
            case "launch.stop":
            {
                var id = RequiredInt(parameters, "id");
                ActiveGameAndProxies.CloseGame(id);
                launchers.Remove(id);
                await transport.SendEventAsync("game.stopped", new { id });
                return new { stopped = true };
            }
            case "proxy.start": return await StartProxyAsync(parameters);
            case "proxy.list": return ActiveGameAndProxies.GetAllProxies().Select(SafeProxy).ToArray();
            case "proxy.stop":
            {
                var id = RequiredInt(parameters, "id");
                ActiveGameAndProxies.CloseProxy(id);
                await transport.SendEventAsync("proxy.stopped", new { id });
                return new { stopped = true };
            }
            case "session.prepare": return await PrepareSessionAsync(parameters);
            default: throw new HostCommandException("method_not_found", $"Unknown method: {method}");
        }
    }

    private async Task<object> StartLaunchAsync(JsonElement parameters)
    {
        var gameId = RequiredString(parameters, "gameId");
        var roleName = RequiredString(parameters, "roleName");
        var mode = OptionalString(parameters, "mode") ?? "net";
        var launcher = await LaunchMessage.LaunchGame(gameId, roleName, mode);
        launchers[launcher.Entity.Id] = launcher;
        var snapshot = WriteSession(
            launcher.Entity.RoleName,
            launcher.Entity.ServerIp,
            launcher.Entity.ServerPort,
            launcher.Entity.GameId);
        var result = new
        {
            id = launcher.Entity.Id,
            pid = launcher.GetPid(),
            gameId = launcher.Entity.GameId,
            roleName = launcher.Entity.RoleName,
            gameName = launcher.Entity.GameName,
            version = launcher.Entity.GameVersion,
            snapshotExpiresAt = snapshot.ExpiresAt
        };
        await transport.SendEventAsync("game.started", result);
        return result;
    }

    private async Task<object> StartProxyAsync(JsonElement parameters)
    {
        var gameId = RequiredString(parameters, "gameId");
        var roleName = RequiredString(parameters, "roleName");
        var mode = OptionalString(parameters, "mode") ?? "net";
        RunningProxy proxy;
        if (parameters.TryGetProperty("localPort", out var portElement) && portElement.ValueKind == JsonValueKind.Number)
            proxy = await ProxiesMessage.StartProxyAsyncTo(gameId, roleName, portElement.GetInt32(), mode);
        else
            proxy = (RunningProxy)await ProxiesMessage.StartProxyAsync(gameId, roleName, mode);

        ProtocolSnapshot? snapshot = null;
        if (mode.Equals("net", StringComparison.OrdinalIgnoreCase))
        {
            var address = await NPFLauncher.GetNetGameServerAddressAsync(gameId);
            snapshot = WriteSession(roleName, address.Host, address.Port, gameId);
        }
        var result = new
        {
            id = proxy.Id,
            localAddress = proxy.LocalAddress,
            localPort = proxy.LocalPort,
            endpoint = $"{proxy.LocalAddress}:{proxy.LocalPort}",
            serverName = proxy.ServerName,
            roleName = proxy.GetNickName(),
            snapshotExpiresAt = snapshot?.ExpiresAt
        };
        await transport.SendEventAsync("proxy.started", result);
        return result;
    }

    private async Task<object> PrepareSessionAsync(JsonElement parameters)
    {
        var gameId = RequiredString(parameters, "gameId");
        var roleName = RequiredString(parameters, "roleName");
        var address = await NPFLauncher.GetNetGameServerAddressAsync(gameId);
        var snapshot = WriteSession(roleName, address.Host, address.Port, gameId);
        return new { prepared = true, expiresAt = snapshot.ExpiresAt };
    }

    private ProtocolSnapshot WriteSession(string roleName, string serverAddress, int serverPort, string gameId)
    {
        var account = InfoManager.GetGameAccount();
        var userId = account.GetUserId();
        var roleUuid = RoleUuidCipher.GenerateRoleUuid(roleName, Convert.ToUInt32(userId));
        return ProtocolSnapshotWriter.Write(new ProtocolSnapshotInput(
            roleName,
            serverAddress,
            serverPort,
            userId,
            account.GetToken(),
            gameId,
            $"fantnel/{PublicProgram.Version}",
            roleUuid), options.ProtocolDirectory);
    }

    private static object SafeAccount(EntityAccount account) => new
    {
        id = account.Id,
        name = account.Name,
        account = MaskAccount(account.Account),
        type = account.Type,
        authenticated = account.IsNotNuLl(),
        userId = account.UserId
    };

    private static object SafeLauncher(LauncherService launcher) => new
    {
        id = launcher.Entity.Id,
        pid = launcher.GetPid(),
        running = launcher.IsRunning(),
        gameId = launcher.Entity.GameId,
        gameName = launcher.Entity.GameName,
        roleName = launcher.Entity.RoleName,
        version = launcher.Entity.GameVersion
    };

    private static object SafeProxy(RunningProxy proxy) => new
    {
        id = proxy.Id,
        localAddress = proxy.LocalAddress,
        localPort = proxy.LocalPort,
        endpoint = $"{proxy.LocalAddress}:{proxy.LocalPort}",
        serverName = proxy.ServerName,
        roleName = proxy.GetNickName()
    };

    private static EntityAccount? TryCurrentAccount()
    {
        try { return InfoManager.GetGameAccount(); }
        catch { return null; }
    }

    private static EntityAccount AccountFrom(JsonElement parameters, int? id = null) => new()
    {
        Id = id,
        Name = OptionalString(parameters, "name"),
        Account = OptionalString(parameters, "account"),
        Type = RequiredString(parameters, "type"),
        Password = RequiredString(parameters, "credential")
    };

    private static string? MaskAccount(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        if (value.Length <= 4) return new string('*', value.Length);
        return value[..2] + new string('*', Math.Min(8, value.Length - 4)) + value[^2..];
    }

    private static string RequiredString(JsonElement value, string name)
    {
        var result = OptionalString(value, name);
        return string.IsNullOrWhiteSpace(result)
            ? throw new HostCommandException("invalid_parameters", $"Missing parameter: {name}")
            : result;
    }

    private static string? OptionalString(JsonElement value, string name) =>
        value.ValueKind == JsonValueKind.Object && value.TryGetProperty(name, out var property)
            && property.ValueKind == JsonValueKind.String ? property.GetString() : null;

    private static int RequiredInt(JsonElement value, string name)
    {
        if (value.ValueKind == JsonValueKind.Object && value.TryGetProperty(name, out var property)
            && property.TryGetInt32(out var result)) return result;
        throw new HostCommandException("invalid_parameters", $"Missing parameter: {name}");
    }

    private static int OptionalInt(JsonElement value, string name, int fallback) =>
        value.ValueKind == JsonValueKind.Object && value.TryGetProperty(name, out var property)
            && property.TryGetInt32(out var result) ? result : fallback;
}

internal sealed class HostCommandException(string code, string message, Exception? innerException = null)
    : Exception(message, innerException)
{
    public string Code { get; } = code;
}
