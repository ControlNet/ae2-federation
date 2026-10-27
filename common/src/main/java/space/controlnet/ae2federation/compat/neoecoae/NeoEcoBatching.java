package space.controlnet.ae2federation.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import space.controlnet.ae2federation.crafting.projection.AddonProjections;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/**
 * Neo ECO's batches through a projection. Neo ECO's CPU (and AE2's, with Neo ECO's mixin) pushes many crafts as one
 * batch only to a provider with one of its batch interfaces, which it recognises by type:
 * <ul>
 *   <li>{@code ECOFastPathDispatchProvider}, such as a crafting system's pattern bus: the provider prepares the batch,
 *   the CPU takes the inputs and hands the batch over;</li>
 *   <li>{@code ECOParallelCraftingProvider}, such as the Large Integrated Working Station's interface: the provider
 *   says how many crafts it takes at once, and the CPU pushes them as one.</li>
 * </ul>
 * A projection of such a provider has the same interface. The real provider prepares or takes each batch, and the
 * hand-over goes through {@link ProjectedProvider#pushBatch}, which owes the batch's outputs as a push owes a
 * pattern's.
 *
 * <p>The real provider never gets the CPU's job id. With one, Neo ECO's machines return a batch only to that job's CPU
 * on their own network, where a consumer's CPU is not; without one, they store the outputs on their network, where the
 * return router takes them, as after an ordinary push. Neo ECO's exact batches need the job id, so a projection does
 * not offer them.
 */
public final class NeoEcoBatching implements AddonProjections {
    private static final String PACKAGE = "cn.dancingsnow.neoecoae.api.me.provider.";
    private static final String FAST_PATH = PACKAGE + "ECOFastPathDispatchProvider";
    private static final String PARALLEL = PACKAGE + "ECOParallelCraftingProvider";

    /** One of Neo ECO's batch interfaces, as this class knows it; null for any other or a changed one. */
    private static final ClassValue<Api> APIS = new ClassValue<>() {
        @Override
        protected Api computeValue(Class<?> type) {
            try {
                return switch (type.getName()) {
                    case FAST_PATH -> new FastPath(type);
                    case PARALLEL -> new Parallel(type);
                    default -> null;
                };
            } catch (ReflectiveOperationException | RuntimeException unknownApi) {
                return null;
            }
        }
    };

    /** Neo ECO's batch interfaces among {@code type}'s whose API is the one this class knows. */
    @Override
    public List<Class<?>> interfaces(Class<?> type) {
        var found = new ArrayList<Class<?>>();
        var pending = new ArrayList<Class<?>>();
        for (var current = type; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        var seen = new HashSet<Class<?>>();
        while (!pending.isEmpty()) {
            var candidate = pending.removeLast();
            if (!seen.add(candidate)) continue;
            if (APIS.get(candidate) != null) found.add(candidate);
            pending.addAll(List.of(candidate.getInterfaces()));
        }
        return found;
    }

    /** Answers a call on one of Neo ECO's batch interfaces for the projection behind {@code proxy}. */
    @Override
    public Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        var answer = APIS.get(method.getDeclaringClass()).invoke(projection, method, arguments);
        if (answer != Api.UNKNOWN) return answer;
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
        throw new UnsupportedOperationException(method.toString());
    }

    private interface Api {
        /** What {@link #invoke} returns for a method it does not know. */
        Object UNKNOWN = new Object();

        Object invoke(ProjectedProvider projection, Method method, Object[] arguments) throws Throwable;
    }

    /** {@code ECOFastPathDispatchProvider}: the real provider prepares each batch without the CPU's job id. */
    private static final class FastPath implements Api {
        private final Method prepare;
        private final Constructor<?> context;
        private final Method pattern;
        private final Method inputs;
        private final Method outputs;
        private final Method containerItems;
        private final Method level;
        private final Constructor<?> preparation;
        private final Method[] preparationParts;
        private final int dispatchPart;
        private final Method inputTotal;
        private final Method outputTotal;
        private final Method remainingTotal;
        private final Method exactInputTotal;

        FastPath(Class<?> provider) throws ReflectiveOperationException {
            Method found = null;
            for (var method : provider.getMethods()) {
                if (method.getName().equals("eco$prepareFastPath") && method.getParameterCount() == 1) found = method;
            }
            if (found == null) throw new NoSuchMethodException(FAST_PATH + ".eco$prepareFastPath");
            prepare = found;
            var contextType = prepare.getParameterTypes()[0];
            context = contextType.getConstructor(IPatternDetails.class, List.class, List.class, List.class,
                    Level.class, UUID.class);
            pattern = contextType.getMethod("pattern");
            inputs = contextType.getMethod("inputs");
            outputs = contextType.getMethod("outputs");
            containerItems = contextType.getMethod("containerItems");
            level = contextType.getMethod("level");
            var preparationType = prepare.getReturnType();
            var components = preparationType.getRecordComponents();
            var types = new Class<?>[components.length];
            preparationParts = new Method[components.length];
            int dispatch = -1;
            for (int index = 0; index < components.length; index++) {
                types[index] = components[index].getType();
                preparationParts[index] = components[index].getAccessor();
                if (components[index].getName().equals("dispatch") && types[index] == Predicate.class) dispatch = index;
            }
            if (dispatch < 0) throw new NoSuchMethodException(preparationType.getName() + ".dispatch");
            dispatchPart = dispatch;
            preparation = preparationType.getDeclaredConstructor(types);
            var batchType = Class.forName(FAST_PATH + "$Batch", false, provider.getClassLoader());
            inputTotal = batchType.getMethod("inputTotal");
            outputTotal = batchType.getMethod("outputTotal");
            remainingTotal = batchType.getMethod("remainingTotal");
            exactInputTotal = batchType.getMethod("exactInputTotal");
        }

