import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "tests/scenarios/manifest.json"
LOG = ROOT / "gametest.log"


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


def main() -> None:
    test_ids = required_test_ids()
    with LOG.open("w") as log:
        for index, test_id in enumerate(test_ids, 1):
            command = [str(ROOT / "gradlew"), ":neoforge-1.21.1:runGameTestServer",
                       "-PfederationGameTestSelection=positive", f"-PfederationGameTestId={test_id}",
                       "--dependency-verification=strict", "--no-configuration-cache", "--no-daemon"]
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
    print(f"All {len(test_ids)} distinct manifest GameTests executed and passed", flush=True)


if __name__ == "__main__":
    main()
