using Codexus.ModHost;
using Codexus.ModHost.Event;
using Codexus.OpenSDK;
using Codexus.OpenSDK.Entities.Yggdrasil;
using Codexus.OpenSDK.Exceptions;
using Codexus.OpenSDK.Yggdrasil;
using Codexus.OpenTransport;
using Codexus.OpenTransport.Entities.Transport;
using Codexus.OpenTransport.Event;
using Serilog;

Console.Title = "Codexus.OpenSDK Original Example";
AppDomain.CurrentDomain.UnhandledException += (_, eventArgs) =>
{
    Console.Error.WriteLine(eventArgs.ExceptionObject);
    Console.WriteLine("Press any key to close...");
    Console.ReadKey(intercept: true);
};

/*
 * Sample Project: Example for testing OpenTransport and the Mod system
 */
Log.Logger = new LoggerConfiguration()
    .MinimumLevel.Debug()
    .WriteTo.Console()
    .CreateLogger();

var modsDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Mods");
var manager = new ModManager(Log.Logger, modsDir);
manager.Initialize();

var c4399 = new C4399();
var x19 = new X19();

Console.Write("4399 username: ");
var username = Console.ReadLine()?.Trim();
if (string.IsNullOrWhiteSpace(username)) throw new InvalidOperationException("Username is required.");
Console.Write("4399 password: ");
var password = ReadSecret();
if (string.IsNullOrWhiteSpace(password)) throw new InvalidOperationException("Password is required.");

var cookie = await Login4399Async(c4399, username, password);
var (user, _) = await x19.ContinueAsync(cookie);
Console.WriteLine("4399/X19 login succeeded.");
Console.Write("Minecraft role name: ");
var roleName = Console.ReadLine()?.Trim();
if (string.IsNullOrWhiteSpace(roleName)) throw new InvalidOperationException("Role name is required.");

var profile = new GameProfile
{
    GameId = "4663909014288106690",
    GameVersion = "1.21",
    BootstrapMd5 = "684528BF492A84489F825F5599B3E1C6",
    DatFileMd5 = "574033E7E4841D8AC4C14D7FA5E05337",
    Mods = new ModList(),
    User = new UserProfile
    {
        UserId = int.Parse(user.EntityId),
        UserToken = user.Token
    }
};

var request = new CreateRequest
{
    ServerAddress = "45.253.142.30",
    ServerPort = 25565,
    RoleName = roleName,
    Debug = false
};

var yggdrasil = new StandardYggdrasil(new YggdrasilData
{
    LauncherVersion = x19.GameVersion,
    Channel = "netease",
    CrcSalt = "22AC4B0143EFFC80F2905B267D4D84D3"
});

EventBus.Instance.Subscribe<EventJoinServer>(async e =>
{
    await Task.Run(async () =>
    {
        var result = await yggdrasil.JoinServerAsync(e.Context.Session.Profile, e.ServerId);

        if (result.IsSuccess)
            Log.Information("Joined server successfully");
        else
            Log.Error("Joined server failed: {Error}", result.Error);
    }).ConfigureAwait(false);
});

var transport = OpenTransport.Create(profile, request, Log.Logger);
await transport.StartAsync();

Console.WriteLine("Press any key to exit...");
Console.ReadKey();

static string ReadSecret()
{
    var value = new System.Text.StringBuilder();
    while (Console.ReadKey(intercept: true) is var key && key.Key != ConsoleKey.Enter)
    {
        if (key.Key == ConsoleKey.Backspace)
        {
            if (value.Length == 0) continue;
            value.Length--;
            Console.Write("\b \b");
            continue;
        }
        if (char.IsControl(key.KeyChar)) continue;
        value.Append(key.KeyChar);
        Console.Write('*');
    }
    Console.WriteLine();
    return value.ToString();
}

static async Task<string> Login4399Async(C4399 c4399, string username, string password)
{
    while (true)
    {
        try
        {
            return await c4399.LoginWithPasswordAsync(username, password);
        }
        catch (VerifyException error) when (error.Challenge != null)
        {
            var challenge = error.Challenge;
            var extension = challenge.ContentType switch
            {
                "image/png" => ".png",
                "image/gif" => ".gif",
                "image/webp" => ".webp",
                _ => ".jpg"
            };
            var imagePath = Path.Combine(AppContext.BaseDirectory, "captcha" + extension);
            await File.WriteAllBytesAsync(imagePath, challenge.ImageBytes);
            Console.WriteLine($"Captcha saved to: {imagePath}");
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(imagePath)
                {
                    UseShellExecute = true
                });
            }
            catch (Exception openError)
            {
                Console.WriteLine($"Could not open captcha automatically: {openError.Message}");
            }

            Console.Write("Captcha: ");
            var captcha = Console.ReadLine()?.Trim();
            if (string.IsNullOrWhiteSpace(captcha))
                throw new InvalidOperationException("Captcha is required.");
            return await c4399.LoginWithPasswordAsync(username, password, challenge.SessionId, captcha);
        }
    }
}
