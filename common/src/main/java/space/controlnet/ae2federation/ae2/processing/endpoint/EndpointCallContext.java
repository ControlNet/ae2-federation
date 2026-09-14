package space.controlnet.ae2federation.ae2.processing.endpoint;

public record EndpointCallContext(Purpose purpose, long generation) {
    public enum Purpose {
        LOCAL_INPUT,
        FEDERATED_INPUT,
        RETURN_INSERT
    }
}
