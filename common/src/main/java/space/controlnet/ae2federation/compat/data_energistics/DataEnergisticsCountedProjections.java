package space.controlnet.ae2federation.compat.data_energistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.helpers.patternprovider.PatternContainer;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.OptionalLong;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/**
 * Data Energistics' side of {@link DataEnergisticsBatching}: the provider plugin that makes a projection counted, and
 * the answers of a projection's Data Energistics interfaces. Data Energistics finds the plugin by its annotation; only
 * Data Energistics and a projection with those interfaces load this class.
 */
@DataEnergisticsEntrypoint
public final class DataEnergisticsCountedProjections implements DataEnergisticsPlugin {
    private static final ResourceLocation PROJECTION =
            ResourceLocation.fromNamespaceAndPath("ae2federation", "pattern_projection");
    /** The identity's schema; a projection has no fields of its own. */
    private static final int SCHEMA = 1;
    private static final ExternalPatternProviderIdentity IDENTITY =
            record(ExternalPatternProviderIdentity.class, PROJECTION, SCHEMA, ObjectLists.emptyList());
    /**
     * A projection's capacity: one aggregate target, the real provider, which says how many crafts it admits. Built as
     * 3.3's {@code aggregateUnknown()}, which 3.4 removed.
     */
    private static final CountedCraftingCapacity CAPACITY = new CountedCraftingCapacity(CountedCraftingTarget.provider(),
            CountedCraftingRoutingMode.AGGREGATE, OptionalLong.empty(), OptionalLong.empty());

