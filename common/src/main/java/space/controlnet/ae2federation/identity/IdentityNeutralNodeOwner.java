package space.controlnet.ae2federation.identity;

/**
 * Marks the owner of a Federation boundary node (Router face, Bridge side). Such a node only attaches to a native
 * network and never carries network history: it publishes no identity claim and follows the network's established
 * NetworkId, so placing a boundary before (or after) the network it joins cannot make that network ambiguous.
 */
public interface IdentityNeutralNodeOwner {
}
