"""Run pinned historical-addon cases through the production compatibility runner (stdlib only)."""
import argparse
import concurrent.futures
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools import compat_run

ROOT = compat_run.ROOT
DEFAULT_MANIFEST = ROOT / "tests/compat/version-boundaries.json"


def resolve_cases(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Replace only the named addon entries in an existing, resolved production profile."""
    bases = compat_run.load_profiles()
    cases = {}
    for case in manifest["cases"]:
        name = case["name"]
        if name in cases:
            raise ValueError(f"Duplicate case: {name}")
        profile = dict(bases[case["base"]])
        replacements = case.get("replace", {})
        available = {entry["name"] for entry in profile["mods"]}
        unknown = replacements.keys() - available
        if unknown:
            raise ValueError(f"{name}: replacement targets are absent from the base profile: {sorted(unknown)}")
        for target, entry in replacements.items():
            if entry["name"] != target:
                raise ValueError(f"{name}: replacement {target!r} has name {entry['name']!r}")
        replaced = set(replacements) | set(case.get("remove", []))
        profile["mods"] = [entry for entry in profile["mods"] if entry["name"] not in replaced]
        profile["mods"] += list(case.get("replace", {}).values()) + case.get("add", [])
        profile["tests"] = case.get("tests", profile["tests"])
        profile["neoforge"] = case.get("neoforge", profile["neoforge"])
        profile["memory"] = case.get("memory", "2G")
        profile["jvmArgs"] = [*profile.get("jvmArgs", []),
                              f"-XX:ActiveProcessorCount={case.get('processors', 2)}"]
        cases[name] = {"case": case, "profile": profile}
    return cases


def artifact_hashes() -> dict[str, str]:
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in compat_run.built_jars()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cases", nargs="*")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--accept-eula", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--bare", action="store_true", help="upstream-only startup control, without Federation")
    parser.add_argument("-j", "--jobs", type=int, default=2)
    parser.add_argument("-p", "--parallel", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--output", type=Path, default=ROOT / "neoforge-1.21.1/build/addon-version-boundaries/results")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    cases = resolve_cases(manifest)
    unknown = set(args.cases) - cases.keys()
    if unknown:
        parser.error(f"Unknown cases: {sorted(unknown)}")
    selected = args.cases or list(cases)
    if args.list:
        for name in selected:
            print(f"{name}: {cases[name]['case'].get('note', '')}")
        return 0
    if not args.accept_eula:
        parser.error("Starting Minecraft accepts https://aka.ms/MinecraftEULA; pass --accept-eula")
    if not args.no_build and not args.bare:
        compat_run.build()
    hashes = {} if args.bare else artifact_hashes()
    # Prepare shared caches before workers use them; the underlying installer is not concurrent-safe.
    for version in sorted({cases[name]["profile"]["neoforge"] for name in selected}):
        compat_run.neoforge_server(version)
    pins = {entry["sha512"]: entry for name in selected for entry in cases[name]["profile"]["mods"]}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(compat_run.download, pins.values()))
    args.output.mkdir(parents=True, exist_ok=True)

    def run(name: str) -> dict[str, Any]:
        resolved = cases[name]
        profile = resolved["profile"]
        label = name + ("-bare" if args.bare else "")
        directory = (args.output / label).resolve()
        result = (compat_run.run_bare(label, profile, args.timeout, directory=directory) if args.bare else
                  compat_run.run_profile(label, profile, ",".join(profile["tests"]), args.timeout,
                                         parallel=args.parallel, directory=directory))
        evidence = {"case": resolved["case"], "profile": profile, "artifacts": hashes,
                    "execution": {"parallel": args.parallel, "timeout": args.timeout, "bare": args.bare},
                    "directory": str(directory.relative_to(ROOT) if directory.is_relative_to(ROOT) else directory),
                    "result": result}
        (args.output / f"{label}.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print(f"{label}: {result['status']} {result.get('passed', 0)}/{result.get('total', 0)} "
              f"({result['seconds']} s)", flush=True)
        for failure in result.get("failed", []):
            print(f"  {failure['id']}: {failure.get('error', failure['status'])}", flush=True)
        if result.get("detail"):
            print(result["detail"], flush=True)
        return result

    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        results = list(pool.map(run, selected))
    summary = args.output / ("summary-bare.json" if args.bare else "summary.json")
    summary.write_text(json.dumps(results, indent=2) + "\n")
    return 0 if all(result["status"] in {"pass", "boots"} for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
