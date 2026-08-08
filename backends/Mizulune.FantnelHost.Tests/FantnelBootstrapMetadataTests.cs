using System.Text.Json;
using Mizulune.FantnelHost;
using Xunit;

namespace Mizulune.FantnelHost.Tests;

public sealed class FantnelBootstrapMetadataTests : IDisposable
{
    private readonly string directory = Path.Combine(
        Path.GetTempPath(), "mizulune-fantnel-bootstrap-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public void LoadsDescriptorBoundToFrozenCommitAndVersion()
    {
        var path = WriteDescriptor(FantnelBootstrapMetadata.ExpectedFantnelCommit);

        var metadata = FantnelBootstrapMetadata.LoadFallback(path);

        Assert.Equal("1.0.0", metadata.UpdateVersions);
        Assert.Contains("1.7.0", metadata.Versions!);
        Assert.Equal("22AC4B0143EFFC80F2905B267D4D84D3", metadata.CrcSalt);
    }

    [Fact]
    public void RejectsDescriptorForAnotherFantnelCommit()
    {
        var path = WriteDescriptor(new string('0', 40));

        Assert.Throws<InvalidDataException>(() => FantnelBootstrapMetadata.LoadFallback(path));
    }

    private string WriteDescriptor(string commit)
    {
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, "fantnel-bootstrap.json");
        File.WriteAllText(path, JsonSerializer.Serialize(new
        {
            schemaVersion = 1,
            fantnelCommit = commit,
            updateVersion = "1.0.0",
            versions = new[] { "1.7.0" },
            crcSalt = "22AC4B0143EFFC80F2905B267D4D84D3",
            source = "test"
        }));
        return path;
    }

    public void Dispose()
    {
        if (Directory.Exists(directory)) Directory.Delete(directory, true);
    }
}
