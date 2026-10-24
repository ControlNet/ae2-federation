"""Phase 4 quest progression: one plain AE2 base and one federated base, grown order by order across server processes.

Each segment is one NAST server process running the playthroughProgression GameTest against the run directory's saved
world. A segment stops at an order boundary after --segment-seconds and saves its bases beside the world; the next
segment resumes them. Before each segment the world and the saved state are copied to a checkpoint. A segment whose
process deadline cut an order leaves a half-finished order in the saved world, so the checkpoint is restored and the
segment is repeated, stopping after the orders it did finish; an order that alone outlasts a process is deferred. A
segment that reached a clean boundary but whose server did not exit cleanly may not have saved its world, so it is
repeated the same way. A stop for a divergence, an unsettled base or failed restart checks ends the tool with exit 1.

--resume and --from-checkpoint continue with the saved run's properties; only --segment-seconds and the properties in
SAFE_OVERRIDES may change, and any other value that differs from the saved one is refused.

    python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2 --chapter ae2
    python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2 --resume
    python3 tools/compat_playthrough_progression.py --accept-eula --no-build --run-suffix ae2-confirm \\
        --from-checkpoint neoforge-1.21.1/build/compat/runs/nast-progression-ae2-checkpoints/segment-3 --segments 1

Reports: build/playthrough/progression-<suffix>/playthrough-progression.{json,md}, one copy per segment, and
segments.json with every process and retry.
"""
import argparse
import json
import re
import shutil
import time
from pathlib import Path

import compat_run

STATE = "playthrough-progression-state.json"
REPORT = "playthrough-progression.json"
PREFIX = "ae2federation.playthrough."
# A NAST server needs a few minutes to start; the segment's own stop and wall limits come on top.
BOOT_SECONDS = 600
MAX_RETRIES = 2
OWNED = (PREFIX + "progression.resume", PREFIX + "progression.maxOrders", PREFIX + "progression.deferNext")
# Properties that change only how long a process runs, never which orders run or how they are checked.
SAFE_OVERRIDES = frozenset({PREFIX + "progression.stopAfterSeconds", PREFIX + "wallSeconds", PREFIX + "sprint"})


def decide(report: dict | None, server: dict) -> tuple[str, dict[str, str]]:
    """What follows a segment: done, continue, fail (harness), failed-stop, or retry from its checkpoint with these
    extra properties."""
    if report is None or report.get("harnessError"):
        return "fail", {}
    if report["stopReason"]:
        return "failed-stop", {}
    finished = report["ordersThisProcess"]
    if report["cleanBoundary"]:
        if server.get("status") == "pass" and server.get("exit") == 0:
            return ("done", {}) if report["completed"] else ("continue", {})
        # The process reached a boundary, but its world may not have been saved: repeat the orders it finished.
        return "retry", ({PREFIX + "progression.maxOrders": str(finished)} if finished > 0 else {})
    # The repeat stops before the order the deadline cut; an order that alone outlasts a process is deferred.
    return "retry", ({PREFIX + "progression.maxOrders": str(finished)} if finished > 0
                     else {PREFIX + "progression.deferNext": "true"})


def parse_properties(values: list[str]) -> dict[str, str]:
    properties = {}
    for value in values:
        key, separator, setting = value.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z0-9_.-]+", key):
            raise ValueError("JVM properties must be KEY=VALUE")
        if key in OWNED:
            raise ValueError(f"{key} is owned by this tool")
        properties[key] = setting
    return properties


def requested(args: argparse.Namespace) -> dict[str, str]:
    """The properties this command line sets explicitly."""
    result = {}
    if args.chapter is not None:
        result[PREFIX + "chapter"] = args.chapter
    if args.max_targets is not None:
        result[PREFIX + "progression.maxTargets"] = str(args.max_targets)
    if args.segment_seconds is not None:
        result[PREFIX + "progression.stopAfterSeconds"] = str(args.segment_seconds)
    result.update(parse_properties(args.jvm_property))
    return result


