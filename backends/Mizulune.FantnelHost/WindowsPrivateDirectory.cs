using System.Diagnostics;
using System.Security.Principal;

namespace Mizulune.FantnelHost;

internal static class WindowsPrivateDirectory
{
    public static void TryRestrict(string path)
    {
        if (!OperatingSystem.IsWindows()) return;
        try
        {
            var sid = WindowsIdentity.GetCurrent().User?.Value;
            if (string.IsNullOrWhiteSpace(sid)) return;
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = "icacls.exe",
                UseShellExecute = false,
                CreateNoWindow = true,
                Arguments = $"\"{path}\" /inheritance:r /grant:r *{sid}:(OI)(CI)F"
            });
            process?.WaitForExit(5000);
        }
        catch
        {
            // ACL hardening is best-effort; the pipe still uses CurrentUserOnly.
        }
    }
}
