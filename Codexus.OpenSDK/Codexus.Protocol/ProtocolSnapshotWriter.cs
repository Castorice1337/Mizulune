using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Codexus.Protocol;

internal static class ProtocolSnapshotWriter
{
    private const string SnapshotName = "protocol-session.json";
    private const string KeyName = "protocol-session.key";
    private static readonly TimeSpan Lifetime = TimeSpan.FromMinutes(10);

    public static void Write(ProtocolSnapshotInput input, string? outputDirectory = null, DateTimeOffset? now = null)
    {
        var directory = outputDirectory ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".mizulune");
        Directory.CreateDirectory(directory);
        var key = LoadOrCreateKey(Path.Combine(directory, KeyName));
        var createdMs = (now ?? DateTimeOffset.UtcNow).ToUnixTimeMilliseconds();
        var expiresMs = createdMs + (long)Lifetime.TotalMilliseconds;
        var snapshot = new ProtocolSessionSnapshot
        {
            RoleName = input.RoleName,
            ServerAddress = NormalizeHost(input.ServerAddress),
            ServerPort = input.ServerPort,
            UserId = input.UserId,
            UserTokenHash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(input.UserToken))).ToLowerInvariant(),
            EntityId = input.EntityId,
            SdkUid = input.SdkUid,
            SessionId = input.SessionId,
            DeviceId = input.DeviceId,
            GameId = input.GameId,
            LauncherVersion = input.LauncherVersion,
            CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(createdMs),
            ExpiresAt = DateTimeOffset.FromUnixTimeMilliseconds(expiresMs)
        };
        snapshot.Signature = Convert.ToBase64String(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(Canonical(snapshot))));

        var target = Path.Combine(directory, SnapshotName);
        var temporary = target + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(snapshot, SerializerOptions), Encoding.UTF8);
        File.Move(temporary, target, true);
    }

    private static byte[] LoadOrCreateKey(string path)
    {
        if (File.Exists(path)) return Convert.FromBase64String(File.ReadAllText(path, Encoding.ASCII).Trim());
        var key = RandomNumberGenerator.GetBytes(32);
        File.WriteAllText(path, Convert.ToBase64String(key), Encoding.ASCII);
        return key;
    }

    internal static string Canonical(ProtocolSessionSnapshot value)
    {
        return string.Join('\n',
            value.RoleName,
            NormalizeHost(value.ServerAddress),
            value.ServerPort.ToString(System.Globalization.CultureInfo.InvariantCulture),
            value.UserId.ToString(System.Globalization.CultureInfo.InvariantCulture),
            value.UserTokenHash,
            value.EntityId,
            value.SdkUid,
            value.SessionId,
            value.DeviceId,
            value.GameId,
            value.LauncherVersion,
            value.CreatedAt.ToUnixTimeMilliseconds().ToString(System.Globalization.CultureInfo.InvariantCulture),
            value.ExpiresAt.ToUnixTimeMilliseconds().ToString(System.Globalization.CultureInfo.InvariantCulture)
        );
    }

    private static string NormalizeHost(string value)
    {
        var host = value.Trim().ToLowerInvariant();
        var colon = host.LastIndexOf(':');
        if (colon > 0 && host.IndexOf(':') == colon && int.TryParse(host[(colon + 1)..], out _))
            host = host[..colon];
        return host.EndsWith('.') ? host[..^1] : host;
    }

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = true
    };
}

internal sealed class ProtocolSessionSnapshot
{
    [JsonPropertyName("roleName")] public required string RoleName { get; init; }
    [JsonPropertyName("serverAddress")] public required string ServerAddress { get; init; }
    [JsonPropertyName("serverPort")] public required int ServerPort { get; init; }
    [JsonPropertyName("userId")] public required int UserId { get; init; }
    [JsonPropertyName("userTokenHash")] public required string UserTokenHash { get; init; }
    [JsonPropertyName("entityId")] public required string EntityId { get; init; }
    [JsonPropertyName("sdkUid")] public required string SdkUid { get; init; }
    [JsonPropertyName("sessionId")] public required string SessionId { get; init; }
    [JsonPropertyName("deviceId")] public required string DeviceId { get; init; }
    [JsonPropertyName("gameId")] public required string GameId { get; init; }
    [JsonPropertyName("launcherVersion")] public required string LauncherVersion { get; init; }
    [JsonPropertyName("createdAt")] public required DateTimeOffset CreatedAt { get; init; }
    [JsonPropertyName("expiresAt")] public required DateTimeOffset ExpiresAt { get; init; }
    [JsonPropertyName("signature")] public string Signature { get; set; } = string.Empty;
}
