namespace Codexus.OpenTransport.Event;

public record EventProtocolRejected(OpenTransport Transport, int ActualProtocol, int RequiredProtocol);
