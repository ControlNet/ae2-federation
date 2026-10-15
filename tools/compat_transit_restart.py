"""Verify the transit recovery matrix across three real server processes sharing one saved world.

Uses the pinned AE2 baseline and a separate compatibility test mod, not a production-client proof.
Three actual items per case are extracted from native storage then explicitly injected into transit.
All block changes, split/copy identity detection, process restarts and recovery commands are real.

    python3 tools/compat_transit_restart.py --accept-eula
"""
import argparse
import json
import subprocess
import time

import compat_run


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-eula", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Starting Minecraft accepts its EULA; pass --accept-eula")
    if not args.no_build:
        compat_run.build()
    profile = compat_run.load_profiles()["baseline"]
    compat_run.neoforge_server(profile["neoforge"])
    directory = compat_run.RUNS / f"transit-recovery-restart-{time.time_ns()}"
    compat_run.assemble(profile, directory)
    outcomes = []
    for phase in ("prepare", "recover", "verify"):
        report = directory / f"{phase}-report.json"
        command = ["java", "-Xmx4G", "-Dae2federation.compat.tests=transitrecoveryrestartmatrix",
                   f"-Dae2federation.compat.report={report}", "-Dae2federation.compat.parallel=1",
                   f"-Dae2federation.compat.transitRestartPhase={phase}",
                   f"-Dae2federation.compat.originX={4096 * ('prepare', 'recover', 'verify').index(phase)}",
                   f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/unix_args.txt", "nogui"]
        started = time.monotonic()
        with (directory / f"{phase}-console.log").open("w") as stream:
            try:
                process = subprocess.run(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=stream,
                                         stderr=subprocess.STDOUT, timeout=300, check=False)
            except subprocess.TimeoutExpired:
                print(f"FAIL {phase}: server did not exit; see {directory}", flush=True)
                return 1
        if not report.exists():
            print(f"FAIL {phase}: missing report; see {directory}", flush=True)
            return 1
        data = json.loads(report.read_text())
        failures = [test for test in data.get("tests", []) if test.get("status") != "pass"]
        success = process.returncode == 0 and not data.get("setupError") and len(data.get("tests", [])) == 1 and not failures
        outcomes.append({"phase": phase, "passed": success, "seconds": round(time.monotonic() - started, 1),
                         "returncode": process.returncode, "report": str(report), "failures": failures})
        (directory / "restart-summary.json").write_text(json.dumps(outcomes, indent=2) + "\n")
        print(f"{'PASS' if success else 'FAIL'} {phase}: {directory}", flush=True)
        if not success:
            print(json.dumps(failures, indent=2), flush=True)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
