# Public Source Boundary

This public repository intentionally excludes Mizulune's proprietary server
protocol module, authentication/session bridge, private mappings, packet
fixtures, traces, semantic ledgers, and protocol-specific backend code.

The GNU GPL v3 license applies to the files actually published in this
repository. It does not grant rights to private materials that are not
distributed here, and no private protocol implementation should be committed
to this repository in the future.

The Gradle check lifecycle includes verifyPublicSourceBoundary, which fails
when reserved private paths or protocol implementation markers are introduced.

Requests concerning private or commercial protocol licensing:

- ilovecastoriceforever@gmail.com
- QQ 2700219578
