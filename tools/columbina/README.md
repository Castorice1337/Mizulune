# Project-local Columbina SDK

This directory vendors the Columbina v2 strict SDK and format reference used by the repository. The workflow configuration is `.columbina/workflow.toml`.

## Commands

```powershell
python tools/columbina/columbina_sdk.py check --strict
python tools/columbina/columbina_sdk.py sync --check
```

The SDK is dependency-free and is kept in sync with the installed `columbina-workflow` skill when the workflow version changes.
