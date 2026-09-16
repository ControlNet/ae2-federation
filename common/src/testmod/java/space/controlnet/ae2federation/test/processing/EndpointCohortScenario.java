package space.controlnet.ae2federation.test.processing;

enum EndpointCohortScenario {
    BUSY("busy"),
    RESULT_LOCKED("result-locked"),
    REJECTING("rejecting"),
    RETURN_CONGESTED("return-congested"),
    ELIGIBLE("eligible");

    private final String serialized;

    EndpointCohortScenario(String serialized) {
        this.serialized = serialized;
    }

    String serialized() {
        return serialized;
    }
}
