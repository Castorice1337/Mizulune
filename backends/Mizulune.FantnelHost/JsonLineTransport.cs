using System.Text.Json;

namespace Mizulune.FantnelHost;

internal sealed class JsonLineTransport
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly StreamWriter writer;
    private readonly SemaphoreSlim writeLock = new(1, 1);

    public JsonLineTransport(Stream stream)
    {
        writer = new StreamWriter(stream, new System.Text.UTF8Encoding(false), leaveOpen: true)
        {
            AutoFlush = true,
            NewLine = "\n"
        };
    }

    public Task SendEventAsync(string name, object data) =>
        WriteAsync(new { @event = name, data });

    public Task SendResultAsync(string id, object? result) =>
        WriteAsync(new { id, ok = true, result });

    public Task SendErrorAsync(string id, string code, string message) =>
        WriteAsync(new { id, ok = false, error = new { code, message = SensitiveText.Redact(message) } });

    private async Task WriteAsync(object message)
    {
        var line = JsonSerializer.Serialize(message, JsonOptions);
        await writeLock.WaitAsync();
        try
        {
            await writer.WriteLineAsync(line);
        }
        finally
        {
            writeLock.Release();
        }
    }
}

internal sealed record HostRequest(string Id, string Method, JsonElement Params);

internal static class SensitiveText
{
    public static string Redact(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return "Fantnel operation failed.";
        var text = value.Replace("\r", " ").Replace("\n", " ").Trim();
        text = System.Text.RegularExpressions.Regex.Replace(
            text,
            "(?i)(password|token|cookie|credential|authorization)[\\\"']?(\\s*[:=]\\s*)[\\\"']?([^,;\\s\\\"']+)",
            "$1$2[redacted]");
        text = System.Text.RegularExpressions.Regex.Replace(text, "(?i)bearer\\s+[^,;\\s]+", "Bearer [redacted]");
        return text.Length > 500 ? text[..500] : text;
    }
}
