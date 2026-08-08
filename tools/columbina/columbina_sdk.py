#!/usr/bin/env python3
"""Strict, dependency-free validation and phase gates for Columbina v2."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Any


PHASE_DIR = re.compile(r"^(?P<id>\d{4})_(?P<slug>[a-z0-9][a-z0-9-]*)$")
STATUSES = {"active", "recorded", "ready-for-human-test", "verified", "closed", "blocked"}
BUILD_STATUSES = {"pending", "pass", "fail", "skipped"}
TEST_STATUSES = {"not-run", "waiting-user-pass", "pass", "fail"}
ROOT_HEADINGS = ("# Columbina Context", "## 当前项目状态", "## Phase 索引")
MARKER_BEGIN = "<!-- COLUMBINA:PHASES:BEGIN -->"
MARKER_END = "<!-- COLUMBINA:PHASES:END -->"


@dataclass
class Phase:
    path: Path
    phase_id: str
    slug: str
    data: dict[str, Any]

    @property
    def metadata(self) -> dict[str, Any]:
        return self.data.get("phase", {})

    @property
    def status(self) -> str:
        return str(self.metadata.get("status", ""))


def load_toml(path: Path, issues: list[str]) -> dict[str, Any]:
    try:
        with path.open("rb") as handle:
            return tomllib.load(handle)
    except (OSError, tomllib.TOMLDecodeError) as error:
        issues.append(f"{path}: invalid TOML: {error}")
        return {}


def value(data: dict[str, Any], section: str, key: str) -> Any:
    selected = data.get(section, {})
    return selected.get(key) if isinstance(selected, dict) else None


def require_text(issues: list[str], path: Path, data: dict[str, Any], section: str, key: str) -> str:
    result = value(data, section, key)
    if not isinstance(result, str) or not result.strip():
        issues.append(f"{path}: [{section}].{key} must be a non-empty string")
        return ""
    return result.strip()


def discover_phases(root: Path, issues: list[str]) -> list[Phase]:
    phase_root = root / ".columbina" / "phase"
    if not phase_root.exists():
        return []
    phases: list[Phase] = []
    for directory in sorted(phase_root.iterdir()):
        if not directory.is_dir():
            continue
        match = PHASE_DIR.fullmatch(directory.name)
        if not match:
            issues.append(f"{directory}: phase directory must match NNNN_short-slug")
            continue
        metadata = directory / "phase.toml"
        if not metadata.exists():
            issues.append(f"{directory}: missing phase.toml")
            continue
        phases.append(Phase(directory, match["id"], match["slug"], load_toml(metadata, issues)))
    return phases


def validate_root(root: Path, issues: list[str]) -> None:
    columbina = root / ".columbina"
    workflow = columbina / "workflow.toml"
    context = columbina / "CONTEXT.md"
    if not workflow.exists():
        issues.append(f"{workflow}: missing; run init")
    else:
        config = load_toml(workflow, issues).get("workflow", {})
        if config.get("version") != 2:
            issues.append(f"{workflow}: [workflow].version must equal 2")
        if config.get("mode") not in {"strict", "compat"}:
            issues.append(f"{workflow}: [workflow].mode must be strict or compat")
    if not context.exists():
        issues.append(f"{context}: missing; run init")
        return
    text = context.read_text(encoding="utf-8")
    for heading in ROOT_HEADINGS:
        if heading not in text:
            issues.append(f"{context}: missing required heading {heading!r}")
    if MARKER_BEGIN not in text or MARKER_END not in text:
        issues.append(f"{context}: missing generated phase-index markers")


def validate_phase(phase: Phase, phase_map: dict[str, Phase], issues: list[str]) -> None:
    path = phase.path / "phase.toml"
    data = phase.data
    if require_text(issues, path, data, "phase", "id") != phase.phase_id:
        issues.append(f"{path}: [phase].id must match directory ID {phase.phase_id}")
    if require_text(issues, path, data, "phase", "slug") != phase.slug:
        issues.append(f"{path}: [phase].slug must match directory slug {phase.slug}")
    require_text(issues, path, data, "phase", "title")
    if phase.status not in STATUSES:
        issues.append(f"{path}: [phase].status is invalid")
    dependencies = value(data, "phase", "depends_on")
    if not isinstance(dependencies, list) or not all(isinstance(item, str) and re.fullmatch(r"\d{4}", item) for item in dependencies):
        issues.append(f"{path}: [phase].depends_on must be an array of four-digit IDs")
        dependencies = []
    for dependency in dependencies:
        if dependency == phase.phase_id:
            issues.append(f"{path}: a phase may not depend on itself")
        elif dependency not in phase_map:
            issues.append(f"{path}: dependency {dependency} does not exist")

    for key in ("actor_id", "name", "email"):
        require_text(issues, path, data, "owner", key)
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", str(value(data, "owner", "email") or "")):
        issues.append(f"{path}: [owner].email must look like an email address")

    build = value(data, "checks", "build")
    test = value(data, "checks", "test")
    if build not in BUILD_STATUSES:
        issues.append(f"{path}: [checks].build is invalid")
    if test not in TEST_STATUSES:
        issues.append(f"{path}: [checks].test is invalid")
    if build == "skipped" and not require_text(issues, path, data, "checks", "build_note"):
        issues.append(f"{path}: skipped build requires [checks].build_note")
    if test == "pass":
        require_text(issues, path, data, "checks", "human_confirmed_by")
        require_text(issues, path, data, "checks", "human_confirmed_at")

    if phase.status != "active":
        require_text(issues, path, data, "evidence", "base_commit")
        require_text(issues, path, data, "evidence", "last_commit")
        files = value(data, "evidence", "modified_files")
        if not isinstance(files, list) or not files or not all(isinstance(item, str) and item.strip() for item in files):
            issues.append(f"{path}: non-active phase requires non-empty [evidence].modified_files")
    if phase.status in {"ready-for-human-test", "verified", "closed"} and build not in {"pass", "skipped"}:
        issues.append(f"{path}: {phase.status} requires a passing or explicitly skipped build")
    if phase.status in {"verified", "closed"} and test != "pass":
        issues.append(f"{path}: {phase.status} requires [checks].test = pass")
    if phase.status == "closed":
        for dependency in dependencies:
            if phase_map[dependency].status != "closed":
                issues.append(f"{path}: closed phase requires dependency {dependency} to be closed")

    for name, header in (("CONTEXT.md", "# Phase"), ("test.md", "# Test Record"), ("debug.md", "# Debug Record")):
        document = phase.path / name
        if not document.exists():
            issues.append(f"{phase.path}: missing {name}")
        elif header not in document.read_text(encoding="utf-8"):
            issues.append(f"{document}: missing required heading {header!r}")

    contributors = data.get("contributors")
    if not isinstance(contributors, list) or not contributors:
        issues.append(f"{path}: requires at least one [[contributors]] entry")
    elif not any(isinstance(item, dict) and item.get("actor_id") == value(data, "owner", "actor_id") for item in contributors):
        issues.append(f"{path}: contributors must include the owner actor_id")


def git_output(root: Path, *arguments: str) -> str | None:
    try:
        result = subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=False)
    except OSError:
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def validate_git_evidence(root: Path, phases: list[Phase], issues: list[str]) -> None:
    if git_output(root, "rev-parse", "--is-inside-work-tree") != "true":
        return
    for phase in phases:
        if phase.status == "active":
            continue
        metadata = phase.path / "phase.toml"
        evidence = phase.data.get("evidence", {})
        base, last = str(evidence.get("base_commit", "")), str(evidence.get("last_commit", ""))
        last_details = git_output(root, "show", "-s", "--format=%H%x00%an%x00%ae", last)
        base_exists = git_output(root, "rev-parse", "--verify", f"{base}^{{commit}}")
        if not base_exists:
            issues.append(f"{metadata}: evidence.base_commit does not resolve to a commit")
        if not last_details:
            issues.append(f"{metadata}: evidence.last_commit does not resolve to a commit")
            continue
        _, author_name, author_email = last_details.split("\x00", maxsplit=2)
        contributors = phase.data.get("contributors", [])
        if not any(isinstance(item, dict) and item.get("name") == author_name and item.get("email") == author_email for item in contributors):
            issues.append(f"{metadata}: last commit author must appear in [[contributors]]")
        changed = git_output(root, "show", "--pretty=", "--name-only", last) if base == last else git_output(root, "diff", "--name-only", base, last)
        changed_files = {line for line in (changed or "").splitlines() if line}
        listed_files = set(evidence.get("modified_files", []))
        if changed is not None and not listed_files.intersection(changed_files):
            issues.append(f"{metadata}: evidence.modified_files must include a file changed by the recorded commit range")


def validate(root: Path) -> tuple[list[str], list[Phase]]:
    issues: list[str] = []
    validate_root(root, issues)
    phases = discover_phases(root, issues)
    phase_map = {phase.phase_id: phase for phase in phases}
    if len(phase_map) != len(phases):
        issues.append("duplicate phase IDs")
    for phase in phases:
        validate_phase(phase, phase_map, issues)
    validate_git_evidence(root, phases, issues)
    return issues, phases


def required_for_action(phase: Phase, phases: dict[str, Phase], action: str) -> list[str]:
    requirements: list[str] = []
    status = phase.status
    checks = phase.data.get("checks", {})
    evidence = phase.data.get("evidence", {})
    dependencies = phase.metadata.get("depends_on", [])
    previous = {
        "record": "active",
        "ready-for-human-test": "recorded",
        "verify": "ready-for-human-test",
        "close": "verified",
    }
    if action not in previous:
        return [f"unknown action {action}"]
    if status != previous[action]:
        return [f"action {action} requires phase status {previous[action]}, found {status}"]
    if action == "record":
        for key in ("base_commit", "last_commit"):
            if not str(evidence.get(key, "")).strip():
                requirements.append(f"evidence.{key} is required")
        if not evidence.get("modified_files"):
            requirements.append("evidence.modified_files is required")
    if action == "ready-for-human-test" and checks.get("build") not in {"pass", "skipped"}:
        requirements.append("checks.build must be pass or skipped")
    if action == "verify":
        if checks.get("test") != "pass":
            requirements.append("checks.test must be pass")
        for key in ("human_confirmed_by", "human_confirmed_at"):
            if not str(checks.get(key, "")).strip():
                requirements.append(f"checks.{key} is required")
    if action == "close":
        for dependency in dependencies:
            dependency_phase = phases.get(dependency)
            if dependency_phase is None or dependency_phase.status != "closed":
                requirements.append(f"dependency {dependency} must be closed")
    return requirements


def render_index(phases: list[Phase]) -> str:
    lines = [MARKER_BEGIN, "| ID | Phase | 状态 | Owner | Last commit |", "|---|---|---|---|---|"]
    for phase in sorted(phases, key=lambda current: current.phase_id):
        owner = phase.data.get("owner", {})
        evidence = phase.data.get("evidence", {})
        lines.append(
            f"| {phase.phase_id} | {phase.slug} | {phase.status} | {owner.get('name', '')} | {evidence.get('last_commit', '')} |"
        )
    lines.append(MARKER_END)
    return "\n".join(lines)


def command_init(root: Path) -> int:
    columbina = root / ".columbina"
    columbina.mkdir(exist_ok=True)
    (columbina / "phase").mkdir(exist_ok=True)
    (columbina / "contributors").mkdir(exist_ok=True)
    workflow = columbina / "workflow.toml"
    context = columbina / "CONTEXT.md"
    if not workflow.exists():
        workflow.write_text('[workflow]\nversion = 2\nmode = "strict"\n', encoding="utf-8")
    if not context.exists():
        context.write_text(
            "# Columbina Context\n\n## 当前项目状态\n\n待确认。\n\n## Phase 索引\n\n"
            + render_index([])
            + "\n",
            encoding="utf-8",
        )
    print(f"initialized {columbina}")
    return 0


def toml_string(text: str) -> str:
    return json.dumps(text, ensure_ascii=False)


def command_phase_init(root: Path, phase_id: str, slug: str, title: str) -> int:
    issues, _ = validate(root)
    if issues:
        print("cannot create phase in invalid workflow:\n" + "\n".join(f"- {issue}" for issue in issues))
        return 1
    if not re.fullmatch(r"\d{4}", phase_id) or not re.fullmatch(r"[a-z0-9][a-z0-9-]*", slug):
        print("phase ID must be four digits and slug must be lowercase hyphen-case")
        return 1
    details, missing = identity_details(root)
    if details is None:
        print("cannot create phase; missing " + ", ".join(missing))
        return 1
    directory = root / ".columbina" / "phase" / f"{phase_id}_{slug}"
    if directory.exists():
        print(f"phase already exists: {directory}")
        return 1
    command_identity_context(root)
    directory.mkdir(parents=True)
    metadata = f'''[phase]
id = {toml_string(phase_id)}
slug = {toml_string(slug)}
title = {toml_string(title)}
status = "active"
depends_on = []

[owner]
actor_id = {toml_string(details['actor_id'])}
name = {toml_string(details['name'])}
email = {toml_string(details['email'])}

[evidence]
base_commit = ""
last_commit = ""
modified_files = []

[checks]
build = "pending"
build_note = ""
test = "not-run"
human_confirmed_by = ""
human_confirmed_at = ""

[[contributors]]
actor_id = {toml_string(details['actor_id'])}
name = {toml_string(details['name'])}
email = {toml_string(details['email'])}
branch = {toml_string(git_output(root, 'branch', '--show-current') or '')}
commits = []
role = "owner"
'''
    (directory / "phase.toml").write_text(metadata, encoding="utf-8")
    (directory / "CONTEXT.md").write_text(f"# Phase {phase_id}: {title}\n\n## 阶段目标\n\n待填写。\n", encoding="utf-8")
    (directory / "test.md").write_text(f"# Test Record: Phase {phase_id}\n\n## 测试状态\n\n未测试。\n", encoding="utf-8")
    (directory / "debug.md").write_text(f"# Debug Record: Phase {phase_id}\n\n暂无调试记录。\n", encoding="utf-8")
    print(f"created {directory}")
    return 0


def command_phase_advance(root: Path, phase_id: str, target: str) -> int:
    action_by_target = {
        "recorded": "record",
        "ready-for-human-test": "ready-for-human-test",
        "verified": "verify",
        "closed": "close",
    }
    action = action_by_target[target]
    issues, phases = validate(root)
    phase_map = {phase.phase_id: phase for phase in phases}
    if issues or phase_id not in phase_map:
        for issue in issues + ([] if phase_id in phase_map else [f"phase {phase_id} does not exist"]):
            print(f"- {issue}")
        return 1
    missing = required_for_action(phase_map[phase_id], phase_map, action)
    if missing:
        print("transition blocked:\n" + "\n".join(f"- {item}" for item in missing))
        return 1
    metadata = phase_map[phase_id].path / "phase.toml"
    lines = metadata.read_text(encoding="utf-8").splitlines(keepends=True)
    in_phase = False
    for index, line in enumerate(lines):
        if line.strip() == "[phase]":
            in_phase = True
        elif in_phase and line.startswith("["):
            in_phase = False
        elif in_phase and re.match(r"\s*status\s*=", line):
            lines[index] = f'status = "{target}"\n'
            break
    else:
        print(f"{metadata}: unable to locate [phase].status")
        return 1
    metadata.write_text("".join(lines), encoding="utf-8")
    print(f"advanced phase {phase_id} to {target}")
    return command_sync(root, False)


def next_step(phase: Phase, phases: dict[str, Phase]) -> dict[str, Any] | None:
    transitions = {
        "active": ("record", "recorded"),
        "recorded": ("ready-for-human-test", "ready-for-human-test"),
        "ready-for-human-test": ("verify", "verified"),
        "verified": ("close", "closed"),
    }
    transition = transitions.get(phase.status)
    if transition is None:
        return None
    action, target = transition
    missing = required_for_action(phase, phases, action)
    return {"action": action, "target_status": target, "allowed": not missing, "missing": missing}


def matched_values(phase: Phase, query: str) -> list[dict[str, str]]:
    needle = query.casefold()
    matches: list[dict[str, str]] = []

    def add(field: str, candidate: Any) -> None:
        if isinstance(candidate, str) and needle in candidate.casefold():
            matches.append({"field": field, "value": candidate})

    add("phase.id", phase.phase_id)
    add("phase.slug", phase.slug)
    for section, keys in (("phase", ("title", "status")), ("owner", ("actor_id", "name", "email")), ("evidence", ("base_commit", "last_commit"))):
        for key in keys:
            add(f"{section}.{key}", value(phase.data, section, key))
    for filename in value(phase.data, "evidence", "modified_files") or []:
        add("evidence.modified_files", filename)
    for contributor in phase.data.get("contributors", []):
        if isinstance(contributor, dict):
            for key in ("actor_id", "name", "email", "branch", "role"):
                add(f"contributors.{key}", contributor.get(key))
            for commit in contributor.get("commits", []):
                add("contributors.commits", commit)
    for document_name in ("CONTEXT.md", "test.md", "debug.md"):
        document = phase.path / document_name
        if document.exists() and needle in document.read_text(encoding="utf-8").casefold():
            matches.append({"field": document_name, "value": "matched document content"})
    return matches


def command_phase_search(root: Path, query: str, as_json: bool) -> int:
    issues, all_phases = validate(root)
    phase_map = {phase.phase_id: phase for phase in all_phases}
    results = []
    for phase in all_phases:
        matches = matched_values(phase, query)
        if matches:
            results.append(
                {
                    "id": phase.phase_id,
                    "slug": phase.slug,
                    "title": value(phase.data, "phase", "title"),
                    "status": phase.status,
                    "path": str(phase.path),
                    "matches": matches,
                    "next_step": next_step(phase, phase_map),
                }
            )
    payload = {"query": query, "matches": results, "validation_issues": issues}
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    elif not results:
        print(f"NO_MATCH: {query}")
    else:
        for result in results:
            next_result = result["next_step"]
            step = "no further transition" if next_result is None else f"next {next_result['target_status']} ({'allowed' if next_result['allowed'] else 'blocked'})"
            print(f"{result['id']}_{result['slug']} | {result['status']} | {step}")
            for match in result["matches"]:
                print(f"- {match['field']}: {match['value']}")
    return 0 if results else 1


def git_config(root: Path, key: str) -> str:
    try:
        result = subprocess.run(["git", "config", "--get", key], cwd=root, capture_output=True, text=True, check=False)
    except OSError:
        return ""
    return result.stdout.strip() if result.returncode == 0 else ""


def command_identity(root: Path) -> int:
    name, email = git_config(root, "user.name"), git_config(root, "user.email")
    if not name or not email:
        print(json.dumps({"ok": False, "name": name, "email": email, "missing": [key for key, item in (("user.name", name), ("user.email", email)) if not item]}, ensure_ascii=False))
        return 1
    actor_id = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "contributor"
    actor_id += "-" + hashlib.sha256(email.lower().encode("utf-8")).hexdigest()[:8]
    print(json.dumps({"ok": True, "name": name, "email": email, "actor_id": actor_id, "authentication": "not provided; Git identity is attribution only"}, ensure_ascii=False))
    return 0


def identity_details(root: Path) -> tuple[dict[str, str] | None, list[str]]:
    name, email = git_config(root, "user.name"), git_config(root, "user.email")
    missing = [key for key, item in (("user.name", name), ("user.email", email)) if not item]
    if missing:
        return None, missing
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "contributor"
    return {
        "name": name,
        "email": email,
        "actor_id": slug + "-" + hashlib.sha256(email.lower().encode("utf-8")).hexdigest()[:8],
    }, []


def command_identity_context(root: Path) -> int:
    details, missing = identity_details(root)
    if details is None:
        print("cannot create contributor context; missing " + ", ".join(missing))
        return 1
    directory = root / ".columbina" / "contributors" / details["actor_id"]
    context = directory / "CONTEXT.md"
    directory.mkdir(parents=True, exist_ok=True)
    if not context.exists():
        context.write_text(
            f"# Contributor Context: {details['name']}\n\n"
            "## Attribution\n\n"
            f"- actor_id: `{details['actor_id']}`\n"
            f"- Git name: `{details['name']}`\n"
            f"- Git email: `{details['email']}`\n"
            "- 说明：仅作 Git 署名追溯，不构成认证。\n\n"
            "## 当前工作项\n\n- 待填写：引用权威 `.columbina/phase/NNNN_slug/`。\n\n"
            "## 分支与交接\n\n- 待填写。\n",
            encoding="utf-8",
        )
        print(f"created {context}")
    else:
        print(f"preserved {context}")
    return 0


def command_check(root: Path, as_json: bool) -> int:
    issues, phases = validate(root)
    payload = {"ok": not issues, "issues": issues, "phases": [{"id": phase.phase_id, "status": phase.status} for phase in phases]}
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    elif issues:
        print("INVALID")
        print("\n".join(f"- {issue}" for issue in issues))
    else:
        print(f"VALID: {len(phases)} phase(s)")
    return 0 if not issues else 1


def command_gate(root: Path, phase_id: str, action: str, as_json: bool) -> int:
    issues, all_phases = validate(root)
    phase_map = {phase.phase_id: phase for phase in all_phases}
    if phase_id not in phase_map:
        issues.append(f"phase {phase_id} does not exist")
        requirements: list[str] = []
    else:
        requirements = required_for_action(phase_map[phase_id], phase_map, action)
    allowed = not issues and not requirements
    payload = {"allowed": allowed, "phase": phase_id, "action": action, "validation_issues": issues, "missing": requirements}
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("ALLOWED" if allowed else "BLOCKED")
        for issue in issues + requirements:
            print(f"- {issue}")
    return 0 if allowed else 1


def command_sync(root: Path, check_only: bool) -> int:
    issues, phases = validate(root)
    if issues:
        print("cannot sync invalid records:\n" + "\n".join(f"- {issue}" for issue in issues))
        return 1
    context = root / ".columbina" / "CONTEXT.md"
    text = context.read_text(encoding="utf-8")
    replacement = render_index(phases)
    current = re.search(re.escape(MARKER_BEGIN) + r".*?" + re.escape(MARKER_END), text, flags=re.DOTALL)
    assert current is not None
    updated = text[: current.start()] + replacement + text[current.end() :]
    if check_only:
        if updated != text:
            print("OUT_OF_SYNC: run sync")
            return 1
        print("IN_SYNC")
        return 0
    if updated != text:
        context.write_text(updated, encoding="utf-8")
        print("synchronized root phase index")
    else:
        print("already synchronized")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="project root (default: current directory)")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("init")
    identity = subparsers.add_parser("identity")
    identity_sub = identity.add_subparsers(dest="identity_command", required=True)
    identity_sub.add_parser("doctor")
    identity_sub.add_parser("init-context")
    phase = subparsers.add_parser("phase")
    phase_sub = phase.add_subparsers(dest="phase_command", required=True)
    phase_init = phase_sub.add_parser("init")
    phase_init.add_argument("--id", required=True, dest="phase_id")
    phase_init.add_argument("--slug", required=True)
    phase_init.add_argument("--title", required=True)
    phase_advance = phase_sub.add_parser("advance")
    phase_advance.add_argument("--id", required=True, dest="phase_id")
    phase_advance.add_argument("--to", required=True, choices=("recorded", "ready-for-human-test", "verified", "closed"))
    phase_search = phase_sub.add_parser("search")
    phase_search.add_argument("--query", required=True)
    phase_search.add_argument("--json", action="store_true")
    check = subparsers.add_parser("check")
    check.add_argument("--strict", action="store_true", help="accepted for CI readability; v2 validates strictly")
    check.add_argument("--json", action="store_true")
    gate = subparsers.add_parser("gate")
    gate.add_argument("--phase", required=True, help="four-digit phase ID")
    gate.add_argument("--action", required=True, choices=("record", "ready-for-human-test", "verify", "close"))
    gate.add_argument("--json", action="store_true")
    sync = subparsers.add_parser("sync")
    sync.add_argument("--check", action="store_true", dest="check_only")
    args = parser.parse_args()
    root = args.root.resolve()
    if args.command == "init":
        return command_init(root)
    if args.command == "identity":
        return command_identity_context(root) if args.identity_command == "init-context" else command_identity(root)
    if args.command == "phase":
        if args.phase_command == "init":
            return command_phase_init(root, args.phase_id, args.slug, args.title)
        if args.phase_command == "advance":
            return command_phase_advance(root, args.phase_id, args.to)
        return command_phase_search(root, args.query, args.json)
    if args.command == "check":
        return command_check(root, args.json)
    if args.command == "gate":
        return command_gate(root, args.phase, args.action, args.json)
    return command_sync(root, args.check_only)


if __name__ == "__main__":
    raise SystemExit(main())
