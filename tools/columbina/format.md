# Columbina v2 format

## Required root files

`workflow.toml`:

```toml
[workflow]
version = 2
mode = "strict"
```

`CONTEXT.md` must contain these headings and the generated block markers:

```md
# Columbina Context

## 当前项目状态

## Phase 索引

<!-- COLUMBINA:PHASES:BEGIN -->
<!-- COLUMBINA:PHASES:END -->
```

Only `columbina_sdk.py sync` may rewrite the text between those markers.

## Phase directory and metadata

Name the phase directory `NNNN_short-slug`, for example `0007_protocol-retry`. Create `CONTEXT.md`, `test.md`, and `debug.md` beside `phase.toml`.

```toml
[phase]
id = "0007"
slug = "protocol-retry"
title = "协议重试"
status = "active" # active | recorded | ready-for-human-test | verified | closed | blocked
depends_on = []

[owner]
actor_id = "alice-a1b2c3d4"
name = "Alice"
email = "alice@example.com"

[evidence]
base_commit = "0123abc"
last_commit = "4567def"
modified_files = ["src/main/java/example/Retry.java"]

[checks]
build = "pending" # pending | pass | fail | skipped
build_note = ""
test = "not-run" # not-run | waiting-user-pass | pass | fail
human_confirmed_by = ""
human_confirmed_at = ""

[[contributors]]
actor_id = "alice-a1b2c3d4"
name = "Alice"
email = "alice@example.com"
branch = "feature/0007-protocol-retry"
commits = ["4567def"]
role = "implementation"
```

Use `build = "skipped"` only for a phase with no executable build requirement and state the reason in `build_note`. Use `test = "pass"` only after a human has explicitly confirmed success; populate both human confirmation fields.

In a Git worktree, `check --strict` resolves `base_commit` and `last_commit`, requires the last commit author to appear in `[[contributors]]`, and requires `modified_files` to overlap the recorded commit range. This is traceability validation, not identity authentication.

## Search phases

Run `phase search --query <keyword>` to find a phase by ID, slug, title, owner/contributor metadata, commit, changed-file path, or phase-document content. Use `--json` for tooling. Results include the phase's status plus its next legal state transition and any fields still blocking it.

## Contributor context

Keep one file per actor at `contributors/<actor-id>/CONTEXT.md`. It may list active branches, assigned phase IDs, current blockers, and handoff notes. Link to canonical phase records; do not duplicate or declare phase status there.

Generate it idempotently from local Git attribution with `identity init-context`. The generated `actor-id` uses a readable name slug and the first eight characters of the SHA-256 digest of the normalized email; it never exposes the raw email in the directory name.
