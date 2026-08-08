using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mizulune.SdkHost;

internal sealed class ProxyProfile
{
    [JsonPropertyName("id")] public required string Id { get; init; }
    [JsonPropertyName("displayName")] public required string DisplayName { get; init; }
    [JsonPropertyName("protocolVersion")] public required int ProtocolVersion { get; init; }
    [JsonPropertyName("gameId")] public required string GameId { get; init; }
    [JsonPropertyName("gameVersion")] public required string GameVersion { get; init; }
    [JsonPropertyName("bootstrapMd5")] public required string BootstrapMd5 { get; init; }
    [JsonPropertyName("datFileMd5")] public required string DatFileMd5 { get; init; }
    [JsonPropertyName("crcSalt")] public required string CrcSalt { get; init; }
    [JsonPropertyName("serverAddress")] public required string ServerAddress { get; init; }
    [JsonPropertyName("serverPort")] public int ServerPort { get; init; } = 25565;
    [JsonPropertyName("localPort")] public int LocalPort { get; init; } = 6445;
    [JsonPropertyName("viaForgeRequired")] public bool ViaForgeRequired { get; init; } = true;

    public void Validate()
    {
        if (string.IsNullOrWhiteSpace(Id) || string.IsNullOrWhiteSpace(DisplayName))
            throw new InvalidDataException("Profile id/displayName is missing.");
        if (ProtocolVersion != 766)
            throw new InvalidDataException($"Profile {Id} must use protocol 766.");
        if (!GameId.All(char.IsDigit) || string.IsNullOrWhiteSpace(GameVersion))
            throw new InvalidDataException($"Profile {Id} game metadata is incomplete.");
        if (!IsMd5(BootstrapMd5) || !IsMd5(DatFileMd5) || !IsMd5(CrcSalt))
            throw new InvalidDataException($"Profile {Id} contains an invalid MD5/CRC value.");
        ValidatePort(ServerPort, nameof(ServerPort));
        ValidatePort(LocalPort, nameof(LocalPort));
    }

    public static IReadOnlyDictionary<string, ProxyProfile> LoadDirectory(string directory)
    {
        if (!Directory.Exists(directory)) return new Dictionary<string, ProxyProfile>();
        var profiles = new Dictionary<string, ProxyProfile>(StringComparer.OrdinalIgnoreCase);
        foreach (var path in Directory.GetFiles(directory, "*.json", SearchOption.TopDirectoryOnly))
        {
            var profile = JsonSerializer.Deserialize<ProxyProfile>(File.ReadAllText(path), JsonOptions)
                          ?? throw new InvalidDataException($"Cannot parse proxy profile: {path}");
            profile.Validate();
            if (!profiles.TryAdd(profile.Id, profile))
                throw new InvalidDataException($"Duplicate proxy profile id: {profile.Id}");
        }
        return profiles;
    }

    private static bool IsMd5(string value) => value.Length == 32 && value.All(Uri.IsHexDigit);

    internal static void ValidatePort(int port, string field)
    {
        if (port is < 1 or > 65535) throw new HostCommandException("invalid_request", $"{field} is invalid.");
    }

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };
}
