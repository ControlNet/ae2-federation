"""Phase 4 quest progression: one plain AE2 base and one federated base, grown order by order across server processes.

Each segment is one NAST server process running the playthroughProgression GameTest against the run directory's saved
world. A segment stops at an order boundary after --segment-seconds and saves its bases beside the world; the next
segment resumes them. Before each segment the world and the saved state are copied to a checkpoint. A segment whose
process deadline cut an order leaves a half-finished order in the saved world, so the checkpoint is restored and the
segment is repeated, stopping after the orders it did finish; an order that alone outlasts a process is deferred.

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


def decide(report: dict | None) -> tuple[str, dict[str, str]]:
    """What follows a segment: done, continue, fail, or retry from its checkpoint with these extra properties."""
    if report is None or report.get("harnessError"):
        return "fail", {}
    if report["cleanBoundary"]:
        return ("done", {}) if report["completed"] or report["stopReason"] else ("continue", {})
    finished = report["ordersThisProcess"]
    # The repeat stops before the order the deadline cut; an order that alone outlasts a process is deferred.
    return "retry", ({PREFIX + "progression.maxOrders": str(finished)} if finished > 0
                     else {PREFIX + "progression.deferNext": "true"})


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
    parser.add_argument("--chapter", default="ae2", help="Phase 1 chapter filter")
    parser.add_argument("--max-targets", type=int, default=0)
    parser.add_argument("--segment-seconds", type=int, default=900, help="each process stops at the first order boundary after this")
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
    if args.from_checkpoint:
        properties = json.loads((args.from_checkpoint / "properties.json").read_text())
    else:
        properties = {PREFIX + "chapter": args.chapter, PREFIX + "progression.maxTargets": str(args.max_targets),
                      PREFIX + "progression.stopAfterSeconds": str(args.segment_seconds),
                      PREFIX + "machinePath": "projection"}
    for value in args.jvm_property:
        key, separator, setting = value.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z0-9_.-]+", key):
            parser.error("JVM properties must be KEY=VALUE")
        properties[key] = setting
    for key in (PREFIX + "progression.resume", PREFIX + "progression.maxOrders", PREFIX + "progression.deferNext"):
        if key in properties:
            parser.error(f"{key} is owned by this tool")
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
    timeout = args.segment_seconds + BOOT_SECONDS + 600
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
            action, extra = decide(report)
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
        if action in ("done", "fail"):
            status = "completed" if action == "done" and report["completed"] else "stopped" if action == "done" else "failed"
            break
    print(f"PROGRESSION {status}: {output}", flush=True)
    return 0 if status in ("completed", "stopped") else 1


if __name__ == "__main__":
    raise SystemExit(main())
