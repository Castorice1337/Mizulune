namespace Mizulune.FantnelHost;

/// <summary>
/// Separates the named-pipe handshake from the frozen FantNEL initialization.
/// The control channel can therefore report progress instead of appearing
/// disconnected while the upstream bootstrap is doing network or disk work.
/// </summary>
internal sealed class FantnelInitializationState
{
    private readonly TaskCompletionSource<bool> completion = new(
        TaskCreationOptions.RunContinuationsAsynchronously);
    private int state;
    private string? failureMessage;

    public bool IsReady => Volatile.Read(ref state) == 1;
    public bool IsFailed => Volatile.Read(ref state) == 2;
    public bool IsInitializing => Volatile.Read(ref state) == 0;
    public string? FailureMessage => failureMessage;

    public void MarkReady()
    {
        if (Interlocked.CompareExchange(ref state, 1, 0) == 0)
            completion.TrySetResult(true);
    }

    public void MarkFailed(Exception error)
    {
        failureMessage = SensitiveText.Redact(error.Message);
        if (Interlocked.CompareExchange(ref state, 2, 0) == 0)
            completion.TrySetResult(false);
    }

    public async Task EnsureReadyAsync()
    {
        if (await completion.Task.ConfigureAwait(false)) return;
        throw new HostCommandException(
            "fantnel_initialization_failed",
            FailureMessage ?? "FantNEL Host initialization failed.");
    }

}
