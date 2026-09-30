import unittest

from tools.dev_gametests import outcomes

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


if __name__ == "__main__":
    unittest.main()
