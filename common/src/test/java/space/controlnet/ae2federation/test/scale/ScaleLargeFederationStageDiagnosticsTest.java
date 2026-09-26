package space.controlnet.ae2federation.test.scale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ScaleLargeFederationStageDiagnosticsTest {
    @Test
    void accountsForEachStageAndWaitWhenWarmupFails() {
        var cpu = new AtomicLong(1000);
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(100, 10, cpu::get);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.PLANNING);
        cpu.set(1020);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION, 120, 12);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION);
        cpu.set(1025);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.TARGET_INPUT_CONTEXT, 130, 13);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.TARGET_INPUT_CONTEXT);
        cpu.set(1030);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.MACHINE_CALLBACK, 160, 16);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.MACHINE_CALLBACK);
        cpu.set(1040);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.RECEIPT_READBACK, 200, 20);
        cpu.set(1047);
        diagnostics.completedJob(210, 21);

        cpu.set(1049);
        var report = diagnostics.snapshot("warmup", "failed", 215, 22);

        assertTrue(report.contains("status=failed completedJobs=1"));
        assertTrue(report.contains("PLANNING.wallNanos=25 PLANNING.serverTicks=3 PLANNING.waits=1"));
        assertTrue(report.contains("NATIVE_SUBMISSION.wallNanos=10 NATIVE_SUBMISSION.serverTicks=1 NATIVE_SUBMISSION.waits=1"));
        assertTrue(report.contains("TARGET_INPUT_CONTEXT.wallNanos=30 TARGET_INPUT_CONTEXT.serverTicks=3 TARGET_INPUT_CONTEXT.waits=1"));
        assertTrue(report.contains("MACHINE_CALLBACK.wallNanos=40 MACHINE_CALLBACK.serverTicks=4 MACHINE_CALLBACK.waits=1"));
        assertTrue(report.contains("RECEIPT_READBACK.wallNanos=10 RECEIPT_READBACK.serverTicks=1 RECEIPT_READBACK.waits=0"));
        assertTrue(report.contains("PLANNING.serverThreadCpuNanos=22"));
        assertTrue(report.contains("NATIVE_SUBMISSION.serverThreadCpuNanos=5"));
        assertTrue(report.contains("TARGET_INPUT_CONTEXT.serverThreadCpuNanos=5"));
        assertTrue(report.contains("MACHINE_CALLBACK.serverThreadCpuNanos=10"));
        assertTrue(report.contains("RECEIPT_READBACK.serverThreadCpuNanos=7"));
    }

    @Test
    void resetsOnlyAfterSuccessfulPhaseBoundary() {
        var cpu = new AtomicLong(100);
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(0, 0, cpu::get);
        diagnostics.waitFor(ScaleLargeFederation256Replay.JobStage.PLANNING);
        cpu.set(110);
        diagnostics.completedJob(10, 2);

        var warmup = diagnostics.snapshot("warmup", "complete", 10, 2);
        diagnostics.reset(10, 2);
        cpu.set(130);
        diagnostics.completedJob(30, 5);
        var sample = diagnostics.snapshot("sample", "complete", 30, 5);

        assertTrue(warmup.contains("completedJobs=1 PLANNING.wallNanos=10 PLANNING.serverTicks=2 PLANNING.waits=1"));
        assertTrue(sample.contains("completedJobs=1 PLANNING.wallNanos=20 PLANNING.serverTicks=3 PLANNING.waits=0"));
        assertTrue(warmup.contains("PLANNING.serverThreadCpuNanos=10"));
        assertTrue(sample.contains("PLANNING.serverThreadCpuNanos=20"));
        assertFalse(sample.contains("status=failed"));
    }

    @Test
    void reportsUnavailableWhenCpuClockCannotMeasure() {
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(0, 0, () -> -1);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION, 10, 2);

        var report = diagnostics.snapshot("warmup", "failed", 20, 3);

        assertTrue(report.contains("PLANNING.wallNanos=10 PLANNING.serverTicks=2 PLANNING.waits=0 PLANNING.serverThreadCpuNanos=unavailable"));
        assertTrue(report.contains("NATIVE_SUBMISSION.wallNanos=10 NATIVE_SUBMISSION.serverTicks=1 NATIVE_SUBMISSION.waits=0 NATIVE_SUBMISSION.serverThreadCpuNanos=unavailable"));
    }

    @Test
    void reportsUnavailableAfterThreadChanges() throws InterruptedException {
        var cpu = new AtomicLong(100);
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(0, 0, cpu::get);
        var otherThread = new Thread(() -> diagnostics.transition(
                ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION, 10, 2));
        otherThread.start();
        otherThread.join();

        var report = diagnostics.snapshot("warmup", "failed", 20, 3);

        assertTrue(report.contains("PLANNING.serverThreadCpuNanos=unavailable"));
        assertTrue(report.contains("NATIVE_SUBMISSION.serverThreadCpuNanos=unavailable"));
    }

    @Test
    void reportsUnavailableWhenClockStopsMidStage() {
        var cpu = new AtomicLong(100);
        var diagnostics = new ScaleLargeFederation256Replay.StageDiagnostics(0, 0, cpu::get);
        cpu.set(110);
        diagnostics.transition(ScaleLargeFederation256Replay.JobStage.NATIVE_SUBMISSION, 10, 2);
        cpu.set(-1);

        var report = diagnostics.snapshot("warmup", "failed", 20, 3);

        assertTrue(report.contains("PLANNING.serverThreadCpuNanos=unavailable"));
        assertTrue(report.contains("NATIVE_SUBMISSION.serverThreadCpuNanos=unavailable"));
    }
}
