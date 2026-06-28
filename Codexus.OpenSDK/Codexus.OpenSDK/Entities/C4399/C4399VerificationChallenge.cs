namespace Codexus.OpenSDK.Entities.C4399;

public sealed record C4399VerificationChallenge(
    string SessionId,
    Uri ImageUrl,
    byte[] ImageBytes,
    string ContentType);
