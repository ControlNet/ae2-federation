package space.controlnet.ae2federation.compat.data_energistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.crafting.projection.ProjectedProvider;

/**
 * The consumer is owed a batch exactly when Data Energistics' committer counts it as committed: the real provider
 * accepted it, or took its inputs whatever its commit then answered.
 */
class DataEnergisticsCountedProjectionsTest {
    @Test
    void anAcceptedBatchIsOwed() {
        var projection = new Projection();
        assertTrue(admission(projection, new Admission(true, false, false)).commit(prototype()));
        assertTrue(projection.owed);
    }

    @Test
    void aRefusedBatchWhoseInputsStayIsNotOwed() {
        var projection = new Projection();
        assertFalse(admission(projection, new Admission(false, false, false)).commit(prototype()));
        assertFalse(projection.owed);
    }

    @Test
    void aBatchWhoseInputsWereTakenIsOwedEvenWhenCommitAnswersFalse() {
        var projection = new Projection();
        var taking = new Admission(false, false, false) {
            @Override
            public boolean commit(KeyCounter[] prototype) {
                prototype[0].clear();
                return false;
            }
        };
        assertFalse(admission(projection, taking).commit(prototype()));
        assertTrue(projection.owed);
    }

    @Test
    void aBatchTheProviderOwnsIsOwedAndItsFailureStillReachesTheCpu() {
        var projection = new Projection();
        var failing = new Admission(false, true, true);
        var thrown = assertThrows(IllegalStateException.class, () -> admission(projection, failing).commit(prototype()));
        assertSame(Admission.FAILURE, thrown);
        assertTrue(projection.owed);
    }

    @Test
    void aProviderThatCannotTellOwnershipIsTakenToOwnTheBatch() {
        var projection = new Projection();
        var unsure = new Admission(false, false, false) {
            @Override
            public boolean hasTransferredInputOwnership() {
                throw new IllegalStateException("unknown");
            }
        };
        assertFalse(admission(projection, unsure).commit(prototype()));
        assertTrue(projection.owed);
    }

    /** The identity is built through its record's canonical constructor, which 3.3 and 3.4 declare differently. */
    @Test
    void theIdentityNamesTheProjection() throws Throwable {
        var identity = (ExternalPatternProviderIdentity) DataEnergisticsCountedProjections.invoke(new Projection(), null,
                PatternProviderIdentitySource.class.getMethod("providerIdentity"), new Object[0]);
        assertEquals(ResourceLocation.fromNamespaceAndPath("ae2federation", "pattern_projection"), identity.type());
        assertTrue(identity.canonicalFieldsFast().isEmpty());
    }

    /** A projection's only target is the provider itself; 3.4 asks for it without a default method. */
    @Test
    void aTargetOtherThanTheProviderIsNotPrepared() throws Throwable {
        var method = CountedCraftingProviderAdapter.class.getMethod("prepareBatchForTarget", IPatternDetails.class,
                KeyCounter[].class, long.class, CountedCraftingTarget.class);
        assertNull(DataEnergisticsCountedProjections.invoke(new Projection(), null, method,
                new Object[] {new Pattern(), prototype(), 1L, CountedCraftingTarget.route("machine")}));
    }

    private static CountedCraftingAdmission admission(Projection projection, CountedCraftingAdmission real) {
        return new DataEnergisticsCountedProjections.ProjectedAdmission(projection, new Pattern(), real);
    }

    private static KeyCounter[] prototype() {
        var inputs = new KeyCounter();
        inputs.add(new Key(), 2);
        return new KeyCounter[] {inputs};
    }

    /** A real provider's admission of one craft; {@code throwing} makes its commit fail after the hand-over. */
    private static class Admission implements CountedCraftingAdmission {
        static final IllegalStateException FAILURE = new IllegalStateException("provider failed");
        private final boolean accepts;
        private final boolean owns;
        private final boolean throwing;

        Admission(boolean accepts, boolean owns, boolean throwing) {
            this.accepts = accepts;
            this.owns = owns;
            this.throwing = throwing;
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return owns;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            if (throwing) throw FAILURE;
            return accepts;
        }
    }

    /** Owes the batch as {@code PatternProjection.pushBatch} does: when the hand-over answers true. */
    private static final class Projection implements ProjectedProvider {
        boolean owed;

        @Override
        public boolean isLive() {
            return true;
        }

        @Override
        public ICraftingProvider real() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean pushBatch(IPatternDetails details, List<GenericStack> outputs, KeyCounter containerItems,
                KeyCounter inputs, BooleanSupplier handOver) {
            if (!handOver.getAsBoolean()) return false;
            owed = true;
            return true;
        }
    }

    private record Pattern() implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
    }

    /** Test-only key: a KeyCounter needs a key, and AE2's own need a bootstrapped registry. */
    private static final class Key extends AEKey {
        public AEKeyType getType() { return null; }
        public AEKey dropSecondary() { return this; }
        public CompoundTag toTag(HolderLookup.Provider registries) { return new CompoundTag(); }
        public Object getPrimaryKey() { return this; }
        public ResourceLocation getId() { return null; }
        public void writeToPacket(RegistryFriendlyByteBuf buffer) { }
        protected Component computeDisplayName() { return Component.empty(); }
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        public boolean hasComponents() { return false; }
    }
}