        @Override
        public Object invoke(ProjectedProvider projection, Method method, Object[] arguments) throws Throwable {
            return switch (method.getName()) {
                case "eco$prepareFastPath" -> prepare(projection, arguments[0]);
                case "eco$prepareExactFastPath" -> null;
                default -> UNKNOWN;
            };
        }

        /**
         * The real provider's preparation of {@code request}, made without its job id, whose hand-over goes through
         * {@code projection}; null when the projection is withdrawn or the real provider offers no batch.
         */
        private Object prepare(ProjectedProvider projection, Object request) throws Throwable {
            if (!projection.isLive()) return null;
            var details = (IPatternDetails) call(pattern, request);
            var jobless = construct(context, details, call(inputs, request), call(outputs, request),
                    call(containerItems, request), call(level, request), null);
            var prepared = call(prepare, projection.real(), jobless);
            if (prepared == null) return null;
            var parts = new Object[preparationParts.length];
            for (int index = 0; index < parts.length; index++) parts[index] = call(preparationParts[index], prepared);
            @SuppressWarnings("unchecked")
            var handOver = (Predicate<Object>) parts[dispatchPart];
            parts[dispatchPart] = (Predicate<Object>) batch -> projection.pushBatch(details,
                    stacks(batch, outputTotal), counter(stacks(batch, remainingTotal)), batchInputs(batch),
                    () -> handOver.test(batch));
            return construct(preparation, parts);
        }

        /** What a batch takes: its input total, or for exact inputs their totals as far as they fit in a long. */
        private KeyCounter batchInputs(Object batch) {
            var total = stacks(batch, inputTotal);
            if (!total.isEmpty()) return counter(total);
            @SuppressWarnings("unchecked")
            var exact = (Map<AEKey, BigInteger>) unchecked(exactInputTotal, batch);
            var out = new KeyCounter();
            for (var entry : exact.entrySet()) {
                out.add(entry.getKey(), entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        private static List<GenericStack> stacks(Object batch, Method part) {
            return (List<GenericStack>) unchecked(part, batch);
        }

        private static KeyCounter counter(List<GenericStack> stacks) {
            var counter = new KeyCounter();
            for (var stack : stacks) counter.add(stack.what(), stack.amount());
            return counter;
        }
    }

    /**
     * {@code ECOParallelCraftingProvider}: the real provider's free parallel slots while the projection is live, and
     * each batch pushed to it without the CPU's job id.
     */
    private static final class Parallel implements Api {
        private final Method slots;
        private final Method pushBatch;

        Parallel(Class<?> provider) throws ReflectiveOperationException {
            slots = provider.getMethod("eco$getAvailableParallelSlots");
            pushBatch = provider.getMethod("eco$pushPatternBatch", IPatternDetails.class, KeyCounter[].class,
                    long.class, UUID.class);
        }

        @Override
        public Object invoke(ProjectedProvider projection, Method method, Object[] arguments) throws Throwable {
            return switch (method.getName()) {
                case "eco$getAvailableParallelSlots" -> projection.isLive() ? call(slots, projection.real()) : 0;
                case "eco$pushPatternBatch" -> push(projection, (IPatternDetails) arguments[0],
                        (KeyCounter[]) arguments[1], (long) arguments[2]);
                default -> UNKNOWN;
            };
        }

        /** Pushes {@code crafts} runs of {@code details}, whose inputs {@code holders} hold, as one batch. */
        private boolean push(ProjectedProvider projection, IPatternDetails details, KeyCounter[] holders,
                long crafts) {
            var outputs = new ArrayList<GenericStack>();
            for (var output : details.getOutputs()) {
                outputs.add(new GenericStack(output.what(), Math.multiplyExact(output.amount(), crafts)));
            }
            var inputs = new KeyCounter();
            for (var holder : holders) {
                if (holder != null) inputs.addAll(holder);
            }
            return projection.pushBatch(details, outputs, ProjectedProvider.containerItems(details, holders), inputs,
                    () -> (boolean) unchecked(pushBatch, projection.real(), details, holders, crafts, null));
        }
    }

    private static Object unchecked(Method method, Object target, Object... arguments) {
        try {
            return call(method, target, arguments);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object call(Method method, Object target, Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Object construct(Constructor<?> constructor, Object... arguments) throws Throwable {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
