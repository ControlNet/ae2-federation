"""Synthetic segment reports and directories; these fixtures are not gameplay evidence."""
import json
import tempfile
import unittest
from pathlib import Path

from compat_playthrough_progression import PREFIX, STATE, decide, restore, segment_properties, snapshot


def report(**changes):
    value = dict(cleanBoundary=True, completed=False, stopReason="", harnessError="", ordersThisProcess=3, orders=3,
                 nextTarget=2, incompleteOrder={})
    value.update(changes)
    return value


class ProgressionSegmentsTest(unittest.TestCase):
    def test_clean_boundary_continues_until_completed_or_stopped(self):
        self.assertEqual(decide(report()), ("continue", {}))
        self.assertEqual(decide(report(completed=True)), ("done", {}))
        self.assertEqual(decide(report(stopReason="Bases diverged")), ("done", {}))

    def test_missing_report_or_harness_error_fails(self):
        self.assertEqual(decide(None), ("fail", {}))
        self.assertEqual(decide(report(harnessError="boom")), ("fail", {}))

    def test_cut_order_repeats_finished_orders_or_defers(self):
        self.assertEqual(decide(report(cleanBoundary=False, ordersThisProcess=2)),
                         ("retry", {PREFIX + "progression.maxOrders": "2"}))
        self.assertEqual(decide(report(cleanBoundary=False, ordersThisProcess=0)),
                         ("retry", {PREFIX + "progression.deferNext": "true"}))

    def test_segment_properties_resume_and_retry_settings(self):
        base = {PREFIX + "chapter": "ae2"}
        settings = segment_properties(base, True, {PREFIX + "progression.maxOrders": "1"})
        self.assertEqual(settings[PREFIX + "progression.resume"], "true")
        self.assertEqual(settings[PREFIX + "progression.maxOrders"], "1")
        self.assertNotIn(PREFIX + "progression.resume", base)

    def test_snapshot_and_restore_world_and_state(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run, checkpoint = root / "run", root / "checkpoint"
            (run / "world").mkdir(parents=True)
            (run / "world" / "level.dat").write_text("saved")
            (run / STATE).write_text("{}")
            snapshot(run, checkpoint, {"a": "b"})
            self.assertEqual(json.loads((checkpoint / "properties.json").read_text()), {"a": "b"})
            (run / "world" / "level.dat").write_text("half-finished order")
            (run / "world" / "extra").write_text("x")
            (run / STATE).write_text("changed")
            restore(checkpoint, run)
            self.assertEqual((run / "world" / "level.dat").read_text(), "saved")
            self.assertFalse((run / "world" / "extra").exists())
            self.assertEqual((run / STATE).read_text(), "{}")
            with self.assertRaises(FileExistsError):
                snapshot(run, checkpoint, {})

    def test_restore_of_the_empty_start_removes_world_and_state(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run, checkpoint = root / "run", root / "checkpoint"
            run.mkdir()
            snapshot(run, checkpoint, {})
            (run / "world").mkdir()
            (run / STATE).write_text("{}")
            restore(checkpoint, run)
            self.assertFalse((run / "world").exists())
            self.assertFalse((run / STATE).exists())


if __name__ == "__main__":
    unittest.main()
