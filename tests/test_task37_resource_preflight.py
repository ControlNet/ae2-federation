# pyright: basic
import copy
import json
import tempfile
import unittest
from pathlib import Path
from typing import Any


from tools.task37_resource_preflight import CAPACITY_FIELDS, LAYOUTS, compare, parse_replay


class ResourcePreflightTest(unittest.TestCase):
    ledgers: dict[str, dict[str, Any]] = {}
    observations: dict[str, Any] = {}

    def setUp(self):
        # Test-only comparison values, never persisted or presented as source measurements.
        self.ledgers = {
            layout: {
                "catalogSha256": "same-catalog", "inputReplaySha256": "same-input",
                "physicalPatterns": 256, "orderedJobs": 256, "callbackUnits": 4096,
                "driveUnits": 4096, "sourceCpu": 1, "sourceDriveCells": 5,
                "sourceGridUsedChannels": 19,
                "receipt": {"sha256": f"receipt-{layout}"},
                "topology": {"physicalRoute": layout, "targetGrids": 0 if layout == "native-big-grid" else 16},
            } for layout in LAYOUTS
        }
        self.observations = {
            "schemaVersion": 1,
            "layouts": {
                layout: {
                    "receiptSha256": f"receipt-{layout}", "sourceCpu": 1,
                    "sourceDriveCells": 5, "sourceGridUsedChannels": 19,
                    "cpuBytes": 1024, "driveCellBytes": 16384,
                    "totalItemStorageBytes": 100000,
                    "machineCount": 16,
                    "machineInputSlotsPerHost": 1, "machineUnitsPerTick": 1,
                    "energySupplyAePerTick": 100, "energyDebitAe": 10,
                    "peakEnergyDemandAePerTick": 5, "worldLoadedChunks": 20,
                    "worldLoadedChunksPeak": 20, "worldChunkTickets": 0,
                    "worldChunkTicketsPeak": 0, "energyStorageKind": "finite",
                    "measurementPhase": "postReplay",
                } for layout in LAYOUTS
            },
        }
        for layout in LAYOUTS:
            self.ledgers[layout]["measuredResources"] = {
                field: str(value) for field, value in self.observations["layouts"][layout].items()
            }

    def test_mismatched_capacity_cpu_and_input_reject_speedup(self):
        for field, value in (("cpuBytes", 2048), ("totalItemStorageBytes", 200000), ("sourceCpu", 2),
                             ("machineUnitsPerTick", 2)):
            with self.subTest(field=field):
                observations = copy.deepcopy(self.observations)
                observations["layouts"]["federation"][field] = value
                result = compare(self.ledgers, observations)
                self.assertEqual("NON_COMPARABLE", result["decision"])
                self.assertFalse(result["speedupEligible"])
                self.assertTrue(any(field in reason for reason in result["reasons"]))
        ledgers = copy.deepcopy(self.ledgers)
        ledgers["native-subnet"]["inputReplaySha256"] = "wrong-input"
        result = compare(ledgers, self.observations)
        self.assertEqual("NON_COMPARABLE", result["decision"])
        self.assertFalse(result["speedupEligible"])
        self.assertTrue(any("inputReplaySha256" in reason for reason in result["reasons"]))

    def test_unmeasured_energy_and_loaded_chunks_reject_speedup(self):
        for field in ("energyDebitAe", "worldLoadedChunks", "worldChunkTickets"):
            with self.subTest(field=field):
                observations = copy.deepcopy(self.observations)
                del observations["layouts"]["native-subnet"][field]
                result = compare(self.ledgers, observations)
                self.assertEqual("NON_COMPARABLE", result["decision"])
                self.assertFalse(result["speedupEligible"])
                self.assertTrue(any(field in reason for reason in result["reasons"]))
        self.assertEqual("NON_COMPARABLE", compare(self.ledgers)["decision"])
        ledgers = copy.deepcopy(self.ledgers)
        for layout in LAYOUTS:
            del ledgers[layout]["measuredResources"]
        result = compare(ledgers, self.observations)
        self.assertEqual("NON_COMPARABLE", result["decision"])
        self.assertFalse(result["speedupEligible"])
        self.assertTrue(any("no live resource snapshot" in reason for reason in result["reasons"]))

    def test_missing_or_corrupt_persisted_replay_rejects(self):
        with self.assertRaises(OSError):
            parse_replay("native-big-grid", Path("nonexistent-task37-process.log"))
        with tempfile.TemporaryDirectory(prefix="task37-corrupt-test-only-") as temporary:
            process_log = Path(temporary) / "corrupt-process.log"
            process_log.write_text("BUILD SUCCESSFUL\n")
            with self.assertRaises(ValueError):
                parse_replay("native-big-grid", process_log)
        self.assertEqual("NON_COMPARABLE", compare({})["decision"])
        observations = copy.deepcopy(self.observations)
        observations["layouts"]["native-big-grid"] = 1
        self.assertEqual("NON_COMPARABLE", compare(self.ledgers, observations)["decision"])

    def test_topology_is_disclosed_not_forced_equal(self):
        self.observations["layouts"]["federation"]["energyDebitAe"] = 12
        self.ledgers["federation"]["measuredResources"]["energyDebitAe"] = "12"
        result = compare(self.ledgers, self.observations)
        self.assertEqual("COMPARABLE", result["decision"])
        self.assertEqual(16, result["topologyDisclosedNotEqualized"]["federation"]["targetGrids"])
        self.assertEqual(12, result["observedEnergyNotEqualized"]["federation"]["energyDebitAe"])
        for field in CAPACITY_FIELDS:
            self.assertIn(field, self.observations["layouts"]["native-big-grid"])

    def test_reject_incomparable_speedup_when_deliberately_mismatched_test_only_fixture(self):
        self.assertEqual("COMPARABLE", compare(self.ledgers, self.observations)["decision"])
        observations = copy.deepcopy(self.observations)
        observations["layouts"]["federation"]["cpuBytes"] += 1
        self.ledgers["federation"]["measuredResources"]["cpuBytes"] = str(
            observations["layouts"]["federation"]["cpuBytes"]
        )
        result = compare(self.ledgers, observations)
        self.assertEqual("NON_COMPARABLE", result["decision"])
        self.assertFalse(result["speedupEligible"])
        self.assertIn("federation: cpuBytes differs from direct", result["reasons"])

    def test_stale_receipt_hash_rejects_fully_bound_test_only_fixture(self):
        observations = copy.deepcopy(self.observations)
        observations["layouts"]["native-subnet"]["receiptSha256"] = "stale-process"
        result = compare(self.ledgers, observations)
        self.assertEqual("NON_COMPARABLE", result["decision"])
        self.assertTrue(any("not bound to this receipt" in reason for reason in result["reasons"]))

    def test_valid_fully_bound_test_only_resource_snapshot(self):
        result = compare(self.ledgers, self.observations)
        self.assertEqual("COMPARABLE", result["decision"])
        self.assertEqual([], result["reasons"])

    def test_perf_case_registration_uses_persisted_preflight_backend(self):
        manifest = json.loads(Path("tests/scenarios/manifest.json").read_text())
        cases = {entry["id"]: entry for entry in manifest["cases"]}
        for case_id in ("perf.resource-integrity", "perf.reject-incomparable-speedup"):
            self.assertEqual("task37-resource-preflight", cases[case_id]["backend"])


if __name__ == "__main__":
    unittest.main()
