# MaxHook release input

`MaxHook.dll` is not stored in this source repository. A portable Loader release may
stage an authorized copy here, pass `-PmaxHookDll=<path>`, or set `MAXHOOK_DLL`.

The build accepts only this SHA-256:

```text
982D8223CF8DA9584D67B1A7A24E5B2515DA22BA72EEBBAF813A353DA14F956A
```

`packageDist` copies the verified file to `build/dist/maxhook/MaxHook.dll`. The
Loader then stages it under the current user's `.mizulune/runtime/maxhook/`
directory. If it is absent, the protocol keeps the hash-gated official-install
fallback at `<installRoot>/native/MaxHook.dll`.

The upstream artifact's redistribution terms remain the distributor's
responsibility; do not replace this input with an unverified download.