def resolve_properties(args: argparse.Namespace, saved: dict[str, str] | None) -> dict[str, str]:
    """A new run's properties from the command line, or a saved run's with the command line's safe overrides."""
    overrides = requested(args)
    if saved is None:
        properties = {PREFIX + "chapter": "ae2", PREFIX + "progression.maxTargets": "0",
                      PREFIX + "progression.stopAfterSeconds": "900", PREFIX + "machinePath": "projection"}
        properties.update(overrides)
        return properties
    properties = dict(saved)
    for key, value in overrides.items():
        if key not in SAFE_OVERRIDES and saved.get(key) != value:
            raise ValueError(f"{key}={value!r} would change the saved run ({saved.get(key)!r}); only --segment-seconds and "
                             f"{', '.join(sorted(SAFE_OVERRIDES))} may change on --resume or --from-checkpoint")
        properties[key] = value
    return properties


def saved_properties(checkpoints: Path) -> dict[str, str]:
    """The properties of a run directory's latest checkpoint, which every segment writes before it starts."""
    saved = sorted(checkpoints.glob("segment-*/properties.json"), key=lambda path: int(path.parent.name.split("-")[1]))
    if not saved:
        raise FileNotFoundError(f"{checkpoints} holds no saved properties")
    return json.loads(saved[-1].read_text())


def snapshot(directory: Path, checkpoint: Path, properties: dict[str, str]) -> None:
    """Copies the saved world and progression state; a checkpoint without a world is the empty start."""
    if checkpoint.exists():
        raise FileExistsError(f"checkpoint {checkpoint} already exists")
    checkpoint.mkdir(parents=True)
    if (directory / "world").exists():
        shutil.copytree(directory / "world", checkpoint / "world", symlinks=True)
    if (directory / STATE).exists():
        shutil.copy2(directory / STATE, checkpoint / STATE)
    (checkpoint / "properties.json").write_text(json.dumps(properties, indent=2, sort_keys=True) + "\n")


def restore(checkpoint: Path, directory: Path) -> None:
    """Puts the run directory's world and state back to the checkpoint."""
    shutil.rmtree(directory / "world", ignore_errors=True)
    (directory / STATE).unlink(missing_ok=True)
    if (checkpoint / "world").exists():
        shutil.copytree(checkpoint / "world", directory / "world", symlinks=True)
    if (checkpoint / STATE).exists():
        shutil.copy2(checkpoint / STATE, directory / STATE)


