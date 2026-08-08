using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mizulune.FantnelHost;

internal static class ProtocolSnapshotWriter
{
    // The snapshot gates an active game/proxy session; ten minutes expired during normal minigame play.
    private static readonly TimeSpan Lifetime = TimeSpan.FromHours(12);

    public static ProtocolSnapshot Write(ProtocolSnapshotInput input, string directory, DateTimeOffset? now = null)
    {
        Directory.CreateDirectory(directory);
        var key = LoadOrCreateKey(Path.Combine(directory, "protocol-session.key"));
        var created = now ?? DateTimeOffset.UtcNow;
        var snapshot = new ProtocolSnapshot
        {
            Version = 2,
            Source = "fantnel",
            RoleName = input.RoleName,
            ServerAddress = NormalizeHost(input.ServerAddress),
            ServerPort = input.ServerPort,
            UserId = input.UserId,
            UserTokenHash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(input.UserToken))).ToLowerInvariant(),
            EntityId = input.EntityId ?? string.Empty,
            SdkUid = input.SdkUid ?? string.Empty,
            SessionId = input.SessionId ?? string.Empty,
            DeviceId = input.DeviceId ?? string.Empty,
            GameId = input.GameId,
            LauncherVersion = input.LauncherVersion,
            CreatedAt = created,
            ExpiresAt = created.Add(Lifetime)
        };
        snapshot.Signature = Convert.ToBase64String(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(Canonical(snapshot))));

        var target = Path.Combine(directory, "protocol-session.json");
        var temporary = target + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(snapshot, JsonOptions), new UTF8Encoding(false));
        File.Move(temporary, target, true);
        return snapshot;
    }

    internal static string Canonical(ProtocolSnapshot value) => string.Join('\n',
        value.Version.ToString(System.Globalization.CultureInfo.InvariantCulture),
        value.Source,
        value.RoleName,
        NormalizeHost(value.ServerAddress),
        value.ServerPort.ToString(System.Globalization.CultureInfo.InvariantCulture),
        value.UserId,
        value.UserTokenHash,
        value.EntityId,
        value.SdkUid,
        value.SessionId,
        value.DeviceId,
        value.GameId,
        value.LauncherVersion,
        value.CreatedAt.ToUnixTimeMilliseconds().ToString(System.Globalization.CultureInfo.InvariantCulture),
        value.ExpiresAt.ToUnixTimeMilliseconds().ToString(System.Globalization.CultureInfo.InvariantCulture));

    private static byte[] LoadOrCreateKey(string path)
    {
        if (File.Exists(path)) return Convert.FromBase64String(File.ReadAllText(path, Encoding.ASCII).Trim());
        var key = RandomNumberGenerator.GetBytes(32);
        File.WriteAllText(path, Convert.ToBase64String(key), Encoding.ASCII);
        return key;
    }

    internal static string NormalizeHost(string value)
    {
        var host = value.Trim().ToLowerInvariant();
        var colon = host.LastIndexOf(':');
        if (colon > 0 && host.IndexOf(':') == colon && int.TryParse(host[(colon + 1)..], out _)) host = host[..colon];
        return host.EndsWith('.') ? host[..^1] : host;
    }

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true
    };
}

internal sealed record ProtocolSnapshotInput(
    string RoleName,
    string ServerAddress,
    int ServerPort,
    string UserId,
    string UserToken,
    string GameId,
    string LauncherVersion,
    string? EntityId = null,
    string? SdkUid = null,
    string? SessionId = null,
    string? DeviceId = null);

internal sealed class ProtocolSnapshot
{
    [JsonPropertyName("version")] public int Version { get; init; }
    [JsonPropertyName("source")] public required string Source { get; init; }
    [JsonPropertyName("roleName")] public required string RoleName { get; init; }
    [JsonPropertyName("serverAddress")] public required string ServerAddress { get; init; }
    [JsonPropertyName("serverPort")] public int ServerPort { get; init; }
    [JsonPropertyName("userId")] public required string UserId { get; init; }
    [JsonPropertyName("userTokenHash")] public required string UserTokenHash { get; init; }
    [JsonPropertyName("entityId")] public required string EntityId { get; init; }
    [JsonPropertyName("sdkUid")] public required string SdkUid { get; init; }
    [JsonPropertyName("sessionId")] public required string SessionId { get; init; }
    [JsonPropertyName("deviceId")] public required string DeviceId { get; init; }
    [JsonPropertyName("gameId")] public required string GameId { get; init; }
    [JsonPropertyName("launcherVersion")] public required string LauncherVersion { get; init; }
    [JsonPropertyName("createdAt")] public DateTimeOffset CreatedAt { get; init; }
    [JsonPropertyName("expiresAt")] public DateTimeOffset ExpiresAt { get; init; }
    [JsonPropertyName("signature")] public string Signature { get; set; } = string.Empty;
}
