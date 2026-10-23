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
    parser.add_argument("--playthrough-topology", action="store_true", help="Run the real crafting factory prepare/recover pair on NAST")
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Starting Minecraft accepts its EULA; pass --accept-eula")
    if not args.no_build:
        compat_run.build()
    directory = run_phases(args)
    if directory is None:
        return 1
    if args.playthrough_topology:
        report = directory / "playthrough-topology.json"
        first = json.loads(report.read_text())
        row = first["cases"][0]
        if not row["federated"]["pass"]:
            confirmation_directory = run_phases(args)
            if confirmation_directory is None:
                return 1
            confirmation = json.loads((confirmation_directory / "playthrough-topology.json").read_text())["cases"][0]
            row["firstAttempt"] = row["federated"]
            row["confirmation"] = confirmation["federated"]
            row["federated"] = row["confirmation"]
            row["confirmationDirectory"] = confirmation_directory.name
            row["repeatability"] = ("flaky" if row["confirmation"]["pass"] else
                                    "reproduced-invariant" if row["firstAttempt"]["invariant"] == row["confirmation"]["invariant"] else "different-failure")
            old = row["classification"]
            if row["confirmation"]["pass"]:
                row["classification"] = "flaky"
                for summary in (first["byDimension"], first["byPerturbation"]):
                    for counts in summary.values():
                        counts["flaky"] = counts.pop(old)
            report.write_text(json.dumps(first, indent=2) + "\n")
            markdown = directory / "playthrough-topology.md"
            text = markdown.read_text().replace(old, row["classification"]).replace("not-required", row["repeatability"])
            first_evidence = row["firstAttempt"]["invariant"] + ": " + row["firstAttempt"]["reason"]
            final_evidence = row["confirmation"]["invariant"] + ": " + row["confirmation"]["reason"]
            text = text.replace(first_evidence, final_evidence)
            markdown.write_text(text + "\n## Isolated restart confirmation\n\n"
                                + f"{row['repeatability']}; second world: `{confirmation_directory.name}`. "
                                + "Both complete attempt results are retained in JSON.\n")
        print(f"CASE {row['classification']} restart seed={args.seed}: {directory}", flush=True)
    return 0


def run_phases(args):
    profile = compat_run.load_profiles()["nast" if args.playthrough_topology else "baseline"]
    compat_run.neoforge_server(profile["neoforge"])
    directory = compat_run.RUNS / f"transit-recovery-restart-{time.time_ns()}"
    compat_run.assemble(profile, directory)
    outcomes = []
    phases = ("prepare", "recover") if args.playthrough_topology else ("prepare", "recover", "verify")
    for phase in phases:
        report = directory / f"{phase}-report.json"
        command = ["java", "-Xmx8G" if args.playthrough_topology else "-Xmx4G",
                   "-Dae2federation.compat.tests=" + ("playthroughTopology" if args.playthrough_topology else "transitrecoveryrestartmatrix"),
                   f"-Dae2federation.compat.report={report}", "-Dae2federation.compat.parallel=1",
                   f"-Dae2federation.compat.transitRestartPhase={phase}",
                   f"-Dae2federation.compat.originX={4096 * ('prepare', 'recover', 'verify').index(phase)}",
                   f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/unix_args.txt", "nogui"]
        if args.playthrough_topology:
            command[1:1] = [
                "-Dae2federation.playthrough.items=ae2:blank_pattern",
                f"-Dae2federation.playthrough.topology.seedStart={args.seed}",
                "-Dae2federation.playthrough.topology.seedCount=1",
                "-Dae2federation.playthrough.topology.capacity=normal",
                "-Dae2federation.playthrough.topology.capacityCases=false",
                "-Dae2federation.playthrough.topology.networks=3",
                "-Dae2federation.playthrough.topology.join=router-switch",
                "-Dae2federation.playthrough.topology.path=projection",
                "-Dae2federation.playthrough.topology.perturbations=restart",
            ]
        started = time.monotonic()
        with (directory / f"{phase}-console.log").open("w") as stream:
            try:
                process = subprocess.run(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=stream,
                                         stderr=subprocess.STDOUT, timeout=1800 if args.playthrough_topology else 300, check=False)
            except subprocess.TimeoutExpired:
                print(f"FAIL {phase}: server did not exit; see {directory}", flush=True)
                return None
        if not report.exists():
            print(f"FAIL {phase}: missing report; see {directory}", flush=True)
            return None
        data = json.loads(report.read_text())
        failures = [test for test in data.get("tests", []) if test.get("status") != "pass"]
        success = process.returncode == 0 and not data.get("setupError") and len(data.get("tests", [])) == 1 and not failures
        outcomes.append({"phase": phase, "passed": success, "seconds": round(time.monotonic() - started, 1),
                         "returncode": process.returncode, "report": str(report), "failures": failures})
        (directory / "restart-summary.json").write_text(json.dumps(outcomes, indent=2) + "\n")
        print(f"{'PASS' if success else 'FAIL'} {phase}: {directory}", flush=True)
        if not success:
            print(json.dumps(failures, indent=2), flush=True)
            return None
    return directory


if __name__ == "__main__":
    raise SystemExit(main())
