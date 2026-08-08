using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Mizulune.FantnelHost;
using Xunit;

namespace Mizulune.FantnelHost.Tests;

public sealed class ProtocolSnapshotWriterTests : IDisposable
{
    private readonly string directory = Path.Combine(Path.GetTempPath(), "mizulune-fantnel-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void WritesSignedV2WithoutPlaintextToken()
    {
        var now = DateTimeOffset.Parse("2026-07-01T10:00:00Z");
        var snapshot = ProtocolSnapshotWriter.Write(new ProtocolSnapshotInput(
            "Player", "PC.BJDMC.NET:25565", 25565, "987654321012345678", "plain-secret-token",
            "game-id", "fantnel/1.7.0"), directory, now);

        var json = File.ReadAllText(Path.Combine(directory, "protocol-session.json"));
        Assert.DoesNotContain("plain-secret-token", json, StringComparison.Ordinal);
        using var document = JsonDocument.Parse(json);
        Assert.Equal(2, document.RootElement.GetProperty("version").GetInt32());
        Assert.Equal("fantnel", document.RootElement.GetProperty("source").GetString());
        Assert.Equal("987654321012345678", document.RootElement.GetProperty("userId").GetString());
        Assert.Equal("pc.bjdmc.net", document.RootElement.GetProperty("serverAddress").GetString());

        var key = Convert.FromBase64String(File.ReadAllText(Path.Combine(directory, "protocol-session.key"), Encoding.ASCII));
        var expected = Convert.ToBase64String(HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(ProtocolSnapshotWriter.Canonical(snapshot))));
        Assert.Equal(expected, snapshot.Signature);
        Assert.Equal(TimeSpan.FromHours(12), snapshot.ExpiresAt - snapshot.CreatedAt);
    }

    [Fact]
    public void RedactsCommonCredentialShapes()
    {
        var value = SensitiveText.Redact("password=hello token:world Authorization=BearerSecret bearer abcdef cookie\":\"json-secret");
        Assert.DoesNotContain("hello", value, StringComparison.Ordinal);
        Assert.DoesNotContain("world", value, StringComparison.Ordinal);
        Assert.DoesNotContain("abcdef", value, StringComparison.Ordinal);
        Assert.DoesNotContain("json-secret", value, StringComparison.Ordinal);
    }

    public void Dispose()
    {
        if (Directory.Exists(directory)) Directory.Delete(directory, true);
    }
}
