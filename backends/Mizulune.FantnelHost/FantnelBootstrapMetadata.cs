using System.Text.Json;
using Nirvana.Common;
using Nirvana.Common.Entities;
using Nirvana.WPFLauncher.Http;

namespace Mizulune.FantnelHost;

internal static class FantnelBootstrapMetadata
{
    internal const string ExpectedFantnelCommit = "70134bf8ea16bffd951c2afe8cff1f1c1fa3713a";
    private const string DescriptorName = "fantnel-bootstrap.json";

    public static EntityInfo Resolve(string stateDirectory)
    {
        try
        {
            var upstream = X19Extensions.Nirvana.ApiAsync<EntityInfo>("/fantnel.json")
                .GetAwaiter().GetResult();
            if (HasRequiredFields(upstream))
            {
                HostBootstrapLog.Info("Loaded Fantnel bootstrap metadata from the frozen upstream endpoint.");
                return upstream!;
            }
            HostBootstrapLog.Warning("Fantnel bootstrap endpoint returned incomplete metadata; using the locked fallback.");
        }
        catch (Exception error)
        {
            HostBootstrapLog.Warning(
                $"Fantnel bootstrap endpoint is unavailable ({error.GetType().Name}); using the locked fallback.");
        }

        var path = Path.Combine(stateDirectory, "resources", DescriptorName);
        return LoadFallback(path);
    }

    internal static EntityInfo LoadFallback(string path)
    {
        if (!File.Exists(path))
            throw new FileNotFoundException("The locked Fantnel bootstrap descriptor is unavailable.", path);

        var descriptor = JsonSerializer.Deserialize<FantnelBootstrapDescriptor>(
            File.ReadAllText(path),
            new JsonSerializerOptions(JsonSerializerDefaults.Web));
        if (descriptor is null || descriptor.SchemaVersion != 1)
            throw new InvalidDataException("The Fantnel bootstrap descriptor schema is invalid.");
        if (!string.Equals(descriptor.FantnelCommit, ExpectedFantnelCommit, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("The Fantnel bootstrap descriptor does not match the frozen commit.");
        if (!string.Equals(descriptor.UpdateVersion, PublicProgram.UpdateVersion, StringComparison.Ordinal))
            throw new InvalidDataException("The Fantnel bootstrap update version does not match the frozen core.");
        if (descriptor.Versions is null || !descriptor.Versions.Contains(PublicProgram.Version, StringComparer.Ordinal))
            throw new InvalidDataException("The Fantnel bootstrap descriptor does not allow the frozen core version.");
        if (descriptor.CrcSalt is null || descriptor.CrcSalt.Length != 32
            || !descriptor.CrcSalt.All(Uri.IsHexDigit))
            throw new InvalidDataException("The Fantnel bootstrap CRC salt is invalid.");

        HostBootstrapLog.Warning(
            $"Using locked Fantnel bootstrap metadata for commit {ExpectedFantnelCommit[..12]}; source={descriptor.Source}.");
        return new EntityInfo
        {
            UpdateVersions = descriptor.UpdateVersion,
            Versions = descriptor.Versions,
            CrcSalt = descriptor.CrcSalt
        };
    }

    private static bool HasRequiredFields(EntityInfo? metadata) =>
        metadata is not null
        && !string.IsNullOrWhiteSpace(metadata.UpdateVersions)
        && metadata.Versions is { Length: > 0 }
        && metadata.CrcSalt is { Length: 32 }
        && metadata.CrcSalt.All(Uri.IsHexDigit);
}

internal sealed record FantnelBootstrapDescriptor(
    int SchemaVersion,
    string FantnelCommit,
    string UpdateVersion,
    string[] Versions,
    string CrcSalt,
    string Source);
