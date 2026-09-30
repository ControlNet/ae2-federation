package space.controlnet.ae2federation.client.policy;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One explicit wire state between a pattern slot of the selected Provider and an Endpoint choice
 * ({@code endpointUuid:instanceEpoch}); unlike a toggle, repeating a request never reverses it.
 */
public record MappingWireTarget(String slot, String endpoint, boolean mapped) {
    private static final Pattern SLOT = Pattern.compile("[0-9]{1,3}");
    private static final Pattern ENDPOINT = Pattern.compile("[0-9a-fA-F-]{36}:[0-9]{1,19}");

    public MappingWireTarget {
        if (!SLOT.matcher(slot).matches() || !ENDPOINT.matcher(endpoint).matches()) {
            throw new IllegalArgumentException("Invalid mapping wire");
        }
    }

    public int slotIndex() {
        return Integer.parseInt(slot);
    }

    public String encode() {
        return slot + "/" + endpoint + "/" + (mapped ? 1 : 0);
    }

    public static Optional<MappingWireTarget> parse(String value) {
        var fields = value.split("/", -1);
        if (fields.length != 3 || !(fields[2].equals("1") || fields[2].equals("0"))) return Optional.empty();
        try {
            return Optional.of(new MappingWireTarget(fields[0], fields[1], fields[2].equals("1")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
