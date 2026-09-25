package space.controlnet.ae2federation.test.scale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ScaleLargeFederationStageDiagnosticsTest {
    @Test
    void accountsForEachStageAndWaitWhenWarmupFails() {
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(100, 10);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.PLANNING);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION, 120, 12);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.TARGET_INPUT_CONTEXT, 130, 13);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.TARGET_INPUT_CONTEXT);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.MACHINE_CALLBACK, 160, 16);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.MACHINE_CALLBACK);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.RECEIPT_READBACK, 200, 20);
        diagnostics.completedJob(210, 21);

        var report = diagnostics.snapshot("warmup", "failed", 215, 22);

        assertTrue(report.contains("status=failed completedJobs=1"));
        assertTrue(report.contains("PLANNING.wallNanos=25 PLANNING.serverTicks=3 PLANNING.waits=1"));
        assertTrue(report.contains("NATIVE_SUBMISSION.wallNanos=10 NATIVE_SUBMISSION.serverTicks=1 NATIVE_SUBMISSION.waits=1"));
        assertTrue(report.contains("TARGET_INPUT_CONTEXT.wallNanos=30 TARGET_INPUT_CONTEXT.serverTicks=3 TARGET_INPUT_CONTEXT.waits=1"));
        assertTrue(report.contains("MACHINE_CALLBACK.wallNanos=40 MACHINE_CALLBACK.serverTicks=4 MACHINE_CALLBACK.waits=1"));
        assertTrue(report.contains("RECEIPT_READBACK.wallNanos=10 RECEIPT_READBACK.serverTicks=1 RECEIPT_READBACK.waits=0"));
    }

    @Test
    void resetsOnlyAfterSuccessfulPhaseBoundary() {
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(0, 0);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.PLANNING);
        diagnostics.completedJob(10, 2);

        var warmup = diagnostics.snapshot("warmup", "complete", 10, 2);
        diagnostics.reset(10, 2);
        diagnostics.completedJob(30, 5);
        var sample = diagnostics.snapshot("sample", "complete", 30, 5);

        assertTrue(warmup.contains("completedJobs=1 PLANNING.wallNanos=10 PLANNING.serverTicks=2 PLANNING.waits=1"));
        assertTrue(sample.contains("completedJobs=1 PLANNING.wallNanos=20 PLANNING.serverTicks=3 PLANNING.waits=0"));
        assertFalse(sample.contains("status=failed"));
    }
}
