using System.IO.Pipes;
using System.Text;
using Nirvana.Common.Manager;
using Nirvana.Public.Manager;
using Nirvana.Public.Utils;
using Nirvana.Public.Utils.ViewLogger;

namespace Mizulune.FantnelHost;

internal static class Program
{
    public static async Task<int> Main(string[] args)
    {
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
        if (args.Contains("--health", StringComparer.OrdinalIgnoreCase))
        {
            Console.WriteLine("{\"ok\":true,\"backend\":\"fantnel\",\"protocol\":1}");
            return 0;
        }
        if (args.Contains("--console-health", StringComparer.OrdinalIgnoreCase))
        {
            HiddenConsoleSession.EnsureAvailable();
            Console.Clear();
            return Console.BufferWidth > 0 ? 0 : 1;
        }

        var options = HostOptions.Parse(args);
        if (string.IsNullOrWhiteSpace(options.PipeName))
        {
            Console.Error.WriteLine("Missing required --pipe argument.");
            return 2;
        }

        HostBootstrapLog.Initialize(options.ProtocolDirectory);
        try
        {
            return await RunAsync(options);
        }
        catch (Exception error)
        {
            HostBootstrapLog.Error("Host startup failed before a stable control session was established.", error);
            Console.Error.WriteLine($"{error.GetType().FullName}: {SensitiveText.Redact(error.Message)}");
            return 1;
        }
    }

    private static async Task<int> RunAsync(HostOptions options)
    {
        Directory.CreateDirectory(options.StateDirectory);
        Directory.CreateDirectory(options.ProtocolDirectory);
        WindowsPrivateDirectory.TryRestrict(options.StateDirectory);
        WindowsPrivateDirectory.TryRestrict(options.ProtocolDirectory);

        // Fantnel's frozen console logger and launch progress code require a real
        // console buffer even when the Loader starts this process without a window.
        HiddenConsoleSession.EnsureAvailable();
        HostBootstrapLog.Info("Hidden console buffer initialized.");

        await using var pipe = new NamedPipeServerStream(
            options.PipeName,
            PipeDirection.InOut,
            1,
            PipeTransmissionMode.Byte,
            PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
        HostBootstrapLog.Info("Named pipe created; waiting for Loader connection.");
        await pipe.WaitForConnectionAsync();

        var transport = new JsonLineTransport(pipe);
        // Do not emit a server-first event here. The Java client opens this pipe
        // with RandomAccessFile and uses serialized write-then-read transactions;
        // writing during the open handshake can still deadlock both sides.
        // host.status is the first client request, while ready and failed remain
        // normal post-handshake events.

        try
        {
            var initialization = new FantnelInitializationState();
            var dispatcher = new FantnelDispatcher(options, transport, initialization);

            // Keep the control loop responsive during frozen bootstrap. The
            // previous sequential flow made the Java UI wait forever whenever
            // an upstream update check or service initializer stalled.
            _ = Task.Run(async () =>
            {
                try
                {
                    HostBootstrapLog.Info("Starting frozen Fantnel initialization sequence.");
                    InitializeFantnel(options);
                    initialization.MarkReady();
                    await transport.SendEventAsync("host.ready", dispatcher.Status());
                    HostBootstrapLog.Info("Fantnel Host is ready.");
                }
                catch (Exception error)
                {
                    initialization.MarkFailed(error);
                    HostBootstrapLog.Error("Fantnel initialization failed after the control session was established.", error);
                    try
                    {
                        await transport.SendEventAsync("host.failed", new
                        {
                            code = "fantnel_initialization_failed",
                            message = SensitiveText.Redact(error.Message)
                        });
                    }
                    catch
                    {
                        // The client may have closed the pipe while bootstrap
                        // was failing; the bootstrap log remains authoritative.
                    }
                }
            });
            await dispatcher.RunAsync(pipe);
            return 0;
        }
        catch (Exception error)
        {
            HostBootstrapLog.Error("Fantnel initialization or request loop failed.", error);
            try
            {
                await transport.SendEventAsync("host.failed", new
                {
                    code = "fantnel_initialization_failed",
                    message = SensitiveText.Redact(error.Message)
                });
            }
            catch
            {
                // The pipe may already be gone; the redacted bootstrap log is
                // the fallback diagnostic channel.
            }
            Console.Error.WriteLine($"{error.GetType().FullName}: {SensitiveText.Redact(error.Message)}");
            return 1;
        }
        finally
        {
            try
            {
                foreach (var proxy in ActiveGameAndProxies.GetAllProxies().ToArray())
                {
                    try { proxy.Shutdown(); }
                    catch (Exception error)
                    {
                        HostBootstrapLog.Warning(
                            $"Fantnel proxy cleanup failed ({error.GetType().Name}).");
                    }
                }
            }
            catch (Exception error)
            {
                // Early shutdown is valid while frozen initialization is still
                // in flight, before its global proxy registry is available.
                HostBootstrapLog.Warning(
                    $"Fantnel proxy registry was unavailable during shutdown ({error.GetType().Name}).");
            }
        }
    }

    private static void InitializeFantnel(HostOptions options)
    {
        Logger.LogoInit();
        var fantnelArgs = new List<string>
        {
            "--MainPid", options.ParentPid.ToString(System.Globalization.CultureInfo.InvariantCulture),
            "--update_false",
            "--update_ui_false",
            "--update_static_false",
            "--update_static_system_false",
            "--update_static_linux_system_false"
        };
        HostBootstrapLog.Info("Resolving Fantnel bootstrap metadata.");
        InfoManager.FantnelInfo = FantnelBootstrapMetadata.Resolve(options.StateDirectory);
        HostBootstrapLog.Info("Running frozen Fantnel update/version checks with runtime updates disabled.");
        InitProgram.CheckUpdate(fantnelArgs.ToArray(), Logger.LogoInit);
        HostBootstrapLog.Info("Running frozen Fantnel restart dispatcher.");
        if (!RestartTools.Main(fantnelArgs.ToArray(), Logger.LogoInit))
            throw new InvalidOperationException("Fantnel entered a restart-only mode.");
        HostBootstrapLog.Info("Running frozen Fantnel service initialization.");
        InitProgram.NelInit1(fantnelArgs.ToArray());
    }
}

internal sealed record HostOptions(string PipeName, int ParentPid, string StateDirectory, string ProtocolDirectory)
{
    public static HostOptions Parse(string[] args)
    {
        string? Value(string name)
        {
            var index = Array.FindIndex(args, value => value.Equals(name, StringComparison.OrdinalIgnoreCase));
            return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
        }

        var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var state = Value("--state-dir") ?? Path.Combine(home, ".mizulune", "backends", "fantnel");
        var protocol = Value("--protocol-dir") ?? Path.Combine(home, ".mizulune");
        _ = int.TryParse(Value("--parent-pid"), out var parentPid);
        return new HostOptions(Value("--pipe") ?? string.Empty, parentPid, state, protocol);
    }
}