def segment_properties(base: dict[str, str], resumed: bool, extra: dict[str, str]) -> dict[str, str]:
    settings = dict(base)
    settings[PREFIX + "progression.resume"] = "true" if resumed else "false"
    settings.update(extra)
    return settings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--accept-eula", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--run-suffix", required=True)
    parser.add_argument("--chapter", help="Phase 1 chapter filter (new runs: ae2)")
    parser.add_argument("--max-targets", type=int, help="new runs: 0, all targets")
    parser.add_argument("--segment-seconds", type=int, help="each process stops at the first order boundary after this (new runs: 900)")
    parser.add_argument("--segments", type=int, default=100, help="at most this many segments in this invocation")
    parser.add_argument("--resume", action="store_true", help="continue the run directory's saved progression")
    parser.add_argument("--from-checkpoint", type=Path, help="copy a checkpoint into a new run directory and continue it")
    parser.add_argument("--jvm-property", action="append", default=[], metavar="KEY=VALUE")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.run_suffix):
        parser.error("run suffix must contain only letters, numbers, underscores or hyphens")
    if not args.accept_eula:
        parser.error("starting a Minecraft server accepts its EULA (https://aka.ms/MinecraftEULA): pass --accept-eula")
    if args.resume and args.from_checkpoint:
        parser.error("--resume continues this run directory; --from-checkpoint starts a new one")
    name = f"nast-progression-{args.run_suffix}"
    directory = compat_run.RUNS / name
    checkpoints = compat_run.RUNS / f"{name}-checkpoints"
    output = compat_run.ROOT / "build/playthrough" / f"progression-{args.run_suffix}"
    profile = compat_run.load_profiles()["nast"]
    try:
        if args.from_checkpoint:
            saved = json.loads((args.from_checkpoint / "properties.json").read_text())
        elif args.resume:
            saved = saved_properties(checkpoints)
        else:
            saved = None
        properties = resolve_properties(args, saved)
    except (ValueError, FileNotFoundError) as error:
        parser.error(str(error))
    if not args.no_build:
        compat_run.build()
    for jar in compat_run.built_jars():
        if not jar.exists():
            parser.error(f"{jar} is missing; build without --no-build")
    compat_run.neoforge_server(profile["neoforge"])
    compat_run.unpack_pack(profile["pack"])
    log_path = output / "segments.json"
    if args.resume:
        if not (directory / STATE).exists():
            parser.error(f"{directory} holds no saved progression")
        segments = json.loads(log_path.read_text()) if log_path.exists() else []
    else:
        if directory.exists() or checkpoints.exists() or output.exists():
            parser.error(f"{name} already exists; choose a new --run-suffix or pass --resume")
        compat_run.assemble(profile, directory)
        if args.from_checkpoint:
            restore(args.from_checkpoint, directory)
        segments = []
    output.mkdir(parents=True, exist_ok=True)
    timeout = int(properties[PREFIX + "progression.stopAfterSeconds"]) + BOOT_SECONDS + 600
    status = "stopped"
    for _ in range(args.segments):
        index = len({entry["segment"] for entry in segments})
        checkpoint = checkpoints / f"segment-{index}"
        snapshot(directory, checkpoint, properties)
        extra: dict[str, str] = {}
        for attempt in range(MAX_RETRIES + 1):
            resumed = (directory / STATE).exists()
            settings = segment_properties(properties, resumed, extra)
            (directory / REPORT).unlink(missing_ok=True)
            result = compat_run.run_profile(name, profile, "playthroughProgression", timeout, 1, settings, fresh=False)
            report = json.loads((directory / REPORT).read_text()) if (directory / REPORT).exists() else None
            stem = f"segment-{index}-attempt-{attempt}"
            for source, target in (("server-console.log", stem + "-console.log"), (REPORT, stem + ".json"),
                                   ("playthrough-progression.md", stem + ".md")):
                if (directory / source).exists():
                    shutil.copy2(directory / source, output / target)
            action, extra = decide(report, result)
            segments.append({"segment": index, "attempt": attempt, "checkpoint": str(checkpoint.relative_to(compat_run.ROOT)),
                             "server": {key: result.get(key) for key in ("status", "exit", "seconds", "passed", "total", "detail")},
                             "resumed": resumed, "extraProperties": {key: settings[key] for key in settings if key not in properties},
                             "action": action, "orders": None if report is None else report["orders"],
                             "nextTarget": None if report is None else report["nextTarget"],
                             "ordersThisProcess": None if report is None else report["ordersThisProcess"],
                             "cleanBoundary": None if report is None else report["cleanBoundary"],
                             "incompleteOrder": None if report is None else report["incompleteOrder"]})
            log_path.write_text(json.dumps(segments, indent=2) + "\n")
            print(f"SEGMENT {index}.{attempt} {action}: server {result['status']} ({result['seconds']} s), "
                  f"orders {segments[-1]['orders']}, next target {segments[-1]['nextTarget']}", flush=True)
            if action != "retry":
                break
            restore(checkpoint, directory)
        if action == "retry":
            status = "retries-exhausted"
            break
        if report is not None:
            for source in (REPORT, "playthrough-progression.md"):
                shutil.copy2(directory / source, output / source)
        if action in ("done", "fail", "failed-stop"):
            status = {"done": "completed", "fail": "failed", "failed-stop": "stopped-on-failure"}[action]
            break
    print(f"PROGRESSION {status}: {output}", flush=True)
    if status == "stopped-on-failure":
        print(f"STOP REASON: {report['stopReason']}", flush=True)
    return 0 if status in ("completed", "stopped") else 1


if __name__ == "__main__":
    raise SystemExit(main())
