package space.controlnet.ae2federation.identity;

import java.util.Optional;

public record IdentitySettlement(IdentityStatus status, Optional<NetworkId> networkId) {
    public boolean canInheritPolicy() {
        return status == IdentityStatus.SETTLED;
    }
}
