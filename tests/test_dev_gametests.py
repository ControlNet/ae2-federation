import unittest

from tools.dev_gametests import outcomes, verify_batch

BATCH = "[12:00:00] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'defaultBatch/{}:0' (1 tests)...\n"
FAILURE = "[12:00:01] [Server thread/ERROR] [minecraft/GameTestServer]: {} failed at 1,2,3! {}\n"
COMPLETE = "[12:00:02] [Server thread/INFO] [minecraft/GameTestServer]: ========= 2 GAME TESTS COMPLETE IN 1.2 s ======\n"
CRASH = ("[12:00:01] [Server thread/ERROR] [minecraft/GameTestServer]: Game test server crashed\n"
         "---- Minecraft Crash Report ----\n"
         "net.minecraft.ReportedException: Ticking block entity\n"
         "Caused by: java.lang.IllegalStateException: invalid cache\n")


class DevGameTestsTest(unittest.TestCase):
    def test_started_tests_without_failure_pass_once_the_run_completes(self):
        output = BATCH.format("alpha") + BATCH.format("beta") + FAILURE.format("somegametests.beta", "Expected 1") \
            + COMPLETE
        self.assertEqual(outcomes(output, ["alpha", "beta", "gamma"]),
                         {"alpha": "PASS", "beta": "FAIL Expected 1", "gamma": "MISSING"})

    def test_crash_blames_the_running_test_and_passes_the_finished_ones(self):
        output = BATCH.format("alpha") + BATCH.format("beta") + CRASH
        self.assertEqual(outcomes(output, ["alpha", "beta", "gamma"]),
                         {"alpha": "PASS", "beta": "CRASH java.lang.IllegalStateException: invalid cache",
                          "gamma": "MISSING"})

    def test_a_server_that_never_starts_a_batch_reports_every_test_missing(self):
        self.assertEqual(outcomes("BUILD FAILED\n", ["alpha"]), {"alpha": "MISSING"})


class VerifyBatchTest(unittest.TestCase):
    PASSED = "[12:00:02] [Server thread/INFO] [minecraft/GameTestServer]: All {} required tests passed :)\n"

    def batch(self, *names, passed=2):
        return "".join(BATCH.format(name) for name in names) + COMPLETE + self.PASSED.format(passed)

    def test_every_requested_test_once_and_one_complete_pass_is_accepted(self):
        verify_batch(["alpha", "beta"], self.batch("alpha", "beta"), 0)

    def test_a_run_that_is_not_one_complete_pass_of_exactly_the_requested_tests_is_rejected(self):
        cases = {
            "nonzero exit": (self.batch("alpha", "beta"), 1),
            "fewer passed than requested": (self.batch("alpha", "beta", passed=1), 0),
            "no completion": ("".join(BATCH.format(name) for name in ("alpha", "beta")), 0),
            "two completions": (self.batch("alpha", "beta") + self.PASSED.format(2), 0),
            "test never started": (self.batch("alpha"), 0),
            "test started twice": (self.batch("alpha", "beta", "beta"), 0),
            "unrequested test": (self.batch("alpha", "beta", "gamma"), 0),
            "initialization failure": (self.batch("alpha", "beta") + "Failed to create mod instance\n", 0),
        }
        for name, (output, code) in cases.items():
            with self.subTest(name), self.assertRaises(ValueError):
                verify_batch(["alpha", "beta"], output, code)


if __name__ == "__main__":
    unittest.main()
