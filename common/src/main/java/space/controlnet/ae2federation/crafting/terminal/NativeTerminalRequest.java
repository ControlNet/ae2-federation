package space.controlnet.ae2federation.crafting.terminal;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.binding.NativeCraftingRequestBinding;
import space.controlnet.ae2federation.crafting.binding.CraftingSubmissionSnapshot;

public final class NativeTerminalRequest {
    private final Thread serverThread;
    private final CraftingBindingService authority;
    private final CraftingSubmissionSnapshot snapshot;
    private final ServerLevel level;
    private final ICraftingService service;
    private final IActionSource actionSource;
    private final AEKey output;
    private final long amount;
    private final Future<ICraftingPlan> future;
    private ICraftingPlan plan;
    private NativeTerminalSubmission submission;

    NativeTerminalRequest(ServerLevel level, CraftingBindingService authority, CraftingSubmissionSnapshot snapshot,
            IActionSource actionSource, AEKey output, long amount) {
        serverThread = Thread.currentThread();
        this.authority = authority;
        this.snapshot = snapshot;
        this.level = level;
        service = snapshot.service();
        this.actionSource = actionSource;
        this.output = output;
        this.amount = amount;
        var requester = new CapturedTerminalRequester(actionSource, snapshot.providerSources().getFirst().node());
        future = service.beginCraftingCalculation(level, requester, output, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public Optional<ICraftingPlan> completedPlan() {
        requireServerThread();
        if (plan != null) {
            return Optional.of(plan);
        }
        if (!future.isDone()) {
            return Optional.empty();
        }
        try {
            plan = future.get();
            return Optional.of(plan);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native terminal calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Native terminal calculation failed", exception);
        }
    }

    public NativeTerminalSubmission submit(@Nullable ICraftingCPU target) {
        requireServerThread();
        if (submission != null) {
            return submission;
        }
        var completed = completedPlan().orElseThrow(() ->
                new IllegalStateException("Native terminal plan is not complete"));
        if (!authority.submissionAuthorityCurrent(snapshot)) {
            submission = new NativeTerminalSubmission.StaleBinding();
            return submission;
        }
        submission = new NativeTerminalSubmission.Native(
                service.submitJob(completed, null, target, true, actionSource));
        return submission;
    }

    public Optional<NativeCraftingRequestBinding> submitTracked(int slot, ICraftingRequester requester,
            TrackedSubmitter submitter) {
        requireServerThread();
        Objects.requireNonNull(requester);
        Objects.requireNonNull(submitter);
        if (slot < 0) {
            throw new IllegalArgumentException("Native terminal tracker slot must be nonnegative");
        }
        if (completedPlan().isEmpty() || !authority.submissionAuthorityCurrent(snapshot)
                || !submitter.submit(output, amount, level, service)) {
            return Optional.empty();
        }
        return synchronizeTracked(slot, requester);
    }

    public Optional<NativeCraftingRequestBinding> synchronizeTracked(int slot, ICraftingRequester requester) {
        requireServerThread();
        Objects.requireNonNull(requester);
        if (slot < 0) {
            throw new IllegalArgumentException("Native terminal tracker slot must be nonnegative");
        }
        var node = requester.getActionableNode();
        var jobs = requester.getRequestedJobs();
        if (node == null || jobs.size() != 1) {
            return Optional.empty();
        }
        return authority.synchronizeNativeRequest(snapshot, node, slot, requester, jobs.iterator().next());
    }

    public CraftingSubmissionSnapshot snapshot() {
        return snapshot;
    }

    private void requireServerThread() {
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("Native terminal request state is server-thread owned");
        }
    }

    @FunctionalInterface
    public interface TrackedSubmitter {
        boolean submit(AEKey output, long amount, ServerLevel level, ICraftingService service);
    }
}
