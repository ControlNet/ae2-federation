package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;

public interface NetworkIdentityService extends IGridService {
    IdentitySettlement settlement();

    NodeLineage lineage(IGridNode node);
}
