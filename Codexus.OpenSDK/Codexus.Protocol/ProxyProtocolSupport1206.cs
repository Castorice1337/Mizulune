using System.Numerics;
using System.Security.Cryptography;
using System.Text;
using Codexus.ModSDK;
using Codexus.OpenTransport.Codecs.Stream;
using Codexus.OpenTransport.Entities.Transport;
using Codexus.OpenTransport.Event;
using Codexus.OpenTransport.Extensions;
using Codexus.OpenTransport.Packet;
using Codexus.OpenTransport.Packet.Handler;
using Codexus.OpenTransport.Registry;
using Codexus.OpenTransport.Session;
using Org.BouncyCastle.Asn1.X509;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Crypto.Encodings;
using Org.BouncyCastle.Crypto.Engines;
using Org.BouncyCastle.Security;

namespace Codexus.Protocol;

/// <summary>
/// Login/configuration interception for a 1.20.1 client translated by ViaForge
/// to the 1.20.5/1.20.6 wire protocol (protocol 766).
/// </summary>
internal sealed class ProxyProtocolSupport1206(IModContext context, CreateRequest request) : IRegistryApply
{
    private static readonly IByteBufferCodec<LoginStart> LoginStartCodec =
        StreamCodec.Composite(
            ByteBufCodecs.MaxString(16), value => value.Profile,
            ByteBufCodecs.Uuid, value => value.Uuid,
            (profile, uuid) => new LoginStart(profile, uuid));

    private static readonly IByteBufferCodec<EncryptionResponse> EncryptionResponseCodec =
        StreamCodec.Composite(
            ByteBufCodecs.ByteArray, value => value.SecretKeyEncrypted,
            ByteBufCodecs.ByteArray, value => value.VerifyTokenEncrypted,
            (key, token) => new EncryptionResponse(key, token));

    private static readonly IByteBufferCodec<LoginAcknowledged> LoginAcknowledgedCodec =
        StreamCodec.Unit(new LoginAcknowledged());

    private static readonly IByteBufferCodec<AcknowledgeFinishConfiguration> FinishConfigurationCodec =
        StreamCodec.Unit(new AcknowledgeFinishConfiguration());

    private static readonly IByteBufferCodec<EnableCompression> EnableCompressionCodec =
        StreamCodec.Composite(
            ByteBufCodecs.VarInt, value => value.CompressionThreshold,
            threshold => new EnableCompression(threshold));

    private static readonly IByteBufferCodec<EncryptionRequest> EncryptionRequestCodec =
        StreamCodec.Composite(
            ByteBufCodecs.MaxString(20), value => value.ServerId,
            ByteBufCodecs.ByteArray, value => value.PublicKey,
            ByteBufCodecs.ByteArray, value => value.VerifyToken,
            ByteBufCodecs.Bool, value => value.ShouldAuthenticate,
            (id, key, token, authenticate) => new EncryptionRequest(id, key, token, authenticate));

    private static readonly IByteBufferCodec<List<ProfileProperty>> PropertiesCodec =
        StreamCodec.Composite(
            ByteBufCodecs.String, value => value.Name,
            ByteBufCodecs.String, value => value.Value,
            ByteBufCodecs.String.OptionalRef(), value => value.Signature,
            (name, value, signature) => new ProfileProperty(name, value, signature)).List();

    private static readonly IByteBufferCodec<LoginSuccess> LoginSuccessCodec =
        StreamCodec.Composite(
            ByteBufCodecs.Uuid, value => value.Uuid,
            ByteBufCodecs.MaxString(20), value => value.Username,
            PropertiesCodec, value => value.Properties,
            ByteBufCodecs.Bool, value => value.StrictErrorHandling,
            (uuid, username, properties, strict) => new LoginSuccess(uuid, username, properties, strict));

