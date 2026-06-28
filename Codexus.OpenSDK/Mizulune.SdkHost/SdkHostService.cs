using System.Net;
using System.Text.Json;
using Codexus.ModHost;
using Codexus.ModHost.Event;
using Codexus.OpenSDK;
using Codexus.OpenSDK.Entities.MPay;
using Codexus.OpenSDK.Entities.X19;
using Codexus.OpenSDK.Entities.Yggdrasil;
using Codexus.OpenSDK.Exceptions;
using Codexus.OpenSDK.Yggdrasil;
using Codexus.OpenTransport.Entities.Transport;
using Codexus.OpenTransport.Event;
using Codexus.OpenTransport.Packet;
using Serilog;

namespace Mizulune.SdkHost;

internal sealed class SdkHostService : IAsyncDisposable
{
    private readonly IReadOnlyDictionary<string, ProxyProfile> _profiles;
    private readonly Func<string, object?, Task> _emit;
    private readonly ILogger _logger;
    private readonly ModManager _mods;
    private readonly Func<EventJoinServer, Task> _joinHandler;
    private readonly Action<EventProtocolRejected> _protocolRejectedHandler;
    private X19? _x19;
    private C4399? _c4399;
    private AuthenticatedSession? _auth;
    private global::Codexus.OpenTransport.OpenTransport? _transport;
    private ProxyProfile? _activeProfile;

    public SdkHostService(ILogger logger, Func<string, object?, Task> emit)
    {
        _logger = logger;
        _emit = emit;
        _profiles = ProxyProfile.LoadDirectory(Path.Combine(AppContext.BaseDirectory, "profiles"));
        _mods = new ModManager(logger, Path.Combine(AppContext.BaseDirectory, "Mods"));
        _mods.Initialize();
        _joinHandler = HandleJoinServerAsync;
        _protocolRejectedHandler = HandleProtocolRejected;
        EventBus.Instance.Subscribe(_joinHandler);
        EventBus.Instance.Subscribe(_protocolRejectedHandler);
    }

    public async Task<object?> HandleAsync(string method, JsonElement parameters)
    {
        return method switch
        {
            "sdk.status" or "proxy.status" => Status(),
            "profiles.list" => ListProfiles(),
            "auth.4399.password" => await Login4399Async(parameters),
            "auth.netease.email" => await LoginEmailAsync(parameters),
            "auth.netease.sms.send" => await SendSmsAsync(parameters),
            "auth.netease.sms.complete" => await CompleteSmsAsync(parameters),
            "auth.logout" => await LogoutAsync(),
            "session.prepare" => PrepareSession(parameters),
            "proxy.start" => await StartProxyAsync(parameters),
            "proxy.stop" => await StopProxyAsync(),
            _ => throw new HostCommandException("method_not_allowed", "Unknown SDK method.")
        };
    }

    private object Status() => new
    {
        version = 1,
        authenticated = _auth != null,
        provider = _auth?.Provider,
        account = _auth == null ? null : Mask(_auth.Otp.Account),
        proxy = new
        {
            running = _transport?.IsRunning == true,
            endpoint = EndpointText(_transport?.BoundLocalEndPoint),
            protocolVersion = _activeProfile?.ProtocolVersion
        },
        profiles = _profiles.Count
    };

    private object ListProfiles() => _profiles.Values.Select(profile => new
    {
        id = profile.Id,
        displayName = profile.DisplayName,
        protocolVersion = profile.ProtocolVersion,
        serverAddress = profile.ServerAddress,
        serverPort = profile.ServerPort,
        localPort = profile.LocalPort,
        viaForgeRequired = profile.ViaForgeRequired
    }).ToArray();

    private async Task<object> Login4399Async(JsonElement parameters)
    {
        await EnsureProxyStoppedForAuthAsync();
        var username = RequiredString(parameters, "username");
        var password = RequiredString(parameters, "password");
        try
        {
            ResetAuthClients();
            _c4399 = new C4399();
            _x19 = new X19();
            var cookie = await _c4399.LoginWithPasswordAsync(username, password);
            var wrapper = ParseSAuth(cookie);
            var authJson = ParseSAuthJson(wrapper);
            var (otp, channel) = await _x19.ContinueAsync(wrapper);
            SetAuthenticated("4399", otp, authJson, channel);
            return Status();
        }
        catch (VerifyException)
        {
            throw new HostCommandException("verification_required",
                "4399 requires captcha verification, which the current OpenSDK cannot complete.");
        }
        catch (HttpRequestException error)
        {
            throw MapAuthenticationError(error);
        }
        catch (InvalidOperationException error)
        {
            throw new HostCommandException("authentication_failed", error.Message);
        }
        catch (Exception error)
        {
            throw MapAuthenticationError(error);
        }
    }

