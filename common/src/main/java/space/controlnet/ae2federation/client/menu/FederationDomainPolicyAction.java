package space.controlnet.ae2federation.client.menu;

public enum FederationDomainPolicyAction {
    NEXT_CONSUMER(0),
    NEXT_PROVIDER(1),
    NEXT_CAPABILITY(2),
    TOGGLE_POLICY(3),
    NEXT_MAPPING_PROVIDER(4),
    NEXT_MAPPING_SLOT(5),
    NEXT_MAPPING_LANE(6),
    TOGGLE_MAPPING(7),
    NEXT_ENDPOINT(8),
    RELEASE_ENDPOINT(9),
    SELECT_TARGET(10),
    PREPARE_RELEASE(11),
    CANCEL_RELEASE(12),
    /** Sets one directional rule to an explicit state; the target carries the rule revision the client observed. */
    SET_POLICY(13),
    /** Names a member network; the target is the network id and the new name. */
    RENAME_NETWORK(14),
    /** Sets one pattern-to-Endpoint wire to an explicit state; the target is a {@code MappingWireTarget}. */
    SET_MAPPING(15);

    private final int wireId;

    FederationDomainPolicyAction(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public boolean takesTarget() {
        return this == SELECT_TARGET || this == SET_POLICY || this == RENAME_NETWORK || this == SET_MAPPING;
    }

    public static FederationDomainPolicyAction fromWireId(int wireId) {
        for (var action : values()) {
            if (action.wireId == wireId) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown Federation Domain policy action");
    }
}
