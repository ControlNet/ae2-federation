package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;

public interface NetworkIdentityService extends IGridService {
    IdentitySettlement settlement();

    NodeLineage lineage(IGridNode node);

    /** Changes whenever a node joins or leaves this Grid, so a scan of the Grid's nodes can be reused until then. */
    long nodeRevision();

    /**
     * Settles this Grid's pending merge on {@code survivor}, as a player chose: the other ids' names and rules are
     * deleted and every node takes the survivor's id. False, changing nothing, when this Grid is no pending merge or
     * {@code survivor} is not one of its ids.
     */
    boolean keep(NetworkId survivor);
}
