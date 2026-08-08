# Fantnel Backend Boundary

## Frozen upstream

- Runtime core: `fantnel` at `70134bf8ea16bffd951c2afe8cff1f1c1fa3713a`.
- UI reference only: `fantnel-pro` at `63edab4b064f3a259272eb686a6f7e0ca94a3137`.
- `verifyFantnelUpstream` checks commit, tree hash, and a clean submodule worktree before publishing.
- Login, captcha, server/role lookup, direct launch, proxy, join authentication, and decoupling logic remain entirely inside the frozen upstream projects.

## Mizulune-owned layer

- `Mizulune.FantnelHost` exposes upstream public entry points through a current-user-only named pipe.
- Returned account objects are safe DTOs; password, token, and cookie values are never sent to WebView2.
- Direct launch returns `LauncherService.GetPid()`. The loader waits for an explicit Inject action before using the existing manual-map injector.
- The packaged backend is staged to `%USERPROFILE%\.mizulune\backends\fantnel`; stdout/stderr are written to `.mizulune/logs/fantnel-host.log` and never share the control channel.

## Protocol snapshot

`ProtocolSessionSnapshot v2` uses `source=fantnel`, the selected role/server, Fantnel user ID, a SHA-256 token digest, game ID, timestamps, and HMAC. The current Protocol runtime does not consume `sdkUid`, `sessionId`, or `deviceId`, so they remain empty instead of being fabricated.

No Fantnel source exception is currently required. If a future recovered packet schema proves that a missing value is server-visible, the only permitted upstream change is an additive observer/retention field; request order, cryptography, login branching, join, launch, proxy, and decoupling behavior remain frozen.

## Runtime resources

`backends/fantnel-runtime.lock.json` pins every distributed file by SHA256. Runtime update flags remain disabled, so Fantnel cannot replace the source-built Host or core assemblies. Upstream `static` descriptors are recorded for provenance but are not trusted as mutable runtime updates.
