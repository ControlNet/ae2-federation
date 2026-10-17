import argparse
import concurrent.futures
import json
import os
import re
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "tests/scenarios/manifest.json"
LOG = ROOT / "gametest.log"
LAUNCH = ROOT / "neoforge-1.21.1/build/gametest-launch.json"
PARALLEL_ROOT = ROOT / "neoforge-1.21.1/build/gametest-parallel"


def required_test_ids() -> list[str]:
    cases = json.loads(MANIFEST.read_text())["cases"]
    selected = [case["testId"] for case in cases if case["backend"] == "gametest"]
    if not selected or any(not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9]*", test_id) for test_id in selected):
        raise ValueError("Required GameTest manifest is empty or contains an invalid test ID")
    return list(dict.fromkeys(selected))


def verify_one(test_id: str, output: str, exit_code: int) -> None:
    completions = re.findall(r"All (\d+) required tests passed :\)", output)
    if exit_code != 0 or len(completions) != 1:
        raise ValueError(f"GameTest {test_id} failed or did not report exactly one completion")
    if completions != ["1"]:
        raise ValueError(f"GameTest {test_id} did not execute exactly one required test")
    if any(marker in output for marker in ("No test functions were given", "Failed to create mod instance",
                                          "LoadingFailedException")):
        raise ValueError(f"GameTest {test_id} reported an initialization failure")


def gametest_command(test_id: str) -> list[str]:
    """One GameTest server, with a fresh level, for one test. The server always runs in its own process; reusing the
    Gradle daemon between tests saves only Gradle's start-up (about a third of each test's time)."""
    return [str(ROOT / "gradlew"), ":neoforge-1.21.1:runGameTestServer",
            "-PfederationGameTestSelection=positive", f"-PfederationGameTestId={test_id}",
            "--dependency-verification=strict", "--no-configuration-cache"]


def select_shard(test_ids: list[str], index: int, count: int) -> list[str]:
    if count < 1 or count > len(test_ids) or not 0 <= index < count:
        raise ValueError("Shard index/count must select a nonempty partition of the test manifest")
    return test_ids[index::count]


def direct_command(launch: dict, test_id: str) -> list[str]:
    """runGameTestServer's own command for one test. The test id follows the argument files, whose baked id belongs to
    whichever Gradle run wrote them last, because a later -D wins."""
    return [launch["java"], *launch["jvmArgs"], f"-Dae2federation.testId={test_id}",
            "-Dae2federation.testSelection=positive", "-cp", os.pathsep.join(launch["classpath"]),
            launch["mainClass"], *launch["args"]]


def freeze_argument_files(launch: dict, directory: Path) -> dict:
    """Copies the @argument files, so a Gradle run started meanwhile, which rewrites them, cannot change this run."""
    directory.mkdir(parents=True, exist_ok=True)

    def freeze(argument: str) -> str:
        if not argument.startswith("@"):
            return argument
        source = Path(argument[1:])
        copy = directory / source.name
        shutil.copyfile(source, copy)
        return f"@{copy}"

    return dict(launch, jvmArgs=[freeze(argument) for argument in launch["jvmArgs"]],
                args=[freeze(argument) for argument in launch["args"]])


def run_direct(launch: dict, test_id: str, directory: Path) -> tuple[bool, str]:
    """One test in a fresh server directory, removed when it passes and kept for a look when it fails."""
    shutil.rmtree(directory, ignore_errors=True)
    directory.mkdir(parents=True)
    try:
        result = subprocess.run(direct_command(launch, test_id), cwd=directory, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, timeout=300, check=False,
                                env={**os.environ, **launch["environment"]})
        output, code = result.stdout, result.returncode
    except subprocess.TimeoutExpired as error:
        output, code = (error.stdout or b"").decode(errors="replace") + "\nTIMED OUT", -1
    (directory.parent / f"{test_id}.log").write_text(output)
    try:
        verify_one(test_id, output, code)
    except ValueError:
        return False, output
    shutil.rmtree(directory, ignore_errors=True)
    return True, output


def run_parallel(test_ids: list[str], jobs: int) -> None:
    """Every test in a server of its own, `jobs` at a time, after one Gradle build. A test that fails among the others
    runs again alone: the gate fails only on a test that fails alone, and names those that failed only under load."""
    subprocess.run([str(ROOT / "gradlew"), ":neoforge-1.21.1:federationWriteGameTestLaunch",
                    "--dependency-verification=strict", "--no-configuration-cache", "-q"], cwd=ROOT, check=True)
    shutil.rmtree(PARALLEL_ROOT, ignore_errors=True)
    launch = freeze_argument_files(json.loads(LAUNCH.read_text()), PARALLEL_ROOT / "arguments")
    failed = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        futures = {pool.submit(run_direct, launch, test_id, PARALLEL_ROOT / test_id): test_id for test_id in test_ids}
        for done, future in enumerate(concurrent.futures.as_completed(futures), 1):
            test_id = futures[future]
            passed, _ = future.result()
            print(f"{'PASS' if passed else 'FAIL'} {done}/{len(test_ids)} {test_id}", flush=True)
            if not passed:
                failed.append(test_id)
    under_load = []
    for test_id in failed:
        passed, _ = run_direct(launch, test_id, PARALLEL_ROOT / test_id)
        print(f"{'PASS' if passed else 'FAIL'} alone {test_id}", flush=True)
        if passed:
            under_load.append(test_id)
    if under_load:
        print(f"Failed only among the others, passed alone: {', '.join(under_load)}", flush=True)
    failing = [test_id for test_id in failed if test_id not in under_load]
    if failing:
        raise ValueError(f"GameTests failed alone, logs in {PARALLEL_ROOT}: {', '.join(failing)}")
    print(f"All {len(test_ids)} distinct manifest GameTests executed and passed, {jobs} at a time", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("--shard-count", type=int, default=1)
    parser.add_argument("--jobs", type=int, default=1,
                        help="servers at once; above 1 the servers start without Gradle, each in its own directory")
    args = parser.parse_args()
    test_ids = select_shard(required_test_ids(), args.shard_index, args.shard_count)
    if args.jobs > 1:
        run_parallel(test_ids, args.jobs)
        return
    with LOG.open("w") as log:
        for index, test_id in enumerate(test_ids, 1):
            command = gametest_command(test_id)
            log.write(f"CASE {index}/{len(test_ids)} {test_id}\n")
            log.flush()
            try:
                result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, text=True, timeout=300, check=False)
            except subprocess.TimeoutExpired as error:
                raise ValueError(f"GameTest {test_id} timed out") from error
            log.write(result.stdout)
            log.flush()
            verify_one(test_id, result.stdout, result.returncode)
            print(f"PASS {index}/{len(test_ids)} {test_id}", flush=True)
    print(f"All {len(test_ids)} distinct manifest GameTests in shard {args.shard_index + 1}/{args.shard_count} executed and passed", flush=True)


if __name__ == "__main__":
    main()
