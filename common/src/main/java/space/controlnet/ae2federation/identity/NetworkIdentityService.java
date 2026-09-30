package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;

public interface NetworkIdentityService extends IGridService {
    IdentitySettlement settlement();

    NodeLineage lineage(IGridNode node);

    /** Changes whenever a node joins or leaves this Grid, so a scan of the Grid's nodes can be reused until then. */
    long nodeRevision();
}
