using Codexus.OpenSDK.Entities.Yggdrasil;
using Codexus.OpenTransport.Entities.Transport;

namespace Codexus.OpenTransport.Event;

public record EventPrepareTransport(GameProfile Profile, CreateRequest Request);
