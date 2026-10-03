package space.controlnet.ae2federation.client.policy;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One Endpoint's energy switch set to an explicit state, the Endpoint named as a mapping choice
 * ({@code endpointUuid:instanceEpoch}); unlike a toggle, repeating a request never reverses it.
 */
public record EndpointEnergyTarget(String endpoint, boolean on) {
    private static final Pattern ENDPOINT = Pattern.compile("[0-9a-fA-F-]{36}:[0-9]{1,19}");

    public EndpointEnergyTarget {
        if (!ENDPOINT.matcher(endpoint).matches()) {
            throw new IllegalArgumentException("Invalid Endpoint energy target");
        }
    }

    public String encode() {
        return endpoint + "/" + (on ? 1 : 0);
    }

    public static Optional<EndpointEnergyTarget> parse(String value) {
        var fields = value.split("/", -1);
        if (fields.length != 2 || !(fields[1].equals("1") || fields[1].equals("0"))) return Optional.empty();
        try {
            return Optional.of(new EndpointEnergyTarget(fields[0], fields[1].equals("1")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
