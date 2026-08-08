using System.Text;
using System.Text.Json;
using Mizulune.FantnelHost;
using Xunit;

namespace Mizulune.FantnelHost.Tests;

public sealed class JsonLineTransportTests
{
    [Fact]
    public async Task EmitsSingleLineUtf8JsonWithoutCredentialEcho()
    {
        await using var stream = new MemoryStream();
        var transport = new JsonLineTransport(stream);

        await transport.SendResultAsync("mc-1", new { accepted = true });
        stream.Position = 0;
        var line = await new StreamReader(stream, new UTF8Encoding(false), leaveOpen: true)
            .ReadLineAsync(TestContext.Current.CancellationToken);

        Assert.NotNull(line);
        Assert.DoesNotContain("credential-secret", line, StringComparison.Ordinal);
        using var document = JsonDocument.Parse(line!);
        Assert.Equal("mc-1", document.RootElement.GetProperty("id").GetString());
        Assert.True(document.RootElement.GetProperty("ok").GetBoolean());
        Assert.True(document.RootElement.GetProperty("result").GetProperty("accepted").GetBoolean());
    }

    [Fact]
    public async Task RedactsCredentialLabelsInErrors()
    {
        await using var stream = new MemoryStream();
        var transport = new JsonLineTransport(stream);

        await transport.SendErrorAsync("mc-2", "login_failed", "credential=credential-secret");
        stream.Position = 0;
        var line = await new StreamReader(stream, new UTF8Encoding(false), leaveOpen: true)
            .ReadLineAsync(TestContext.Current.CancellationToken);

        Assert.NotNull(line);
        Assert.DoesNotContain("credential-secret", line, StringComparison.Ordinal);
        Assert.Contains("[redacted]", line, StringComparison.Ordinal);
    }
}
