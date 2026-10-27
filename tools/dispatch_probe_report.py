"""Markdown tables of the crafting dispatch probe's counts (stdlib only).

Each compat run started with --jvm-property ae2federation.dispatchProbe=true leaves dispatch-probe.json beside its
compat-report.json. This prints, per run, which executor sites dispatched and where their crafting service came from,
which provider-side pushes no executor probe saw, which fell outside every crafting service's dispatch scope (both with
their call stacks), and whether the dispatching service, by executor or by scope, holds the provider.

The gap tables measure option 1 of the CPU route: executor hand-overs that opened no known protocol, pattern provider
pushes that succeeded and send lists that grew outside every known protocol, and the provider classes each executor
handed work to. Joined runs (--jvm-property ae2federation.compat.joinBeforeOrder=true) are the ones that decide: only
there do addons see real providers and run their own protocols against them.

    python3 tools/dispatch_probe_report.py neoecoae-probe data-energistics-probe-joined
    python3 tools/dispatch_probe_report.py build/compat/runs/neoecoae-probe/dispatch-probe.json
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNS = ROOT / "neoforge-1.21.1/build/compat/runs"


def probe_file(argument: str) -> Path:
    path = Path(argument)
    if path.is_file():
        return path
    if path.is_dir():
        return path / "dispatch-probe.json"
    return RUNS / argument / "dispatch-probe.json"


def failed_tests(run: Path) -> list[str]:
    report = run / "compat-report.json"
    if not report.is_file():
        return ["(no compat-report.json)"]
    tests = json.loads(report.read_text(encoding="utf-8")).get("tests", [])
    return [test["id"] for test in tests if test.get("status") != "pass"]


def ratio(witness: dict, prefix: str) -> str:
    checked = witness.get(prefix + "Checked", 0)
    return f"{witness[prefix + 'Correct']}/{checked}" if checked else "-"


def render(path: Path) -> str:
    probe = json.loads(path.read_text(encoding="utf-8"))
    lines = [f"## {path.parent.name}", ""]
    failed = failed_tests(path.parent)
    lines += [f"Tests not passing: {', '.join(failed) if failed else 'none'}", ""]
    executors = probe.get("executors", {})
    if executors:
        lines += ["| Executor site | Calls | Accepted | Runs | Unknown runs | Service: argument / scope / none | "
                  "Argument != scope | Nested |", "|---|---:|---:|---:|---:|---|---:|---:|"]
        for site, e in executors.items():
            lines.append(f"| `{site}` | {e['calls']} | {e['accepted']} | {e['runsAccepted']} | "
                         f"{e['acceptedWithUnknownRuns']} | {e['serviceFromArgument']} / {e['serviceFromScope']} / "
                         f"{e['noService']} | {e['argumentDiffersFromScope']} | {e['nested']} |")
    else:
        lines.append("No executor site dispatched.")
    lines.append("")
    witnesses = probe.get("witnesses", {})
    if witnesses:
        lines += ["| Provider-side site | Accepted pushes | Runs | Executor: seen / missed | Executor holds provider | "
                  "Scope: in / out | Scope holds provider | Seen by |", "|---|---:|---:|---|---|---|---|---|"]
        for site, w in witnesses.items():
            covered_by = ", ".join(f"`{name}` {count}" for name, count in w["coveredBy"].items()) or "-"
            lines.append(f"| `{site}` | {w['calls']} | {w['runs']} | {w['covered']} / {w['uncovered']} | "
                         f"{ratio(w, 'attribution')} | {w['inScope']} / {w['outOfScope']} | "
                         f"{ratio(w, 'scopeAttribution')} | {covered_by} |")
        for site, w in witnesses.items():
            for title, key in (("Outside every scope", "outOfScopeStacks"), ("Missed by executors", "uncoveredStacks")):
                for stack in w.get(key, []):
                    lines += ["", f"{title} at `{site}` ({stack['count']}x):", "", "```",
                              stack["stack"].replace(" < ", "\n  < "), "```"]
    else:
        lines.append("No provider-side push was seen.")
    lines.append("")
    if "pushSuccesses" in probe:
        lines += [f"Pattern provider pushes that succeeded: {probe['pushSuccesses']}; send list growths: "
                  f"{probe['sendListGrowths']}", ""]
    classes = probe.get("providerClasses", {})
    if classes:
        lines += ["| Executor site | Provider class | Accepted | Opened a known protocol |", "|---|---|---:|---:|"]
        for site, by_class in classes.items():
            for name, counts in by_class.items():
                lines.append(f"| `{site}` | `{name}` | {counts['accepted']} | {counts['known']} |")
        lines.append("")
    for title, key in (("Executor hand-overs that opened no known protocol", "unhookedDispatches"),
                       ("Pattern provider pushes that succeeded outside known protocols", "unrecordedSuccesses"),
                       ("Send lists that grew outside known protocols", "unrecordedSends")):
        gaps = probe.get(key, {})
        if not gaps:
            lines += [f"{title}: none.", ""]
            continue
        lines += [f"{title}:", "", "| Where | Count | Scope: in / out |", "|---|---:|---|"]
        for where, gap in gaps.items():
            lines.append(f"| `{where}` | {gap['count']} | {gap['inScope']} / {gap['outOfScope']} |")
        for where, gap in gaps.items():
            for stack in gap.get("stacks", []):
                lines += ["", f"`{where}` ({stack['count']}x):", "", "```", stack["stack"].replace(" < ", "\n  < "),
                          "```"]
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("runs", nargs="+", help="run names under build/compat/runs, run directories or probe files")
    args = parser.parse_args()
    missing = 0
    for argument in args.runs:
        path = probe_file(argument)
        if not path.is_file():
            print(f"## {argument}\n\nNo dispatch-probe.json at {path}\n")
            missing += 1
            continue
        print(render(path))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
