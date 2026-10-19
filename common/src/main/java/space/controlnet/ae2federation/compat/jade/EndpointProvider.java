package space.controlnet.ae2federation.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;

/**
 * A Processing Endpoint's mode and whether a Provider owns it, in place of AE2's "Device Offline": an idle Endpoint's
 * node waits for a Provider, which reads as broken when it is not.
 */
enum EndpointProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "endpoint");
    /** AE2's grid node state line ({@code appeng.integration.modules.igtooltip.TooltipIds.GRID_NODE_STATE}). */
    private static final ResourceLocation AE2_NODE_STATE = ResourceLocation.fromNamespaceAndPath("ae2", "grid_node_state");
    private static final String LOCAL = "EndpointLocal";
    private static final String CLAIMED = "EndpointClaimed";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof EndpointBlockEntity endpoint)) return;
        data.putBoolean(LOCAL, endpoint.nativeProviderOnFederationFace());
        data.putBoolean(CLAIMED, endpoint.claimState() instanceof ClaimState.Owned);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.remove(AE2_NODE_STATE);
        var data = accessor.getServerData();
        if (data.contains(LOCAL)) tooltip.add(FederationJadeText.endpoint(data.getBoolean(LOCAL), data.getBoolean(CLAIMED)));
    }

    /** After AE2's body lines (priority 1000), so the node state line is there to remove. */
    @Override
    public int getDefaultPriority() {
        return 1500;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
