"""Parity table tests; these do not start servers or count as gameplay evidence."""
import unittest

from compat_run import parity, parity_class


def report(joined: bool, *tests: dict) -> dict:
    return {"setupError": "", "joinBeforeOrder": joined, "tests": list(tests)}


def test(name: str, status: str = "pass", ticks: int | None = None, error: str | None = None) -> dict:
    entry = {"id": name, "status": status, "required": True, "ms": 1000}
    if ticks is not None:
        entry["jobTicks"] = ticks
    if error is not None:
        entry["error"] = error
    return entry


class ParityClassTest(unittest.TestCase):
    def test_both_pass_within_the_margin_is_parity(self):
        self.assertEqual(parity_class(test("a", ticks=120), test("a", ticks=100)), "parity")
        self.assertEqual(parity_class(test("a", ticks=30), test("a", ticks=21)), "parity")

    def test_federation_beyond_the_margin_is_slower(self):
        self.assertEqual(parity_class(test("a", ticks=200), test("a", ticks=100)), "slower")
        self.assertEqual(parity_class(test("a", ticks=19), test("a", ticks=4)), "slower")

    def test_failures_by_side(self):
        self.assertEqual(parity_class(test("a", "fail"), test("a", ticks=10)), "Federation fails")
        self.assertEqual(parity_class(test("a", ticks=10), test("a", "fail")), "joined fails")
        self.assertEqual(parity_class(test("a", "fail"), test("a", "fail")), "both fail")

    def test_scenes_without_a_native_control(self):
        self.assertEqual(parity_class(test("a"), test("a")), "no control")
        self.assertEqual(parity_class(test("a"), None), "no control")
        self.assertEqual(parity_class(test("a", ticks=10), test("a", "skipped")), "Federation-only")


class ParityTableTest(unittest.TestCase):
    def test_table_lists_compared_rows_and_counts_the_rest(self):
        federation = {"p": report(False, test("craft", ticks=150), test("storage"), test("cancel", ticks=30))}
        joined = {"p": report(True, test("craft", ticks=50), test("storage"),
                              test("cancel", "skipped"))}
        table = parity(federation, joined)
        self.assertIn("| p | craft | pass, 150 ticks | pass, 50 ticks | +100 (3.00x) | slower |", table)
        self.assertIn("| p | cancel | pass, 30 ticks | skipped |  | Federation-only |", table)
        self.assertNotIn("| storage |", table)
        self.assertIn("no control: 1", table)

    def test_runs_must_be_one_of_each_kind(self):
        with self.assertRaises(ValueError):
            parity({"p": report(True)}, {"p": report(True)})
        with self.assertRaises(ValueError):
            parity({"p": report(False)}, {"p": report(False)})


if __name__ == "__main__":
    unittest.main()
