# Codexus.Protocol

OpenSDK plugin that writes the signed Mizulune protocol session snapshot used by the Java `Protocol` module.

## Output

- `%USERPROFILE%\.mizulune\protocol-session.json`
- `%USERPROFILE%\.mizulune\protocol-session.key`

The snapshot contains a SHA-256 hash of the user token, never the plaintext token. The complete snapshot is authenticated with HMAC-SHA256 and expires after ten minutes.

Optional launcher-provided fields:

- `CODEXUS_ENTITY_ID`
- `CODEXUS_SDK_UID`
- `CODEXUS_SESSION_ID`
- `CODEXUS_DEVICE_ID`
- `MIZULUNE_LAUNCHER_VERSION`

`MIZULUNE_PROTOCOL_DIRECTORY` overrides the output directory for tests or launcher-managed profiles.

## Build

```powershell
.\.dotnet\dotnet.exe build .\Codexus.Protocol\Codexus.Protocol.csproj -c Release
```

The build stages only `Codexus.Protocol.dll` and `manifest.json` under `Codexus.Protocol\dist\mizulune.protocol`. Shared `Codexus.ModSDK` and `Codexus.OpenTransport` assemblies remain owned by the host to avoid duplicate assembly identities.
