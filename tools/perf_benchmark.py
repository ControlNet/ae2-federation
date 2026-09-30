"""Performance benchmark runner for the manual-only PerformanceBenchmarkGameTests.

Each benchmark GameTest runs in its own GameTest server (``positive`` selection) and logs
``AE2F_PERF test=<id> metric=<name> value=<v> unit=<u>`` lines. This tool runs the suite ``--repeat`` times, keeps the
median of every metric, writes it to ``.omo/evidence/perf/<label>.json`` and, with ``--compare``, prints the change
against an earlier result. Numbers are wall-clock and host dependent: compare only results from the same host.

    python3 tools/perf_benchmark.py --label baseline
    python3 tools/perf_benchmark.py --remote user@example.org --key ~/.ssh/id --label fix1 --compare baseline
    python3 tools/perf_benchmark.py --tests perfenergymesh -P federationPerfMeshSize=4 --label mesh4

``--tree`` benchmarks another checkout, such as ``git worktree add`` of an earlier commit. ``--remote`` copies the
working tree (tracked plus untracked, non-ignored files) to ``<remote-root>/src`` and runs
Gradle there with HOME, GRADLE_USER_HOME, TMPDIR and XDG_CACHE_HOME inside ``<remote-root>``.
"""
import argparse
import json
import re
import shlex
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ["perfenergymesh", "perfstorageprojection", "perftopologyplane", "perfdomainmenu"]
EVIDENCE = ROOT / ".omo" / "evidence" / "perf"
LINE = re.compile(r"AE2F_PERF test=(\S+) metric=(\S+) value=(\S+) unit=(\S+)")
REMOTE_ENV = ("JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 HOME={root} GRADLE_USER_HOME={root}/.gradle "
              "TMPDIR={root}/tmp XDG_CACHE_HOME={root}/.cache "
              "JAVA_TOOL_OPTIONS='-Duser.home={root} -Djava.io.tmpdir={root}/tmp'")


def parse(output: str) -> dict[str, tuple[float, str]]:
    return {f"{test}.{metric}": (float(value), unit) for test, metric, value, unit in LINE.findall(output)}


def gradle_command(test_id: str, properties: list[str]) -> list[str]:
    return ["./gradlew", ":neoforge-1.21.1:runGameTestServer", "-PfederationGameTestSelection=positive",
            "-PfederationGameTestId=" + test_id, *("-P" + prop for prop in properties),
            "--dependency-verification=strict", "--no-daemon", "--console=plain"]


def ssh(args: argparse.Namespace) -> list[str]:
    command = ["ssh", "-o", "BatchMode=yes"]
    if args.key:
        command += ["-i", str(Path(args.key).expanduser())]
    return command + [args.remote]


def remote_home(args: argparse.Namespace) -> str:
    return subprocess.run(ssh(args) + ['printf %s "$HOME"'], check=True, stdout=subprocess.PIPE,
                          text=True).stdout


def sync(args: argparse.Namespace) -> None:
    files = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=args.tree, check=True,
                           stdout=subprocess.PIPE).stdout
    subprocess.run(ssh(args) + [f"mkdir -p {args.remote_root}/src {args.remote_root}/tmp {args.remote_root}/.cache"],
                   check=True)
    rsh = " ".join(shlex.quote(part) for part in ssh(args)[:-1])
    subprocess.run(["rsync", "-a", "--from0", "--files-from=-", "-e", rsh, "./",
                    f"{args.remote}:{args.remote_root}/src/"], cwd=args.tree, input=files, check=True)


def run_once(args: argparse.Namespace, test_id: str) -> str:
    command = gradle_command(test_id, args.property)
    if args.remote:
        env = REMOTE_ENV.format(root=args.remote_root)
        remote = f"cd {args.remote_root}/src && env {env} " + " ".join(shlex.quote(part) for part in command)
        command = ssh(args) + [remote]
        cwd = None
    else:
        cwd = args.tree
    return subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                          check=False).stdout


def load(label_or_path: str) -> dict:
    path = Path(label_or_path)
    if not path.exists():
        path = EVIDENCE / f"{label_or_path}.json"
    return json.loads(path.read_text())


def compare(current: dict, baseline: dict) -> None:
    print(f"{'metric':52} {'baseline':>14} {'current':>14} {'change':>9}")
    for metric, entry in current["metrics"].items():
        before = baseline["metrics"].get(metric)
        now = entry["median"]
        if before is None:
            print(f"{metric:52} {'-':>14} {now:14.1f} {'new':>9}")
            continue
        base = before["median"]
        change = "n/a" if base == 0 else f"{(now - base) / base * 100:+.1f}%"
        print(f"{metric:52} {base:14.1f} {now:14.1f} {change:>9}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tests", nargs="*", default=TESTS)
    parser.add_argument("--repeat", type=int, default=3)
    parser.add_argument("--label", required=True)
    parser.add_argument("--compare", help="label or JSON path of an earlier result")
    parser.add_argument("-P", "--property", action="append", default=[], help="extra Gradle property, key=value")
    parser.add_argument("--remote", help="user@host to run on instead of this machine")
    parser.add_argument("--key", help="SSH identity file for --remote")
    parser.add_argument("--remote-root", help="directory on the remote host "
                        "(default: ae2f/perf in the remote login's home)")
    parser.add_argument("--tree", type=Path, default=ROOT,
                        help="checkout to benchmark, e.g. a git worktree of an earlier commit (default: this one)")
    args = parser.parse_args()
    if args.remote and not args.remote_root:
        args.remote_root = remote_home(args) + "/ae2f/perf"
    if args.remote:
        sync(args)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    samples: dict[str, list[float]] = {}
    units: dict[str, str] = {}
    failed = []
    log = EVIDENCE / f"{args.label}.log"
    with log.open("w") as handle:
        for repeat in range(args.repeat):
            for test_id in args.tests:
                started = time.monotonic()
                output = run_once(args, test_id)
                handle.write(output)
                passed = "All 1 required tests passed" in output
                metrics = parse(output)
                print(f"[{repeat + 1}/{args.repeat}] {test_id}: {'PASS' if passed else 'FAIL'} "
                      f"{len(metrics)} metrics in {time.monotonic() - started:.0f}s", flush=True)
                if not passed:
                    failed.append(f"{test_id}#{repeat + 1}")
                    failures = re.findall(r"failed at [^!]*! (.*)", output)
                    print("  " + (failures[-1] if failures else output.strip().splitlines()[-1]), flush=True)
                    continue
                for metric, (value, unit) in metrics.items():
                    samples.setdefault(metric, []).append(value)
                    units[metric] = unit
    result = {
        "label": args.label,
        "host": args.remote or "local",
        "properties": args.property,
        "commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.tree, stdout=subprocess.PIPE,
                                 text=True).stdout.strip(),
        "dirty": bool(subprocess.run(["git", "status", "--porcelain"], cwd=args.tree, stdout=subprocess.PIPE,
                                     text=True).stdout.strip()),
        "failed": failed,
        "metrics": {metric: {"median": statistics.median(values), "samples": values, "unit": units[metric]}
                    for metric, values in samples.items()},
    }
    out = EVIDENCE / f"{args.label}.json"
    out.write_text(json.dumps(result, indent=2) + "\n")
    print(f"wrote {out} (log {log})")
    if args.compare:
        compare(result, load(args.compare))
    else:
        for metric, entry in result["metrics"].items():
            print(f"{metric:52} {entry['median']:14.1f} {entry['unit']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