    public void ApplyTo(MinecraftRegistry registry, RegistryScope scope)
    {
        registry.Builder(scope)
            .ForVersion(EnumProtocolVersion.V1206)
            .InState(EnumConnectionState.Login)
            .ServerBound()
            .Register(0x00, LoginStartCodec)
            .Attach<LoginStart>((_, packet) =>
            {
                if (!string.IsNullOrWhiteSpace(request.RoleName)) packet.Profile = request.RoleName;
            })
            .Register(0x01, EncryptionResponseCodec)
            .Register(0x03, LoginAcknowledgedCodec)
            .Attach<LoginAcknowledged>((packetContext, _) =>
            {
                packetContext.Session.SetState(EnumConnectionState.Configuration);
                context.LogInformation("Initial configuration process started.");
            })
            .ClientBound()
            .Register(0x03, EnableCompressionCodec)
            .Attach<EnableCompression>((packetContext, packet) =>
            {
                if (packetContext.Session.Remote != null)
                    NetworkSession.EnableCompression(packetContext.Session.Remote, packet.CompressionThreshold);
                packetContext.OnSendAfter(() =>
                    NetworkSession.EnableCompression(packetContext.Session.Local, packet.CompressionThreshold));
            })
            .Register(0x01, EncryptionRequestCodec)
            .Attach<EncryptionRequest>(HandleEncryptionRequest)
            .Register(0x02, LoginSuccessCodec)
            .Attach<LoginSuccess>((_, packet) =>
                context.LogInformation("{0}({1}) authenticated through the proxy.", packet.Username, packet.Uuid))
            .InState(EnumConnectionState.Configuration)
            .ServerBound()
            .Register(0x03, FinishConfigurationCodec)
            .Attach<AcknowledgeFinishConfiguration>((packetContext, _) =>
            {
                packetContext.Session.SetState(EnumConnectionState.Play);
                context.LogInformation("Configuration completed successfully.");
            });
    }

    private void HandleEncryptionRequest(PacketHandlerContext packetContext, EncryptionRequest packet)
    {
        var session = packetContext.Session;
        var generator = new CipherKeyGenerator();
        generator.Init(new KeyGenerationParameters(new SecureRandom(), 128));
        var rsaKey = SubjectPublicKeyInfo.GetInstance(packet.PublicKey);
        var secretKey = generator.GenerateKey();

        using var stream = new MemoryStream(20);
        stream.Write(Encoding.GetEncoding("ISO-8859-1").GetBytes(packet.ServerId));
        stream.Write(secretKey);
        stream.Write(packet.PublicKey);
        stream.Position = 0;
        var serverId = ToServerId(stream);

        context.EventBus.Publish(new EventJoinServer(packetContext, serverId));

        var encoding = new Pkcs1Encoding(new RsaEngine());
        encoding.Init(true, PublicKeyFactory.CreateKey(rsaKey));
        packetContext.OnSendAfter(async () =>
        {
            var response = new EncryptionResponse(
                encoding.ProcessBlock(secretKey, 0, secretKey.Length),
                encoding.ProcessBlock(packet.VerifyToken, 0, packet.VerifyToken.Length));
            await packetContext.SendToRemote(response);
            if (session.Remote != null) NetworkSession.EnableEncryption(session.Remote, secretKey);
        });
        packetContext.Cancel();
    }

    private static string ToServerId(Stream data)
    {
        using var sha = SHA1.Create();
        var hash = sha.ComputeHash(data);
        Array.Reverse(hash);
        var number = new BigInteger(hash);
        return number < 0
            ? "-" + (-number).ToString("x").TrimStart('0')
            : number.ToString("x").TrimStart('0');
    }

    internal sealed record LoginStart(string Profile, Guid Uuid) : IServerBoundPacket
    {
        public string Profile { get; set; } = Profile;
        public Guid Uuid { get; set; } = Uuid;
    }

    internal sealed record EncryptionResponse(byte[] SecretKeyEncrypted, byte[] VerifyTokenEncrypted)
        : IServerBoundPacket;

    internal sealed record LoginAcknowledged : IServerBoundPacket;
    internal sealed record AcknowledgeFinishConfiguration : IServerBoundPacket;
    internal sealed record EnableCompression(int CompressionThreshold) : IClientBoundPacket;
    internal sealed record EncryptionRequest(
        string ServerId,
        byte[] PublicKey,
        byte[] VerifyToken,
        bool ShouldAuthenticate) : IClientBoundPacket;

    internal sealed record ProfileProperty(string Name, string Value, string? Signature);

    internal sealed record LoginSuccess(
        Guid Uuid,
        string Username,
        List<ProfileProperty> Properties,
        bool StrictErrorHandling) : IClientBoundPacket;
}
