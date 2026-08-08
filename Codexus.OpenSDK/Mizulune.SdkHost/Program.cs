using System.Text;
using System.Text.Json;
using Mizulune.SdkHost;
using Serilog;
using Serilog.Events;

Console.InputEncoding = Encoding.UTF8;
Console.OutputEncoding = new UTF8Encoding(false);

if (args.Contains("--health", StringComparer.OrdinalIgnoreCase))
{
    Console.WriteLine("{\"ok\":true,\"name\":\"Mizulune.SdkHost\",\"protocol\":1}");
    return;
}

var sdkWorkDirectory = Path.Combine(SdkHostService.MizuluneDirectory(), "sdk");
Directory.CreateDirectory(sdkWorkDirectory);
Directory.SetCurrentDirectory(sdkWorkDirectory);

Log.Logger = new LoggerConfiguration()
    .MinimumLevel.Information()
    .WriteTo.Console(standardErrorFromLevel: LogEventLevel.Verbose)
    .CreateLogger();

var jsonOptions = new JsonSerializerOptions
{
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
};
var writeLock = new SemaphoreSlim(1, 1);

async Task WriteAsync(object value)
{
    await writeLock.WaitAsync();
    try
    {
        await Console.Out.WriteLineAsync(JsonSerializer.Serialize(value, jsonOptions));
        await Console.Out.FlushAsync();
    }
    finally
    {
        writeLock.Release();
    }
}

await using var service = new SdkHostService(Log.Logger,
    (name, data) => WriteAsync(new HostEvent(name, data)));

while (await Console.In.ReadLineAsync() is { } line)
{
    line = line.TrimStart('\uFEFF');
    if (string.IsNullOrWhiteSpace(line)) continue;
    HostRequest? request = null;
    try
    {
        request = JsonSerializer.Deserialize<HostRequest>(line, jsonOptions)
                  ?? throw new HostCommandException("invalid_request", "Request is empty.");
        if (string.IsNullOrWhiteSpace(request.Id) || string.IsNullOrWhiteSpace(request.Method))
            throw new HostCommandException("invalid_request", "id and method are required.");
        var result = await service.HandleAsync(request.Method, request.Params);
        await WriteAsync(new HostResponse(request.Id, true, result));
    }
    catch (HostCommandException error)
    {
        await WriteAsync(new HostResponse(request?.Id ?? string.Empty, false,
            Error: new HostError(error.Code, error.Message)));
    }
    catch (Exception error)
    {
        Log.Error(error, "SDK command failed");
        await WriteAsync(new HostResponse(request?.Id ?? string.Empty, false,
            Error: new HostError("internal_error", "The SDK host could not complete the request.")));
    }
}

await Log.CloseAndFlushAsync();