    private async Task<object> LoginEmailAsync(JsonElement parameters)
    {
        await EnsureProxyStoppedForAuthAsync();
        var email = RequiredString(parameters, "email");
        var password = RequiredString(parameters, "password");
        ResetAuthClients();
        _x19 = new X19();
        var device = await _x19.InitializeDeviceAsync();
        var user = await _x19.LoginWithEmailAsync(email, password)
                   ?? throw new HostCommandException("authentication_failed", "Email login failed.");
        return await CompleteNeteaseAuthAsync("netease-email", user, device);
    }

    private async Task<object> SendSmsAsync(JsonElement parameters)
    {
        await EnsureProxyStoppedForAuthAsync();
        var phone = RequiredString(parameters, "phone");
        if (_x19 == null)
        {
            ResetAuthClients();
            _x19 = new X19();
        }
        await _x19.InitializeDeviceAsync();
        if (!await _x19.SendSmsCodeAsync(phone))
            throw new HostCommandException("sms_send_failed", "Failed to send the SMS code.");
        return new { sent = true };
    }

    private async Task<object> CompleteSmsAsync(JsonElement parameters)
    {
        await EnsureProxyStoppedForAuthAsync();
        var phone = RequiredString(parameters, "phone");
        var code = RequiredString(parameters, "code");
        _x19 ??= new X19();
        var device = await _x19.InitializeDeviceAsync();
        var ticket = await _x19.VerifySmsCodeAsync(phone, code)
                     ?? throw new HostCommandException("sms_verify_failed", "The SMS code is invalid or expired.");
        var user = await _x19.CompleteSmsLoginAsync(phone, ticket.Ticket)
                   ?? throw new HostCommandException("authentication_failed", "SMS login failed.");
        return await CompleteNeteaseAuthAsync("netease-sms", user, device);
    }

    private async Task<object> CompleteNeteaseAuthAsync(string provider, MPayUserWrapper user, MPayDevice device)
    {
        var authJson = new X19SAuthJson
        {
            SdkUid = user.User.Id,
            SessionId = user.User.Token,
            Udid = Guid.NewGuid().ToString("N").ToUpperInvariant(),
            DeviceId = device.Id
        };
        var wrapper = new X19SAuthJsonWrapper { Json = JsonSerializer.Serialize(authJson) };
        var (otp, channel) = await _x19!.ContinueAsync(wrapper);
        SetAuthenticated(provider, otp, authJson, channel);
        return Status();
    }

    private object PrepareSession(JsonElement parameters)
    {
        var auth = RequireAuth();
        var (profile, request) = BuildRequest(parameters);
        ApplySessionEnvironment(auth);
        EventBus.Instance.Publish(new EventPrepareTransport(BuildGameProfile(auth, profile), request));
        return new { prepared = true, profile = profile.Id, server = request.ServerAddress };
    }

    private async Task<object> StartProxyAsync(JsonElement parameters)
    {
        if (_transport?.IsRunning == true)
            throw new HostCommandException("proxy_already_running", "The proxy is already running.");
        var auth = RequireAuth();
        var (profile, request) = BuildRequest(parameters);
        ApplySessionEnvironment(auth);
        await X19.InterconnectionApi.GameStartAsync(auth.Otp.EntityId, auth.Otp.Token, profile.GameId);

        var transport = global::Codexus.OpenTransport.OpenTransport.Create(
            BuildGameProfile(auth, profile), request, _logger);
        var result = await transport.StartAsync();
        if (result.IsFailure)
        {
            await transport.CloseAsync();
            throw new HostCommandException("proxy_start_failed", result.Error ?? "Proxy startup failed.");
        }
        _transport = transport;
        _activeProfile = profile;
        var endpoint = EndpointText(transport.BoundLocalEndPoint);
        await _emit("proxy.started", new { endpoint, protocolVersion = profile.ProtocolVersion });
        return new { running = true, endpoint, protocolVersion = profile.ProtocolVersion };
    }

    private async Task<object> StopProxyAsync()
    {
        if (_transport != null)
        {
            await _transport.CloseAsync();
            _transport = null;
            _activeProfile = null;
            await _emit("proxy.stopped", null);
        }
        return new { running = false };
    }

    private async Task<object> LogoutAsync()
    {
        await StopProxyAsync();
        ResetAuthClients();
        ClearSessionEnvironment();
        return Status();
    }