    /** Binds every projection that names itself {@link #IDENTITY} to itself as its counted adapter. */
    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.patternProviders().register(PatternProviderRegistration.counted(record(PatternProviderMetadata.class,
                        PROJECTION, new ProviderIdentityDescriptor.External(PROJECTION, SCHEMA), ObjectLists.emptyList(),
                        ObjectLists.emptyList()),
                context -> (CountedCraftingProviderAdapter) context.provider()));
    }

    /**
     * A new {@code type} through its canonical constructor. Data Energistics 3.4 changed the list components of these
     * records from {@code List} to {@code ObjectList}, so no one constructor call links against both 3.3 and 3.4; an
     * empty {@code ObjectList} fits either.
     */
    private static <T extends Record> T record(Class<T> type, Object... components) {
        var types = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
        try {
            return type.getDeclaredConstructor(types).newInstance(components);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Data Energistics' " + type.getSimpleName() + " has an unknown shape",
                    exception);
        }
    }

    /** Answers a call on one of a projection's Data Energistics interfaces for the projection behind {@code proxy}. */
    static Object invoke(ProjectedProvider projection, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        if (method.getDeclaringClass() == PatternProviderIdentitySource.class) return IDENTITY;
        if (method.getDeclaringClass() == PatternProviderRuntimeLink.class) return ProjectionContainer.INSTANCE;
        return switch (method.getName()) {
            case "captureCapacityFast" -> projection.isLive() ? ObjectLists.singleton(CAPACITY) : ObjectLists.emptyList();
            case "prepareBatch" -> prepareBatch(projection, (IPatternDetails) arguments[0], (KeyCounter[]) arguments[1],
                    (long) arguments[2]);
            // A default method in 3.3, abstract in 3.4: the projection's one target is the provider itself.
            case "prepareBatchForTarget" -> ((CountedCraftingTarget) arguments[3]).providerScoped()
                    ? prepareBatch(projection, (IPatternDetails) arguments[0], (KeyCounter[]) arguments[1],
                            (long) arguments[2])
                    : null;
            default -> {
                if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, arguments);
                throw new UnsupportedOperationException(method.toString());
            }
        };
    }

    /**
     * The real provider's admission of up to {@code requested} crafts of {@code details}, whose hand-over goes through
     * {@code projection}; null when the projection is withdrawn or the real provider admits none.
     */
    private static @Nullable CountedCraftingAdmission prepareBatch(ProjectedProvider projection, IPatternDetails details,
            KeyCounter[] prototype, long requested) {
        if (!projection.isLive() || !(projection.real() instanceof CountedCraftingProviderAdapter real)) return null;
        var admission = real.prepareBatch(details, prototype, requested);
        return admission == null ? null : new ProjectedAdmission(projection, details, admission);
    }

    /**
     * A batch the real provider admitted. The CPU commits it with one craft's inputs, {@code prototype}, and the real
     * provider pushes them {@link #count} times; the consumer is owed {@code count} crafts' outputs. Data Energistics
     * counts a batch as committed once the real provider took its inputs, even when its commit then answers false or
     * throws, and its CPU waits for the outputs: the consumer is owed them then too.
     */
    record ProjectedAdmission(ProjectedProvider projection, IPatternDetails details,
            CountedCraftingAdmission admission) implements CountedCraftingAdmission {
        @Override
        public long count() {
            return admission.count();
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return admission.hasTransferredInputOwnership();
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            long crafts = admission.count();
            var outputs = new ArrayList<GenericStack>();
            for (var output : details.getOutputs()) {
                outputs.add(new GenericStack(output.what(), Math.multiplyExact(output.amount(), crafts)));
            }
            var inputs = new KeyCounter();
            for (var holder : prototype) {
                if (holder != null) inputs.addAll(holder);
            }
            var untouched = copy(prototype);
            var accepted = new boolean[1];
            var failure = new RuntimeException[1];
            projection.pushBatch(details, outputs,
                    times(ProjectedProvider.containerItems(details, prototype), crafts), times(inputs, crafts), () -> {
                        try {
                            accepted[0] = admission.commit(prototype);
                        } catch (RuntimeException exception) {
                            failure[0] = exception;
                        }
                        return accepted[0] || tookInputs(untouched, prototype);
                    });
            if (failure[0] != null) throw failure[0];
            return accepted[0];
        }

        /** Whether the real provider took the batch's inputs, as Data Energistics' committer judges it. */
        private boolean tookInputs(KeyCounter[] untouched, KeyCounter[] prototype) {
            if (!same(untouched, prototype)) return true;
            try {
                return admission.hasTransferredInputOwnership();
            } catch (RuntimeException exception) {
                // Data Energistics treats a provider that cannot tell as having taken them.
                return true;
            }
        }
    }

    private static KeyCounter[] copy(KeyCounter[] counters) {
        var copy = new KeyCounter[counters.length];
        for (int index = 0; index < counters.length; index++) {
            copy[index] = new KeyCounter();
            if (counters[index] != null) copy[index].addAll(counters[index]);
        }
        return copy;
    }

    private static boolean same(KeyCounter[] expected, KeyCounter[] actual) {
        if (expected.length != actual.length) return false;
        for (int index = 0; index < expected.length; index++) {
            var now = actual[index] == null ? new KeyCounter() : actual[index];
            if (!contains(expected[index], now) || !contains(now, expected[index])) return false;
        }
        return true;
    }

    /** Whether {@code actual} holds every amount {@code expected} holds. */
    private static boolean contains(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) return false;
        }
        return true;
    }

    private static KeyCounter times(KeyCounter perCraft, long crafts) {
        var total = new KeyCounter();
        for (var entry : perCraft) total.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), crafts));
        return total;
    }

    /**
     * What a projection names as its pattern container, which Data Energistics needs to bind a plugin. A projection has
     * no pattern inventory of its own: its patterns are the real provider's, and no terminal lists it.
     */
    private enum ProjectionContainer implements PatternContainer {
        INSTANCE;

        @Override
        public @Nullable IGrid getGrid() {
            return null;
        }

        @Override
        public boolean isVisibleInTerminal() {
            return false;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return InternalInventory.empty();
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return PatternContainerGroup.nothing();
        }
    }
}
