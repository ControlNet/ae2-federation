package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** A Federation menu that takes sequenced action requests: the domain workspace or the Provider screen. */
interface FederationMenuHolder {
    FederationDomainPolicyActionResult dispatch(ServerPlayer player, ModularUIContainerMenu menu,
            FederationDomainPolicyActionRequest request);

    Optional<FederationDomainPolicyActionRequest> currentRequest(ModularUIContainerMenu menu, FederationDomainPolicyAction action);

    long currentSequence();

    String currentMappingStatus();

    void acceptReply(UUID nonce, UUID requestId, long sequence, FederationDomainPolicyActionResult result);

    /** Where the Provider the workspace was opened from is, for its way back to the Provider screen; empty elsewhere. */
    default Optional<net.minecraft.core.BlockPos> returnProvider() {
        return Optional.empty();
    }
}
