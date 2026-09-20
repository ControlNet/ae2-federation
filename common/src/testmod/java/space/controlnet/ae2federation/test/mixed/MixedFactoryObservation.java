package space.controlnet.ae2federation.test.mixed;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;

public final class MixedFactoryObservation {
    private static Mutable active;

    private MixedFactoryObservation() {
    }

    public static synchronized void begin(int iteration, boolean warmup) {
        active = new Mutable(iteration, warmup);
        MixedFactoryRuntimeReceipt.beginScene(iteration, warmup);
    }

    public static synchronized void authorizeMachine(Object machine, Object inputOwner, Object outputOwner,
            Object returnOwner, AEKey input, AEKey output) {
        state().handlers.add(new HandlerAuthority(machine, inputOwner, outputOwner, returnOwner, key(input), key(output)));
    }

    public static synchronized void machineAccepted(Object machine, Object inputOwner, AEKey input, long amount) {
        var authority = state().handlers.stream().filter(candidate -> candidate.machine == machine
                && candidate.inputOwner == inputOwner && candidate.input.equals(key(input))).findFirst().orElseThrow(() ->
                        new IllegalStateException("Mixed machine input lacks pre-operation handler authority"));
        state().acceptedReceipts.add(new AcceptedReceipt(identity(machine), identity(inputOwner), authority.input, amount));
        MixedFactoryRuntimeReceipt.machineAccepted(machine, inputOwner, input, amount);
    }

    public static synchronized void machineTransition(Object machine, Object inputOwner, Object outputOwner,
            Object returnOwner, AEKey input, AEKey output, long amount) {
        var authority = state().handlers.stream().filter(candidate -> candidate.machine == machine
                && candidate.inputOwner == inputOwner && candidate.outputOwner == outputOwner
                && candidate.returnOwner == returnOwner && candidate.input.equals(key(input))
                && candidate.output.equals(key(output))).findFirst().orElseThrow(() ->
                        new IllegalStateException("Mixed handler lacks pre-operation native owner authority"));
        var accepted = state().acceptedReceipts.stream().filter(receipt -> receipt.machineOwner.equals(identity(machine))
                && receipt.inputOwner.equals(identity(inputOwner)) && receipt.input.equals(key(input)))
                .mapToLong(AcceptedReceipt::amount).sum();
        var consumed = state().handlerReceipts.stream().filter(receipt -> receipt.machineOwner.equals(identity(machine))
                && receipt.inputOwner.equals(identity(inputOwner)) && receipt.input.equals(key(input)))
                .mapToLong(HandlerReceipt::consumed).sum();
        if (accepted - consumed < amount) {
            throw new IllegalStateException("Mixed machine transition exceeds capability-accepted input");
        }
        state().handlerReceipts.add(new HandlerReceipt(identity(authority.machine), identity(authority.inputOwner),
                identity(authority.outputOwner), identity(authority.returnOwner), authority.input, authority.output,
                amount, amount, amount));
        MixedFactoryRuntimeReceipt.machineTransition(machine, inputOwner, outputOwner, returnOwner, input, output, amount);
    }

    public static synchronized void observeWaiting(ICraftingLink link, long busyCpus, long blockedInput) {
        if (link != null) {
            MixedFactoryRuntimeReceipt.cpuInFlight(link.getCraftingID().toString(), busyCpus, blockedInput,
                    !link.isDone() && !link.isCanceled());
        }
        if (link != null && !link.isDone() && !link.isCanceled() && busyCpus > 0 && blockedInput > 0) {
            if (state().waitingJobs.add(link.getCraftingID().toString())) {
                MixedFactoryRuntimeReceipt.waiting(link.getCraftingID().toString(), busyCpus, blockedInput);
            }
        }
        state().peakInFlight = Math.max(state().peakInFlight, busyCpus);
    }

    public static synchronized void stocking(Object owner, long before, long moved, long after) {
        if (!state().stockingOwners.contains(owner) || moved == 0 || Math.addExact(before, moved) != after) {
            throw new IllegalStateException("Mixed stocking receipt lacks authorized physical inventory delta");
        }
        state().stockingReceipts.add(new StockingReceipt(identity(owner), before, moved, after));
        MixedFactoryRuntimeReceipt.stocking(owner, before, moved, after);
    }

    public static synchronized void authorizeStocking(Object owner) {
        state().stockingOwners.add(owner);
    }

    public static synchronized Snapshot snapshot() {
        var terminal = TerminalNativeObservation.snapshot();
        var automation = AutomationNativeObservation.snapshot();
        var processing = ProcessingNativeObservation.snapshot();
        return new Snapshot(state().iteration, state().warmup, terminal.beginCalls(), terminal.cpuSubmissions(),
                Set.copyOf(terminal.jobIds()), terminal.serviceIdentity(), terminal.cpuIdentity(),
                processing.stream().filter(receipt -> receipt.operation().equals("push-return")
                        && receipt.detail().equals("accepted=true")).count(),
                processing.stream().filter(receipt -> receipt.operation().equals("return-inject")
                        && receipt.detail().equals("changed=true")).count(),
                Set.copyOf(state().waitingJobs), state().peakInFlight, List.copyOf(state().handlerReceipts),
                List.copyOf(state().stockingReceipts), List.copyOf(processing), automation);
    }

    public static synchronized void close() {
        active = null;
    }

    private static Mutable state() {
        if (active == null) throw new IllegalStateException("No mixed factory observation is active");
        return active;
    }

    private static String key(AEKey key) {
        return key.getId().toString();
    }

    private static String identity(Object owner) {
        return Integer.toUnsignedString(System.identityHashCode(owner));
    }

    public record Snapshot(int iteration, boolean warmup, long planning, long submitted, Set<String> craftingIds,
            String planningService, String cpuOwner, long executing, long returnInjections, Set<String> waitingJobs,
            long peakInFlight, List<HandlerReceipt> handlers, List<StockingReceipt> stocking,
            List<ProcessingNativeObservation.Receipt> processingReceipts,
            AutomationNativeObservation.Snapshot automation) {
    }

    public record HandlerReceipt(String machineOwner, String inputOwner, String outputOwner, String returnOwner,
            String input, String output, long accepted, long consumed, long produced) {
        public long amount() {
            return produced;
        }
    }

    public record AcceptedReceipt(String machineOwner, String inputOwner, String input, long amount) {
    }

    public record StockingReceipt(String interfaceOwner, long before, long moved, long after) {
    }

    private static final class Mutable {
        private final int iteration;
        private final boolean warmup;
        private final Set<HandlerAuthority> handlers = new TreeSet<>((left, right) ->
                (identity(left.machine) + left.input).compareTo(identity(right.machine) + right.input));
        private final Set<Object> stockingOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<AcceptedReceipt> acceptedReceipts = new ArrayList<>();
        private final List<HandlerReceipt> handlerReceipts = new ArrayList<>();
        private final List<StockingReceipt> stockingReceipts = new ArrayList<>();
        private final Set<String> waitingJobs = new TreeSet<>();
        private long peakInFlight;

        private Mutable(int iteration, boolean warmup) {
            this.iteration = iteration;
            this.warmup = warmup;
        }
    }

    private record HandlerAuthority(Object machine, Object inputOwner, Object outputOwner, Object returnOwner,
            String input, String output) {
    }
}