    private async Task HandleJoinServerAsync(EventJoinServer e)
    {
        var auth = RequireAuth();
        var profile = _activeProfile ?? throw new HostCommandException("profile_unavailable", "No active proxy profile.");
        var yggdrasil = new StandardYggdrasil(new YggdrasilData
        {
            LauncherVersion = _x19?.GameVersion ?? profile.GameVersion,
            Channel = auth.Channel,
            CrcSalt = profile.CrcSalt
        });
        var result = await yggdrasil.JoinServerAsync(e.Context.Session.Profile, e.ServerId);
        if (result.IsFailure)
            throw new HostCommandException("join_server_failed", result.Error ?? "Yggdrasil join failed.");
    }

    private void HandleProtocolRejected(EventProtocolRejected e)
    {
        _emit("proxy.protocolRejected", new
        {
            actual = e.ActualProtocol,
            required = e.RequiredProtocol,
            message = "ViaForge must advertise Minecraft protocol 766 (1.20.5/1.20.6)."
        }).GetAwaiter().GetResult();
    }

    private (ProxyProfile Profile, CreateRequest Request) BuildRequest(JsonElement parameters)
    {
        var profileId = RequiredString(parameters, "presetId");
        if (!_profiles.TryGetValue(profileId, out var profile))
            throw new HostCommandException("profile_not_found", "The selected proxy profile does not exist.");
        var roleName = RequiredString(parameters, "roleName").Trim();
        if (roleName.Length > 16)
            throw new HostCommandException("invalid_request", "roleName must not exceed 16 characters.");
        var serverAddress = OptionalString(parameters, "serverAddress", profile.ServerAddress).Trim();
        if (serverAddress.Length == 0 || serverAddress.Any(char.IsWhiteSpace))
            throw new HostCommandException("invalid_request", "serverAddress is invalid.");
        var serverPort = OptionalInt(parameters, "serverPort", profile.ServerPort);
        var localPort = OptionalInt(parameters, "localPort", profile.LocalPort);
        ProxyProfile.ValidatePort(serverPort, "serverPort");
        ProxyProfile.ValidatePort(localPort, "localPort");
        return (profile, new CreateRequest
        {
            ServerAddress = serverAddress,
            ServerPort = serverPort,
            RoleName = roleName,
            Debug = false,
            LocalAddress = IPAddress.Loopback,
            LocalPort = localPort,
            RequiredProtocolVersion = EnumProtocolVersion.V1206
        });
    }

    private static GameProfile BuildGameProfile(AuthenticatedSession auth, ProxyProfile profile) => new()
    {
        GameId = profile.GameId,
        GameVersion = profile.GameVersion,
        BootstrapMd5 = profile.BootstrapMd5,
        DatFileMd5 = profile.DatFileMd5,
        Mods = new ModList(),
        User = new UserProfile
        {
            UserId = int.TryParse(auth.Otp.EntityId, out var id)
                ? id
                : throw new HostCommandException("invalid_identity", "The SDK returned a non-numeric entity id."),
            UserToken = auth.Otp.Token
        }
    };

    private void SetAuthenticated(string provider, X19AuthenticationOtp otp, X19SAuthJson metadata, string channel)
    {
        if (string.IsNullOrWhiteSpace(otp.EntityId) || string.IsNullOrWhiteSpace(otp.Token))
            throw new HostCommandException("authentication_failed", "The SDK returned an incomplete identity.");
        _auth = new AuthenticatedSession(provider, otp, metadata, channel);
        ApplySessionEnvironment(_auth);
    }

    private void ApplySessionEnvironment(AuthenticatedSession auth)
    {
        Environment.SetEnvironmentVariable("CODEXUS_ENTITY_ID", auth.Otp.EntityId);
        Environment.SetEnvironmentVariable("CODEXUS_SDK_UID",
            string.IsNullOrWhiteSpace(auth.Metadata.SdkUid) ? auth.Otp.SdkUid : auth.Metadata.SdkUid);
        Environment.SetEnvironmentVariable("CODEXUS_SESSION_ID", auth.Metadata.SessionId);
        Environment.SetEnvironmentVariable("CODEXUS_DEVICE_ID", auth.Metadata.DeviceId);
        Environment.SetEnvironmentVariable("MIZULUNE_LAUNCHER_VERSION", _x19?.GameVersion ?? string.Empty);
        Environment.SetEnvironmentVariable("MIZULUNE_PROTOCOL_DIRECTORY", MizuluneDirectory());
    }

