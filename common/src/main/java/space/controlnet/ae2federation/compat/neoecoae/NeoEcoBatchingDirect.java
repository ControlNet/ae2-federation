package space.controlnet.ae2federation.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/** {@link NeoEcoBatching} through Neo ECO's own types; loaded only after a provider with its interfaces was seen. */
final class NeoEcoBatchingDirect {
    private NeoEcoBatchingDirect() {
    }

    static Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        return switch (method.getName()) {
            case "eco$prepareFastPath" -> prepare(projection, (ECOBatchDispatchContext) arguments[0]);
            // Exact batches need the CPU's job id, which the real provider never gets.
            case "eco$prepareExactFastPath" -> null;
            case "eco$getAvailableParallelSlots" -> projection.isLive()
                    ? ((ECOParallelCraftingProvider) projection.real()).eco$getAvailableParallelSlots() : 0;
            case "eco$pushPatternBatch" -> push(projection, (IPatternDetails) arguments[0],
                    (KeyCounter[]) arguments[1], (long) arguments[2]);
            default -> {
                if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, arguments);
                throw new UnsupportedOperationException(method.toString());
            }
        };
    }

    /**
     * {@code ECOFastPathDispatchProvider}: the real provider's preparation of {@code request}, made without its job id,
     * whose hand-over goes through {@code projection}; null when the projection is withdrawn or the real provider offers
     * no batch.
     */
    private static ECOFastPathDispatchProvider.@Nullable Preparation prepare(ProjectedProvider projection,
            ECOBatchDispatchContext request) {
        if (!projection.isLive()) return null;
        var details = request.pattern();
        var jobless = new ECOBatchDispatchContext(details, request.inputs(), request.outputs(),
                request.containerItems(), request.level(), null);
        var prepared = ((ECOFastPathDispatchProvider) projection.real()).eco$prepareFastPath(jobless);
        if (prepared == null) return null;
        var handOver = prepared.dispatch();
        return new ECOFastPathDispatchProvider.Preparation(prepared.capacity(), prepared.statefulCalculator(),
                prepared.statefulCalculatorRequired(), batch -> handOver(projection, details, handOver, batch),
                prepared.supportsExactInputs());
    }

    /** Hands {@code batch} over through {@code projection}, which owes its outputs once the real provider took it. */
    private static boolean handOver(ProjectedProvider projection, IPatternDetails details,
            Predicate<ECOFastPathDispatchProvider.Batch> handOver, ECOFastPathDispatchProvider.Batch batch) {
        List<GenericStack> outputs;
        KeyCounter containerItems;
        KeyCounter inputs;
        try {
            outputs = batch.outputTotal();
            containerItems = counter(batch.remainingTotal());
            inputs = inputs(batch);
        } catch (LinkageError otherApi) {
            // Neo ECO calls this later, outside NeoEcoBatching's guard: decline before anything is taken.
            return false;
        }
        return projection.pushBatch(details, outputs, containerItems, inputs, () -> handOver.test(batch));
    }

    /** What a batch takes: its input total, or for exact inputs their totals as far as they fit in a long. */
    private static KeyCounter inputs(ECOFastPathDispatchProvider.Batch batch) {
        var total = batch.inputTotal();
        if (!total.isEmpty()) return counter(total);
        var out = new KeyCounter();
        for (var entry : batch.exactInputTotal().entrySet()) {
            out.add(entry.getKey(), entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
        }
        return out;
    }

    /**
     * {@code ECOParallelCraftingProvider}: pushes {@code crafts} runs of {@code details}, whose inputs {@code holders}
     * hold, to the real provider as one batch without the CPU's job id.
     */
    private static boolean push(ProjectedProvider projection, IPatternDetails details, KeyCounter[] holders,
            long crafts) {
        var outputs = new ArrayList<GenericStack>();
        for (var output : details.getOutputs()) {
            outputs.add(new GenericStack(output.what(), Math.multiplyExact(output.amount(), crafts)));
        }
        var inputs = new KeyCounter();
        for (var holder : holders) {
            if (holder != null) inputs.addAll(holder);
        }
        var real = (ECOParallelCraftingProvider) projection.real();
        return projection.pushBatch(details, outputs, ProjectedProvider.containerItems(details, holders), inputs,
                () -> real.eco$pushPatternBatch(details, holders, crafts, null));
    }

    private static KeyCounter counter(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        for (var stack : stacks) counter.add(stack.what(), stack.amount());
        return counter;
    }
}
