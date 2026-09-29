"""Development GameTest runner: one GameTest server runs many tests, one after another.

The CI gate (tools/required_gametests.py) starts one server per test so every test sees a fresh level; that isolation
is what evidence runs rely on. This runner is for quick local iteration only: the tests share one level, each in its own
batch, in the requested order. A test that fails in the batch runs again alone: SHARED means it passes alone and only
another test's leftover level state failed it; a real failure fails alone too.

    python3 tools/dev_gametests.py claimcompete endpointlocal productionproviderthreeway
    python3 tools/dev_gametests.py --manifest            # every required manifest GameTest
    python3 tools/dev_gametests.py --manifest --match productionprovider
"""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))


def manifest_ids() -> list[str]:
    from required_gametests import required_test_ids
    return required_test_ids()


def outcomes(output: str, test_ids: list[str]) -> dict[str, str]:
    """PASS/FAIL/CRASH per requested id, or MISSING when the test's batch never started. The GameTest server logs only
    failures, so a test passed when its batch ran, a later batch started or the run completed, and no failure names it.
    A crash ends the server: the test whose batch was running when it happened is CRASH."""
    started = [name.lower() for name in re.findall(r"Running test batch '[^/']*/([A-Za-z0-9_]+):\d+'", output)]
    completed = re.search(r"=+ \d+ GAME TESTS COMPLETE IN", output) is not None
    failed = {}
    for name, reason in re.findall(r"\]: ([A-Za-z0-9_.]+) failed at [^!]*! (.*)", output):
        failed[name.lower().rsplit(".", 1)[-1]] = reason.strip()
    crashed = None if completed or not started else started[-1]
    result = {}
    for test_id in test_ids:
        key = test_id.lower()
        if key in failed:
            result[test_id] = "FAIL " + failed[key]
        elif key == crashed:
            result[test_id] = "CRASH " + crash_reason(output)
        elif key in started:
            result[test_id] = "PASS"
        else:
            result[test_id] = "MISSING"
    return result


def crash_reason(output: str) -> str:
    """The innermost cause of the crash report, or the last log line when the server stopped without one."""
    report = output[output.find("---- Minecraft Crash Report ----"):] if "---- Minecraft Crash Report ----" in output else ""
    causes = re.findall(r"^(?:Caused by: )?([a-zA-Z_$.]+(?:Exception|Error): .*)$", report, re.MULTILINE)
    if causes:
        return causes[-1].strip()
    lines = [line for line in output.splitlines() if line.strip()]
    return lines[-1].strip() if lines else "server produced no output"


def run(test_ids: list[str], selection: str = "batch") -> str:
    command = [str(ROOT / "gradlew"), ":neoforge-1.21.1:runGameTestServer",
               "-PfederationGameTestSelection=" + selection, "-PfederationGameTestId=" + ",".join(test_ids),
               "--dependency-verification=strict", "--no-daemon", "--console=plain"]
    return subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                          check=False).stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("test_ids", nargs="*")
    parser.add_argument("--manifest", action="store_true", help="run every required manifest GameTest")
    parser.add_argument("--match", default="", help="with --manifest: only ids containing this text")
    parser.add_argument("--log", default=str(ROOT / "build" / "dev-gametest.log"))
    parser.add_argument("--no-isolate", action="store_true",
                        help="do not rerun batch failures alone in their own server")
    args = parser.parse_args()
    test_ids = list(args.test_ids)
    if args.manifest:
        test_ids += [test_id for test_id in manifest_ids() if args.match in test_id]
    test_ids = list(dict.fromkeys(test_ids))
    if not test_ids:
        parser.error("no GameTest ids given")
    started = time.monotonic()
    log = Path(args.log)
    log.parent.mkdir(parents=True, exist_ok=True)
    report: dict[str, str] = {}
    remaining = test_ids
    logs = []
    # A crash stops the server; start a new one with the tests after the one that crashed.
    while remaining:
        output = run(remaining)
        logs.append(output)
        round_report = outcomes(output, remaining)
        report.update(round_report)
        if not any(outcome.startswith("CRASH") for outcome in round_report.values()):
            break
        crashed = next(index for index, test_id in enumerate(remaining) if round_report[test_id].startswith("CRASH"))
        remaining = remaining[crashed + 1:]
    # Tests share one level in a batch, and level-wide data such as rules and identities outlives each test. A test that
    # fails in the batch runs again alone, as CI runs it, to tell a real failure from one another test's leftovers caused.
    if not args.no_isolate:
        for test_id in [test_id for test_id in test_ids if report[test_id].startswith(("FAIL", "CRASH"))]:
            output = run([test_id], "positive")
            logs.append(output)
            if "All 1 required tests passed" in output:
                report[test_id] = "SHARED passes alone; batch: " + report[test_id]
            else:
                report[test_id] += "  (also fails alone)"
    log.write_text("\n".join(logs))
    for test_id in test_ids:
        outcome = report[test_id]
        status, _, reason = outcome.partition(" ")
        print(f"{status:7} {test_id}" + (f"  {reason}" if reason else ""))
    failures = [test_id for test_id in test_ids if not report[test_id].startswith(("PASS", "SHARED"))]
    shared = sum(report[test_id].startswith("SHARED") for test_id in test_ids)
    if shared:
        print(f"SHARED: {shared} test(s) failed in the batch only, from state earlier tests left in the level")
    print(f"{len(test_ids) - len(failures)}/{len(test_ids)} passed in {time.monotonic() - started:.0f}s; log: {log}")
    if all(report[test_id] == "MISSING" for test_id in test_ids):
        print("\n".join(logs[-1].splitlines()[-25:]))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
