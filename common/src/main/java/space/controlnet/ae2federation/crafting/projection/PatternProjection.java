package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One real pattern provider of another network, offered to a consumer network's crafting service as AE2 offers its
 * own providers: the consumer's CPU plans with these patterns, takes the inputs from its own storage (which includes
 * what its storage rule shows of the other network) and pushes them here, and the push goes straight to the real
 * provider. The patterns are the real provider's own objects, so AE2's {@code patterns.contains} check in the push
 * passes, and nothing is wrapped or copied.
 *
 * <p>A withdrawn projection is first marked not live, so a CPU holding it from an earlier plan cannot push through it
 * before AE2 drops it from its provider list.
 *
 * <p>An add-on may mark its own providers with an interface that has no methods, such as UselessMod's
 * {@code SmartDoublingCraftingProvider}, to say they take what it pushes to them only, here its scaled patterns. The
 * projection carries the real provider's add-on markers too, so the add-on treats it as it treats the real provider, and
 * the push still goes through {@link #pushPattern} to the real provider. AE2 then holds {@link #registered()}, a proxy
 * with those markers, instead of the projection itself.
 *
 * <p>The proxy also shows AE2's own provider logic ({@link #shownLogic}) through
 * {@link ProjectedProviderLogic#getLogic()}, so Neo ECO's CPU pushes many runs as one scaled pattern here as it does to
 * that logic on its own network, still through {@link #pushPattern}. Other logic stays hidden: add-ons that recognise
 * their own logic, such as AE2LT's overloaded provider, push into it directly, past the projection and its return
 * ledger.
 */
final class PatternProjection implements ICraftingProvider {
    /** Captures dispatch context before external callbacks; the returned action runs only for an accepted push. */
    interface PushListener {
        /**
         * {@code containerItems}: what the pushed inputs leave behind; {@code inputs}: the inputs themselves. Both are
         * counted before the push, which may empty the holder.
         */
        Runnable prepare(PatternProjection projection, IPatternDetails details, KeyCounter containerItems,
                KeyCounter inputs);
    }

    private final ICraftingProvider real;
    private final PushListener listener;
    private final ICraftingProvider registered;
    private List<IPatternDetails> patterns;
    private boolean live = true;
    private int priority;
    /** The {@link #markPushed} sequence of its last accepted push; 0 before any. */
    private long lastPush;

    PatternProjection(ICraftingProvider real, PushListener listener) {
        this.real = Objects.requireNonNull(real);
        this.listener = Objects.requireNonNull(listener);
        patterns = List.copyOf(real.getAvailablePatterns());
        priority = real.getPatternPriority();
        registered = withMarkers(this, addonMarkers(real.getClass()), shownLogic() != null);
    }

    /**
     * The real provider when it is AE2's own provider logic, which add-ons only read and push to through
     * {@link #pushPattern}: a plain AE2 Pattern Provider's, or a Federation Pattern Provider's Lane. Null otherwise.
     */
    Object shownLogic() {
        return real.getClass() == PatternProviderLogic.class
                || real instanceof space.controlnet.ae2federation.ae2.processing.NativeProviderLane ? real : null;
    }

    /** What the consumer's crafting service holds for this projection: itself, or a proxy with add-on markers. */
    ICraftingProvider registered() {
        return registered;
    }

    /** Whether AE2 holds {@code provider} for a projection, rather than for a real provider. */
    static boolean isProjection(ICraftingProvider provider) {
        return provider instanceof PatternProjection || provider instanceof ProjectionShowingLogic
                || Proxy.isProxyClass(provider.getClass()) && Proxy.getInvocationHandler(provider) instanceof Markers;
    }

    /**
     * The interfaces of {@code type} and its superclasses that declare no methods, as add-ons mark their providers, left
     * out those of Java, Minecraft, NeoForge and AE2 themselves.
     */
    static List<Class<?>> addonMarkers(Class<?> type) {
        var markers = new LinkedHashSet<Class<?>>();
        var pending = new ArrayList<Class<?>>();
        for (var current = type; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        var seen = new java.util.HashSet<Class<?>>();
        while (!pending.isEmpty()) {
            var candidate = pending.removeLast();
            if (!seen.add(candidate)) continue;
            pending.addAll(List.of(candidate.getInterfaces()));
            if (candidate.getMethods().length == 0 && !platform(candidate.getName())) markers.add(candidate);
        }
        return List.copyOf(markers);
    }

    private static boolean platform(String name) {
        for (var prefix : Set.of("java.", "javax.", "jdk.", "sun.", "com.mojang.", "net.minecraft.",
                "net.neoforged.", "appeng.")) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * {@code projection} as a provider that also has {@code markers}, and {@link ProjectedProviderLogic} with
     * {@code showsLogic}: the projection itself when there is nothing to add, a {@link ProjectionShowingLogic} for the
     * logic alone, and a proxy for markers. With markers that cannot be combined, such as when they live in class
     * loaders that cannot see each other, the projection itself.
     */
    private static ICraftingProvider withMarkers(PatternProjection projection, List<Class<?>> markers,
            boolean showsLogic) {
        if (markers.isEmpty()) return showsLogic ? new ProjectionShowingLogic(projection) : projection;
        var interfaces = new ArrayList<Class<?>>();
        interfaces.add(ICraftingProvider.class);
        if (showsLogic) interfaces.add(ProjectedProviderLogic.class);
        interfaces.addAll(markers);
        try {
            return (ICraftingProvider) Proxy.newProxyInstance(markers.getFirst().getClassLoader(),
                    interfaces.toArray(Class<?>[]::new), new Markers(projection));
        } catch (IllegalArgumentException exception) {
            // The markers matter more than the logic: an add-on that marks its providers pushes only to marked ones.
            return showsLogic ? withMarkers(projection, markers, false) : projection;
        }
    }

    /** Answers every provider call with the projection's own, and is equal only to itself. */
    private record Markers(PatternProjection projection) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == ProjectedProviderLogic.class) return projection.shownLogic();
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "Projection of " + projection.real;
                };
            }
            try {
                return method.invoke(projection, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    ICraftingProvider real() {
        return real;
    }

    /** Records an accepted push as the {@code sequence}th through any projection, so a provider's latest pusher shows. */
    void markPushed(long sequence) {
        lastPush = sequence;
    }

    long lastPush() {
        return lastPush;
    }

    /**
     * Takes the real provider's current patterns; true when they changed, so AE2 must re-read them. Patterns are
     * compared as objects, not by equality: a provider decodes new objects for the same pattern when an addon marks
     * them, such as ExtendedAE-Plus' Smart Doubling, and the consumer must plan with the marked ones.
     */
    boolean refresh() {
        var current = real.getAvailablePatterns();
        int currentPriority = real.getPatternPriority();
        if (sameObjects(current, patterns) && currentPriority == priority) return false;
        priority = currentPriority;
        patterns = List.copyOf(current);
        return true;
    }

    private static boolean sameObjects(List<IPatternDetails> current, List<IPatternDetails> known) {
        if (current.size() != known.size()) return false;
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index) != known.get(index)) return false;
        }
        return true;
    }

    void withdraw() {
        live = false;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return live ? patterns : List.of();
    }

    @Override
    public int getPatternPriority() {
        return priority;
    }

    /**
     * Pushes to the real provider. A machine may take the inputs out of the holder as it accepts them (AE2's Molecular
     * Assembler empties every counter), so the container items they leave behind are counted first and owed only once
     * the push is accepted. The holder itself goes to the provider untouched.
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!live) return false;
        var containerItems = containerItems(patternDetails, inputHolder);
        var inputs = new KeyCounter();
        for (var holder : inputHolder) {
            if (holder != null) inputs.addAll(holder);
        }
        var accepted = listener.prepare(this, patternDetails, containerItems, inputs);
        if (!real.pushPattern(patternDetails, inputHolder)) return false;
        accepted.run();
        return true;
    }

    /**
     * The container items a push of {@code inputs} leaves behind, such as the empty bucket of a water bucket, as AE2's
     * {@code CraftingCpuHelper.extractPatternInputs} counts them for the CPU that waits for them: for each input slot,
     * the remaining key of each key the CPU actually put there (a substitute, or water as a fluid, may leave nothing),
     * once per template it took.
     */
    static KeyCounter containerItems(IPatternDetails details, KeyCounter[] inputs) {
        var containerItems = new KeyCounter();
        var patternInputs = details.getInputs();
        for (int index = 0; index < inputs.length && index < patternInputs.length; index++) {
            if (inputs[index] == null) continue;
            var input = patternInputs[index];
            for (var stack : inputs[index]) {
                var remaining = input.getRemainingKey(stack.getKey());
                if (remaining != null) {
                    containerItems.add(remaining, stack.getLongValue() / templateAmount(input, stack.getKey()));
                }
            }
        }
        return containerItems;
    }

    /** The amount of the template AE2 took {@code key} for: an exact possible input first, then a fuzzy one. */
    private static long templateAmount(IPatternDetails.IInput input, AEKey key) {
        for (var template : input.getPossibleInputs()) {
            if (template.what().equals(key)) return Math.max(1, template.amount());
        }
        for (var template : input.getPossibleInputs()) {
            if (template.what().fuzzyEquals(key, FuzzyMode.IGNORE_ALL)) return Math.max(1, template.amount());
        }
        return 1;
    }

    @Override
    public boolean isBusy() {
        return !live || real.isBusy();
    }
}
