namespace Codexus.OpenTransport.Entities.Transport;

// ReSharper disable once ClassNeverInstantiated.Global
public class CreateRequest
{
    public required string ServerAddress { get; set; }
    public required int ServerPort { get; set; }
    public required string RoleName { get; set; }
    public required bool Debug { get; set; }

    public System.Net.IPAddress LocalAddress { get; set; } = System.Net.IPAddress.Loopback;
    public int LocalPort { get; set; } = 6445;
    public Codexus.OpenTransport.Packet.EnumProtocolVersion? RequiredProtocolVersion { get; set; }

    public CreateRequest Clone()
    {
        return (CreateRequest)MemberwiseClone();
    }
}
