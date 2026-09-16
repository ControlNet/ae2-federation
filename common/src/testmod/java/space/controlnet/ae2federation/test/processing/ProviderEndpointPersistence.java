package space.controlnet.ae2federation.test.processing;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointModeEvidence;
import space.controlnet.ae2federation.test.processing.endpoint.EndpointPersistenceObservation;

final class ProviderEndpointPersistence {
    private ProviderEndpointPersistence() {
    }

    static ReloadedEndpoint reload(GameTestHelper helper, NativeProviderLaneFixtures provider,
            EndpointTargetBinding binding, EndpointBlockEntity endpoint) {
        if (!(endpoint.claimState() instanceof ClaimState.Owned owned)) {
            throw new IllegalStateException("A production Endpoint with an owned Claim is required");
        }
        var generation = binding.runtime().generation();
        var oldBindingIdentity = EndpointModeEvidence.identity(binding);
        EndpointPersistenceObservation.begin("endpointfederated", endpoint);
        var savedTag = endpoint.saveWithFullMetadata(helper.getLevel().registryAccess());
        var serialized = savedTag.toString();
        var runtimeReferencesSerialized = serialized.contains("EndpointTargetBinding")
                || serialized.contains("IGridNode") || serialized.contains("PatternProviderLogic")
                || serialized.contains("GenericStackItemStorage") || serialized.contains("GenericStackFluidStorage");
        endpoint.onChunkUnloaded();
        helper.getLevel().setBlockAndUpdate(provider.endpointTargetPosition(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(provider.endpointTargetPosition(),
                ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        var replacement = (EndpointBlockEntity) helper.getLevel().getBlockEntity(provider.endpointTargetPosition());
        replacement.loadWithComponents(savedTag, helper.getLevel().registryAccess());
        replacement.setChanged();
        return new ReloadedEndpoint(endpoint.endpointIdentity(), owned, generation, oldBindingIdentity,
                runtimeReferencesSerialized);
    }

    record ReloadedEndpoint(EndpointIdentity endpoint, ClaimState.Owned claim, long generation,
            String bindingIdentity, boolean runtimeReferencesSerialized) {
    }
}
