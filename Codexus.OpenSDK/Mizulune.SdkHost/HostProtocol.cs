using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mizulune.SdkHost;

internal sealed record HostRequest(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("method")] string Method,
    [property: JsonPropertyName("params")] JsonElement Params);

internal sealed record HostResponse(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("ok")] bool Ok,
    [property: JsonPropertyName("result")] object? Result = null,
    [property: JsonPropertyName("error")] HostError? Error = null);

internal sealed record HostError(
    [property: JsonPropertyName("code")] string Code,
    [property: JsonPropertyName("message")] string Message);

internal sealed record HostEvent(
    [property: JsonPropertyName("event")] string Event,
    [property: JsonPropertyName("data")] object? Data = null);

internal sealed class HostCommandException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}
