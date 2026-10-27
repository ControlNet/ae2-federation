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
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
    @SuppressWarnings("removal")
    private static final ExternalPatternProviderIdentity IDENTITY =
            new ExternalPatternProviderIdentity(PROJECTION, SCHEMA, List.of());
    /** A projection's capacity: one aggregate target, the real provider, which says how many crafts it admits. */
    private static final CountedCraftingCapacity CAPACITY = CountedCraftingCapacity.aggregateUnknown();

    /** Binds every projection that names itself {@link #IDENTITY} to itself as its counted adapter. */
    @Override
    @SuppressWarnings("removal")
    public void register(DataEnergisticsRegistry registry) {
        registry.patternProviders().registerFactory(new PatternProviderMetadata(PROJECTION,
                        new ProviderIdentityDescriptor.External(PROJECTION, SCHEMA), List.of(), List.of()),
                context -> (CountedCraftingProviderAdapter) context.provider());
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
     * provider pushes them {@link #count} times; the consumer is owed {@code count} crafts' outputs.
     */
    private record ProjectedAdmission(ProjectedProvider projection, IPatternDetails details,
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
            return projection.pushBatch(details, outputs,
                    times(ProjectedProvider.containerItems(details, prototype), crafts), times(inputs, crafts),
                    () -> admission.commit(prototype));
        }
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
