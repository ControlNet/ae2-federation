package space.controlnet.ae2federation.identity;

public enum IdentityStatus {
    SETTLED,
    NEW_NETWORK,
    PARTIAL_LOAD,
    COPIED_LIVE_IDENTITY,
    AMBIGUOUS_SPLIT,
    AMBIGUOUS_MERGE,
    CONFLICTING_NODE_DATA
}
