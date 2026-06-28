using Codexus.OpenSDK.Entities.C4399;

namespace Codexus.OpenSDK.Exceptions;

public class VerifyException : Exception
{
    public VerifyException(string message, C4399VerificationChallenge? challenge = null) : base(message)
    {
        Challenge = challenge;
    }

    public C4399VerificationChallenge? Challenge { get; }
}
