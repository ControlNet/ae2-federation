package space.controlnet.ae2federation.automation;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crafting jobs programs start. AE2 plans a job over later ticks, so {@code ae2.craft} only asks for the plan; the
 * server tick submits it to a free CPU when it is ready. Programs cannot be AE2 crafting requesters (those are grid
 * node services), so a job is tracked by the CPU it was given, which is how {@code ae2.cancel} finds the jobs of one
 * program. The job's output goes into the network's storage, as a terminal's request does.
 */
public final class ProgramCrafting {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramCrafting.class);
    private static final Map<MinecraftServer, ProgramCrafting> SERVICES = new HashMap<>();

    private record Pending(String owner, IGrid grid, AEKey key, Future<ICraftingPlan> plan) {
    }

    private record Started(String owner, ICraftingCPU cpu, AEKey key) {
    }

    private final List<Pending> pending = new ArrayList<>();
    private final List<Started> started = new ArrayList<>();

    public static ProgramCrafting get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, ignored -> new ProgramCrafting());
    }

    public static void tickAll(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service != null) {
            service.tick();
        }
    }

    public static void closeServer(MinecraftServer server) {
        var service = SERVICES.remove(server);
        if (service != null) {
            service.pending.forEach(request -> request.plan().cancel(true));
        }
    }

    /** Asks the grid to plan the job; false when nothing makes the resource there or no CPU is free. */
    public boolean request(String owner, IGrid grid, AEKey key, long amount) {
        var crafting = grid.getCraftingService();
        if (amount <= 0 || !crafting.isCraftable(key) || freeCpus(grid) == 0) {
            return false;
        }
        var pivot = grid.getPivot();
        var level = pivot == null ? null : pivot.getLevel();
        if (level == null) {
            return false;
        }
        // AE2 looks up patterns through the requester's node; without one every plan comes back missing.
        var requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return pivot;
            }
        };
        pending.add(new Pending(owner, grid, key, crafting.beginCraftingCalculation(level, requester, key, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS)));
        return true;
    }

    /** Cancels the owner's planned and running jobs for the resource; whether there was any. */
    public boolean cancel(String owner, AEKey key) {
        var any = false;
        for (var request : List.copyOf(pending)) {
            if (request.owner().equals(owner) && request.key().equals(key)) {
                request.plan().cancel(true);
                pending.remove(request);
                any = true;
            }
        }
        for (var job : List.copyOf(started)) {
            if (job.owner().equals(owner) && job.key().equals(key)) {
                if (running(job)) {
                    job.cpu().cancelJob();
                    any = true;
                }
                started.remove(job);
            }
        }
        return any;
    }

    /** Planned or running jobs of the owner, for the run log and tests. */
    public int jobs(String owner) {
        return (int) (pending.stream().filter(request -> request.owner().equals(owner)).count()
                + started.stream().filter(job -> job.owner().equals(owner) && running(job)).count());
    }

    public static int freeCpus(IGrid grid) {
        return (int) grid.getCraftingService().getCpus().stream().filter(cpu -> !cpu.isBusy()).count();
    }

    private static boolean running(Started job) {
        var status = job.cpu().isBusy() ? job.cpu().getJobStatus() : null;
        return status != null && status.crafting().what().equals(job.key());
    }

    private void tick() {
        started.removeIf(job -> !running(job));
        for (var request : List.copyOf(pending)) {
            if (!request.plan().isDone()) {
                continue;
            }
            pending.remove(request);
            if (request.plan().isCancelled() || request.grid().isEmpty()) {
                continue;
            }
            ICraftingPlan plan;
            try {
                plan = request.plan().get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.debug("A program's crafting plan for {} failed", request.key(), e);
                continue;
            }
            if (plan.simulation()) {
                LOGGER.debug("A program's crafting plan for {} misses {}", request.key(), plan.missingItems());
                continue;
            }
            var crafting = request.grid().getCraftingService();
            for (var cpu : crafting.getCpus()) {
                if (!cpu.isBusy() && crafting.submitJob(plan, null, cpu, false, IActionSource.empty()).successful()) {
                    started.add(new Started(request.owner(), cpu, request.key()));
                    break;
                }
            }
        }
    }
}