    private static void ClearSessionEnvironment()
    {
        foreach (var name in new[]
                 {
                     "CODEXUS_ENTITY_ID", "CODEXUS_SDK_UID", "CODEXUS_SESSION_ID",
                     "CODEXUS_DEVICE_ID", "MIZULUNE_LAUNCHER_VERSION"
                 })
            Environment.SetEnvironmentVariable(name, null);
    }

    private AuthenticatedSession RequireAuth() => _auth
        ?? throw new HostCommandException("not_authenticated", "Log in before preparing a session or starting the proxy.");

    private async Task EnsureProxyStoppedForAuthAsync()
    {
        if (_transport?.IsRunning == true)
            throw new HostCommandException("proxy_running", "Stop the proxy before changing accounts.");
        await Task.CompletedTask;
    }

    private void ResetAuthClients()
    {
        _auth = null;
        ClearSessionEnvironment();
        _c4399?.Dispose();
        _x19?.Dispose();
        _c4399 = null;
        _x19 = null;
    }

    private static X19SAuthJsonWrapper ParseSAuth(string value)
    {
        try
        {
            return JsonSerializer.Deserialize<X19SAuthJsonWrapper>(value)
                   ?? new X19SAuthJsonWrapper { Json = value };
        }
        catch (JsonException)
        {
            return new X19SAuthJsonWrapper { Json = value };
        }
    }

    private static X19SAuthJson ParseSAuthJson(X19SAuthJsonWrapper wrapper) =>
        JsonSerializer.Deserialize<X19SAuthJson>(wrapper.Json)
        ?? throw new HostCommandException("authentication_failed", "The SDK returned malformed session metadata.");

    private static HostCommandException MapAuthenticationError(Exception error)
    {
        var message = error.Message ?? string.Empty;
        if (message.Contains("aid login limit", StringComparison.OrdinalIgnoreCase))
            return new HostCommandException("authentication_limited",
                "The account hit the upstream login limit. Retry later or switch account/device.");
        if (message.Contains("Parameter not found", StringComparison.OrdinalIgnoreCase))
            return new HostCommandException("authentication_response_invalid",
                "The 4399 login response is missing required fields for sAuth generation.");
        if (message.Contains("Authentication failed", StringComparison.OrdinalIgnoreCase))
            return new HostCommandException("authentication_failed", message);
        if (message.Contains("Failed to parse SDK response JSON", StringComparison.OrdinalIgnoreCase))
            return new HostCommandException("authentication_failed", message);
        return new HostCommandException("authentication_failed", message);
    }

    private static string RequiredString(JsonElement parameters, string name)
    {
        if (parameters.ValueKind != JsonValueKind.Object
            || !parameters.TryGetProperty(name, out var value)
            || value.ValueKind != JsonValueKind.String
            || string.IsNullOrWhiteSpace(value.GetString()))
            throw new HostCommandException("invalid_request", $"{name} is required.");
        return value.GetString()!;
    }

    private static string OptionalString(JsonElement parameters, string name, string fallback) =>
        parameters.ValueKind == JsonValueKind.Object
        && parameters.TryGetProperty(name, out var value)
        && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? fallback
            : fallback;

    private static int OptionalInt(JsonElement parameters, string name, int fallback) =>
        parameters.ValueKind == JsonValueKind.Object
        && parameters.TryGetProperty(name, out var value)
        && value.TryGetInt32(out var result)
            ? result
            : fallback;

    private static string EndpointText(IPEndPoint? endpoint)
    {
        if (endpoint == null) return string.Empty;
        var address = endpoint.Address.IsIPv4MappedToIPv6
            ? endpoint.Address.MapToIPv4()
            : endpoint.Address;
        return address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetworkV6
            ? $"[{address}]:{endpoint.Port}"
            : $"{address}:{endpoint.Port}";
    }

    private static string Mask(string value)
    {
        if (string.IsNullOrWhiteSpace(value)) return "authenticated";
        if (value.Length <= 2) return new string('*', value.Length);
        return value[0] + new string('*', Math.Min(8, value.Length - 2)) + value[^1];
    }

    internal static string MizuluneDirectory()
    {
        var root = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var path = Path.Combine(root, ".mizulune");
        Directory.CreateDirectory(path);
        return path;
    }

    public async ValueTask DisposeAsync()
    {
        EventBus.Instance.Unsubscribe(_joinHandler);
        EventBus.Instance.Unsubscribe(_protocolRejectedHandler);
        await StopProxyAsync();
        ResetAuthClients();
        ClearSessionEnvironment();
    }

    private sealed record AuthenticatedSession(
        string Provider,
        X19AuthenticationOtp Otp,
        X19SAuthJson Metadata,
        string Channel);
}
