using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace Mizulune.FantnelHost;

internal static class HiddenConsoleSession
{
    private const int StdInputHandle = -10;
    private const int StdOutputHandle = -11;
    private const int StdErrorHandle = -12;
    private const uint GenericRead = 0x80000000;
    private const uint GenericWrite = 0x40000000;
    private const uint ShareRead = 0x00000001;
    private const uint ShareWrite = 0x00000002;
    private const uint OpenExisting = 3;
    private const int SwHide = 0;

    private static readonly object Sync = new();
    private static bool initialized;
    private static SafeFileHandle? consoleInput;
    private static SafeFileHandle? consoleOutput;
    private static SafeFileHandle? consoleError;

    public static void EnsureAvailable()
    {
        if (!OperatingSystem.IsWindows() || initialized) return;

        lock (Sync)
        {
            if (initialized) return;
            var allocatedConsole = false;
            if (GetConsoleWindow() == IntPtr.Zero)
            {
                // CREATE_NO_WINDOW can leave a console-subsystem process attached
                // to a headless console object. AllocConsole then fails with
                // ERROR_ACCESS_DENIED until that object is detached.
                _ = FreeConsole();
                if (!AllocConsole())
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Unable to allocate the Fantnel console buffer.");
                allocatedConsole = true;
            }

            var consoleWindow = GetConsoleWindow();
            if (allocatedConsole && consoleWindow != IntPtr.Zero) _ = ShowWindow(consoleWindow, SwHide);

            consoleInput = OpenConsole("CONIN$", GenericRead | GenericWrite);
            consoleOutput = OpenConsole("CONOUT$", GenericRead | GenericWrite);
            consoleError = OpenConsole("CONOUT$", GenericRead | GenericWrite);

            SetStandardHandle(StdInputHandle, consoleInput);
            SetStandardHandle(StdOutputHandle, consoleOutput);
            SetStandardHandle(StdErrorHandle, consoleError);

            Console.SetIn(new StreamReader(Console.OpenStandardInput(), Console.InputEncoding, false, 1024, true));
            Console.SetOut(new StreamWriter(Console.OpenStandardOutput(), Console.OutputEncoding, 1024, true) { AutoFlush = true });
            Console.SetError(new StreamWriter(Console.OpenStandardError(), Console.OutputEncoding, 1024, true) { AutoFlush = true });
            initialized = true;
        }
    }

    private static SafeFileHandle OpenConsole(string name, uint access)
    {
        var handle = CreateFile(name, access, ShareRead | ShareWrite, IntPtr.Zero, OpenExisting, 0, IntPtr.Zero);
        if (handle.IsInvalid)
            throw new Win32Exception(Marshal.GetLastWin32Error(), $"Unable to open {name}.");
        return handle;
    }

    private static void SetStandardHandle(int kind, SafeFileHandle handle)
    {
        if (!SetStdHandle(kind, handle.DangerousGetHandle()))
            throw new Win32Exception(Marshal.GetLastWin32Error(), $"Unable to bind standard handle {kind}.");
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AllocConsole();

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool FreeConsole();

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetConsoleWindow();

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr window, int command);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFile(
        string fileName,
        uint desiredAccess,
        uint shareMode,
        IntPtr securityAttributes,
        uint creationDisposition,
        uint flagsAndAttributes,
        IntPtr templateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetStdHandle(int stdHandle, IntPtr handle);
}
