using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Net;
using System.Net.Sockets;
using Codexus.ModHost;
using Codexus.OpenSDK.Entities.Yggdrasil;
using Codexus.OpenTransport;
using Codexus.OpenTransport.Entities.Transport;
using Codexus.OpenTransport.Packet;
using Serilog;
using Xunit;

namespace Codexus.Protocol.Tests;

public sealed class ProtocolSnapshotWriterTests
{
    [Fact]
    public void CanonicalHmacMatchesJavaGolden()
    {
        var snapshot = new ProtocolSessionSnapshot
        {
            RoleName = "Player",
            ServerAddress = "pc.bjdmc.net",
            ServerPort = 25565,
            UserId = 42,
            UserTokenHash = "token-hash",
            EntityId = "",
            SdkUid = "",
            SessionId = "",
            DeviceId = "",
            GameId = "x19",
            LauncherVersion = "1.0",
            CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(1782554400000),
            ExpiresAt = DateTimeOffset.FromUnixTimeMilliseconds(1782554700000)
        };
        var key = Encoding.ASCII.GetBytes("0123456789abcdef0123456789abcdef");
        var signature = Convert.ToBase64String(
            HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(ProtocolSnapshotWriter.Canonical(snapshot))));
        Assert.Equal("6fiZ1gLZHDf3qT79GoKxBNB04nOKgkNJtAjY9rABVXs=", signature);
    }

    [Fact]
    public void WritesSignedSnapshotWithoutPlaintextToken()
    {
        var directory = Path.Combine(Path.GetTempPath(), "codexus-protocol-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        try
        {
            var now = DateTimeOffset.FromUnixTimeMilliseconds(1782554400123);
            ProtocolSnapshotWriter.Write(new ProtocolSnapshotInput(
                "Player", "PC.BJDMC.NET:25565", 25565, 42, "plain-secret-token",
                "entity", "sdk", "session", "device", "x19", "1.0"
            ), directory, now);

            var jsonText = File.ReadAllText(Path.Combine(directory, "protocol-session.json"));
            Assert.DoesNotContain("plain-secret-token", jsonText, StringComparison.Ordinal);
            var snapshot = JsonSerializer.Deserialize<ProtocolSessionSnapshot>(jsonText)!;
            Assert.Equal("pc.bjdmc.net", snapshot.ServerAddress);
            Assert.Equal(now, snapshot.CreatedAt);
            Assert.Equal(now.AddMinutes(10), snapshot.ExpiresAt);

            var key = Convert.FromBase64String(File.ReadAllText(Path.Combine(directory, "protocol-session.key")).Trim());
            var expected = HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(ProtocolSnapshotWriter.Canonical(snapshot)));
            Assert.True(CryptographicOperations.FixedTimeEquals(expected, Convert.FromBase64String(snapshot.Signature)));
        }
        finally
        {
            Directory.Delete(directory, true);
        }
    }

    [Fact]
    public void ModHostLoadsPluginAndWritesSnapshotOnTransportCreation()
    {
        var root = Path.Combine(Path.GetTempPath(), "codexus-modhost-" + Guid.NewGuid().ToString("N"));
        var modsRoot = Path.Combine(root, "mods");
        var pluginRoot = Path.Combine(modsRoot, "mizulune.protocol");
        var output = Path.Combine(root, "output");
        Directory.CreateDirectory(pluginRoot);
        File.Copy(typeof(ModLoader).Assembly.Location, Path.Combine(pluginRoot, "Codexus.Protocol.dll"));
        File.WriteAllText(Path.Combine(pluginRoot, "manifest.json"),
            "{\"name\":\"Mizulune Protocol Session\",\"id\":\"mizulune.protocol\",\"version\":\"1.0.0\",\"dependencies\":{},\"entryDll\":\"Codexus.Protocol.dll\"}");
        Environment.SetEnvironmentVariable("MIZULUNE_PROTOCOL_DIRECTORY", output);
        try
        {
            using var logger = new LoggerConfiguration().CreateLogger();
            var manager = new ModManager(logger, modsRoot);
            manager.Initialize();
            var transport = global::Codexus.OpenTransport.OpenTransport.Create(new GameProfile
            {
                GameId = "x19",
                GameVersion = "1.20.1",
                BootstrapMd5 = "",
                DatFileMd5 = "",
                Mods = new ModList(),
                User = new UserProfile { UserId = 42, UserToken = "plain-secret-token" }
            }, new CreateRequest
            {
                ServerAddress = "pc.bjdmc.net",
                ServerPort = 25565,
                RoleName = "Player",
                Debug = false
            }, logger);
            try
            {
                Assert.True(File.Exists(Path.Combine(output, "protocol-session.json")));
                Assert.DoesNotContain("plain-secret-token",
                    File.ReadAllText(Path.Combine(output, "protocol-session.json")), StringComparison.Ordinal);
                Assert.True(transport.Registry.IsRegistered(
                    EnumProtocolVersion.V1206, EnumConnectionState.Login, EnumPacketDirection.ClientBound, 0x01));
                Assert.True(transport.Registry.IsRegistered(
                    EnumProtocolVersion.V1206, EnumConnectionState.Login, EnumPacketDirection.ServerBound, 0x03));
                Assert.False(transport.Registry.IsRegistered(
                    EnumProtocolVersion.V1210, EnumConnectionState.Login, EnumPacketDirection.ClientBound, 0x01));
            }
            finally
            {
                transport.Close();
            }
        }
        finally
        {
            Environment.SetEnvironmentVariable("MIZULUNE_PROTOCOL_DIRECTORY", null);
            try { Directory.Delete(root, true); } catch { }
        }
    }

    [Fact]
    public async Task TransportBindsLoopbackAndReportsSelectedPort()
    {
        using var occupied = new TcpListener(IPAddress.Loopback, 0);
        occupied.Start();
        var requestedPort = ((IPEndPoint)occupied.LocalEndpoint).Port;
        using var logger = new LoggerConfiguration().CreateLogger();
        var transport = global::Codexus.OpenTransport.OpenTransport.Create(new GameProfile
        {
            GameId = "x19",
            GameVersion = "1.20",
            BootstrapMd5 = new string('0', 32),
            DatFileMd5 = new string('0', 32),
            Mods = new ModList(),
            User = new UserProfile { UserId = 42, UserToken = "token" }
        }, new CreateRequest
        {
            ServerAddress = "127.0.0.1",
            ServerPort = 25565,
            RoleName = "Player",
            Debug = false,
            LocalAddress = IPAddress.Loopback,
            LocalPort = requestedPort,
            RequiredProtocolVersion = EnumProtocolVersion.V1206
        }, logger);
        try
        {
            var result = await transport.StartAsync();
            Assert.True(result.IsSuccess, result.Error);
            Assert.True(transport.IsRunning);
            Assert.NotNull(transport.BoundLocalEndPoint);
            Assert.True(IPAddress.IsLoopback(transport.BoundLocalEndPoint!.Address));
            Assert.NotEqual(requestedPort, transport.BoundLocalEndPoint.Port);
        }
        finally
        {
            await transport.CloseAsync();
        }
        Assert.False(transport.IsRunning);
        Assert.Null(transport.BoundLocalEndPoint);
    }
}
