"""Synthetic segment reports and directories; these fixtures are not gameplay evidence."""
import argparse
import json
import tempfile
import unittest
from pathlib import Path

from compat_playthrough_progression import (PREFIX, STATE, decide, resolve_properties, restore, saved_properties,
                                            segment_properties, snapshot)


def report(**changes):
    value = dict(cleanBoundary=True, completed=False, stopReason="", harnessError="", ordersThisProcess=3, orders=3,
                 nextTarget=2, incompleteOrder={})
    value.update(changes)
    return value


CLEAN = {"status": "pass", "exit": 0}


def arguments(**changes):
    value = dict(chapter=None, max_targets=None, segment_seconds=None, jvm_property=[])
    value.update(changes)
    return argparse.Namespace(**value)


class ProgressionSegmentsTest(unittest.TestCase):
    def test_clean_boundary_continues_until_completed(self):
        self.assertEqual(decide(report(), CLEAN), ("continue", {}))
        self.assertEqual(decide(report(completed=True), CLEAN), ("done", {}))

    def test_failure_stop_is_not_success(self):
        # A divergence, an unsettled base or failed restart checks: the tool ends with a failing status.
        self.assertEqual(decide(report(stopReason="Bases diverged"), CLEAN), ("failed-stop", {}))
        self.assertEqual(decide(report(stopReason="Bases did not re-attach", cleanBoundary=False, ordersThisProcess=0),
                                {"status": "fail", "exit": 0}), ("failed-stop", {}))

    def test_missing_report_or_harness_error_fails(self):
        self.assertEqual(decide(None, CLEAN), ("fail", {}))
        self.assertEqual(decide(report(harnessError="boom"), CLEAN), ("fail", {}))

    def test_cut_order_repeats_finished_orders_or_defers(self):
        self.assertEqual(decide(report(cleanBoundary=False, ordersThisProcess=2), CLEAN),
                         ("retry", {PREFIX + "progression.maxOrders": "2"}))
        self.assertEqual(decide(report(cleanBoundary=False, ordersThisProcess=0), CLEAN),
                         ("retry", {PREFIX + "progression.deferNext": "true"}))

    def test_boundary_without_clean_exit_repeats_the_segment(self):
        # The world of a server that did not exit cleanly may not be saved; its boundary is not accepted.
        for server in ({"status": "pass", "exit": None}, {"status": "pass", "exit": 1}, {"status": "timeout", "exit": None},
                       {"status": "fail", "exit": 0}):
            self.assertEqual(decide(report(), server), ("retry", {PREFIX + "progression.maxOrders": "3"}))
            self.assertEqual(decide(report(completed=True), server), ("retry", {PREFIX + "progression.maxOrders": "3"}))
        # A boundary reached by unexecuted targets only is repeated as it was, never deferred.
        self.assertEqual(decide(report(ordersThisProcess=0), {"status": "pass", "exit": None}), ("retry", {}))

    def test_segment_properties_resume_and_retry_settings(self):
        base = {PREFIX + "chapter": "ae2"}
        settings = segment_properties(base, True, {PREFIX + "progression.maxOrders": "1"})
        self.assertEqual(settings[PREFIX + "progression.resume"], "true")
        self.assertEqual(settings[PREFIX + "progression.maxOrders"], "1")
        self.assertNotIn(PREFIX + "progression.resume", base)

    def test_new_run_properties_take_the_command_line(self):
        properties = resolve_properties(arguments(chapter="031C3CFAFBBF4A6D", max_targets=3, segment_seconds=1,
                                                  jvm_property=[PREFIX + "progression.disturbances=cable"]), None)
        self.assertEqual(properties[PREFIX + "chapter"], "031C3CFAFBBF4A6D")
        self.assertEqual(properties[PREFIX + "progression.maxTargets"], "3")
        self.assertEqual(properties[PREFIX + "progression.stopAfterSeconds"], "1")
        self.assertEqual(properties[PREFIX + "progression.disturbances"], "cable")
        self.assertEqual(resolve_properties(arguments(), None)[PREFIX + "chapter"], "ae2")
        with self.assertRaises(ValueError):
            resolve_properties(arguments(jvm_property=[PREFIX + "progression.maxOrders=1"]), None)

    def test_resume_keeps_the_saved_run_and_refuses_unsafe_changes(self):
        # The documented sequence: start with --chapter, then a plain --resume finds the same properties.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run, checkpoints = root / "run", root / "checkpoints"
            run.mkdir()
            started = resolve_properties(arguments(chapter="031C3CFAFBBF4A6D", max_targets=3, segment_seconds=1,
                                                   jvm_property=[PREFIX + "progression.disturbanceEvery=1"]), None)
            snapshot(run, checkpoints / "segment-0", started)
            snapshot(run, checkpoints / "segment-1", started)
            saved = saved_properties(checkpoints)
            self.assertEqual(resolve_properties(arguments(), saved), started)
            # Timing may change; repeating a saved value is accepted.
            changed = resolve_properties(arguments(segment_seconds=600, chapter="031C3CFAFBBF4A6D",
                                                   jvm_property=[PREFIX + "wallSeconds=1200"]), saved)
            self.assertEqual(changed[PREFIX + "progression.stopAfterSeconds"], "600")
            self.assertEqual(changed[PREFIX + "wallSeconds"], "1200")
            self.assertEqual(changed[PREFIX + "chapter"], "031C3CFAFBBF4A6D")
            for unsafe in (arguments(chapter="ae2"), arguments(max_targets=0),
                           arguments(jvm_property=[PREFIX + "progression.disturbances=cable"]),
                           arguments(jvm_property=[PREFIX + "progression.disturbanceEvery=4"])):
                with self.assertRaisesRegex(ValueError, "would change the saved run"):
                    resolve_properties(unsafe, saved)
            with self.assertRaises(FileNotFoundError):
                saved_properties(root / "none")

    def test_latest_checkpoint_properties_win(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run, checkpoints = root / "run", root / "checkpoints"
            run.mkdir()
            for index in (0, 2, 10):
                snapshot(run, checkpoints / f"segment-{index}", {"segment": str(index)})
            self.assertEqual(saved_properties(checkpoints), {"segment": "10"})

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
