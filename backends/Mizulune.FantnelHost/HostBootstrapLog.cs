using System.Text;

namespace Mizulune.FantnelHost;

internal static class HostBootstrapLog
{
    private static readonly object Sync = new();
    private static string? path;

    public static void Initialize(string protocolDirectory)
    {
        try
        {
            var directory = Path.Combine(protocolDirectory, "logs");
            Directory.CreateDirectory(directory);
            path = Path.Combine(directory, "fantnel-host-bootstrap.log");
            Info("Host process entered Main.");
        }
        catch
        {
            path = null;
        }
    }

    public static void Info(string message) => Write("INFO", message);

    public static void Warning(string message) => Write("WARN", message);

    public static void Error(string message, Exception error)
    {
        Write("ERROR", $"{message}{Environment.NewLine}{SensitiveText.Redact(error.ToString())}");
    }

    private static void Write(string level, string message)
    {
        var target = path;
        if (target is null) return;
        try
        {
            lock (Sync)
            {
                File.AppendAllText(
                    target,
                    $"{DateTimeOffset.Now:O} [{level}] {message}{Environment.NewLine}",
                    new UTF8Encoding(false));
            }
        }
        catch
        {
            // Bootstrap logging must never prevent the backend from starting.
        }
    }
}
