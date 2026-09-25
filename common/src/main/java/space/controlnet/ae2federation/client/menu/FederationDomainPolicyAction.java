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
    RELEASE_ENDPOINT(9);

    private final int wireId;

    FederationDomainPolicyAction(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
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
