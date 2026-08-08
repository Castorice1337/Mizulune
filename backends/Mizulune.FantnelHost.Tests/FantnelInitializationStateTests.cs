using Mizulune.FantnelHost;
using Xunit;

namespace Mizulune.FantnelHost.Tests;

public sealed class FantnelInitializationStateTests
{
    [Fact]
    public async Task StatusStartsAsInitializingAndCanBecomeReady()
    {
        var state = new FantnelInitializationState();

        Assert.True(state.IsInitializing);
        Assert.False(state.IsReady);

        state.MarkReady();
        await state.EnsureReadyAsync();

        Assert.True(state.IsReady);
        Assert.False(state.IsFailed);
    }

    [Fact]
    public async Task FailedStateKeepsControlPlaneAvailableButGatesCommands()
    {
        var state = new FantnelInitializationState();
        state.MarkFailed(new InvalidOperationException("bootstrap unavailable"));

        Assert.True(state.IsFailed);
        var error = await Assert.ThrowsAsync<HostCommandException>(state.EnsureReadyAsync);
        Assert.Equal("fantnel_initialization_failed", error.Code);
        Assert.Contains("bootstrap unavailable", error.Message, StringComparison.Ordinal);
    }
}
