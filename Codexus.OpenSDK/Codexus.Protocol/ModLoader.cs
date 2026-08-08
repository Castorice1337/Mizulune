using Codexus.ModSDK;
using Codexus.OpenSDK.Entities.Yggdrasil;
using Codexus.OpenTransport.Entities.Transport;
using Codexus.OpenTransport.Event;
using Codexus.OpenTransport.Registry;

namespace Codexus.Protocol;

public sealed class ModLoader : IMod
{
    private IModContext? _context;
    private RegistryScope? _proxyScope;

    public void OnLoad(IModContext context)
    {
        _context = context;
        context.EventBus.Subscribe<EventCreateTransport>(HandleTransport);
        context.EventBus.Subscribe<EventJoinServer>(HandleJoinServer);
        context.EventBus.Subscribe<EventPrepareTransport>(HandlePrepareTransport);
        context.LogInformation("Mizulune protocol session plugin loaded.");
    }

    public void OnUnload()
    {
        if (_context == null) return;
        _context.EventBus.Unsubscribe<EventCreateTransport>(HandleTransport);
        _context.EventBus.Unsubscribe<EventJoinServer>(HandleJoinServer);
        _context.EventBus.Unsubscribe<EventPrepareTransport>(HandlePrepareTransport);
        _proxyScope?.Restore();
        _proxyScope = null;
        _context = null;
    }

    private void HandleTransport(EventCreateTransport e)
    {
        _proxyScope?.Restore();
        _proxyScope = e.Transport.Registry.ApplyRegistry(
            new ProxyProtocolSupport1206(_context!, e.Transport.Request));
        WriteSnapshot(e.Transport.Profile, e.Transport.Request);
    }

    private void HandlePrepareTransport(EventPrepareTransport e) => WriteSnapshot(e.Profile, e.Request);

    private void HandleJoinServer(EventJoinServer e)
    {
        var session = e.Context.Session;
        WriteSnapshot(session.Profile, session.Request);
    }

    private void WriteSnapshot(GameProfile profile, CreateRequest request) => WriteSnapshot(
        request.RoleName,
        request.ServerAddress,
        request.ServerPort,
        profile.User.UserId,
        profile.User.UserToken,
        profile.GameId,
        profile.GameVersion);

    private void WriteSnapshot(string roleName, string serverAddress, int serverPort, int userId,
        string userToken, string gameId, string gameVersion)
    {
        try
        {
            ProtocolSnapshotWriter.Write(new ProtocolSnapshotInput(
                roleName,
                serverAddress,
                serverPort,
                userId,
                userToken,
                Environment.GetEnvironmentVariable("CODEXUS_ENTITY_ID") ?? string.Empty,
                Environment.GetEnvironmentVariable("CODEXUS_SDK_UID") ?? string.Empty,
                Environment.GetEnvironmentVariable("CODEXUS_SESSION_ID") ?? string.Empty,
                Environment.GetEnvironmentVariable("CODEXUS_DEVICE_ID") ?? string.Empty,
                gameId,
                Environment.GetEnvironmentVariable("MIZULUNE_LAUNCHER_VERSION") ?? gameVersion
            ), Environment.GetEnvironmentVariable("MIZULUNE_PROTOCOL_DIRECTORY"));
            _context?.LogDebug("Protocol session snapshot updated for {0}:{1}.", serverAddress, serverPort);
        }
        catch (Exception error)
        {
            _context?.LogError(error, "Failed to write protocol session snapshot.");
        }
    }
}

internal sealed record ProtocolSnapshotInput(
    string RoleName,
    string ServerAddress,
    int ServerPort,
    int UserId,
    string UserToken,
    string EntityId,
    string SdkUid,
    string SessionId,
    string DeviceId,
    string GameId,
    string LauncherVersion
);
