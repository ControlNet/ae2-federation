package space.controlnet.ae2federation.client.policy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.persistence.NetworkNameBook;

/** A rename request: the network and its new name; an empty name clears it. */
public record NetworkRenameTarget(NetworkId network, String name) {
    public NetworkRenameTarget {
        Objects.requireNonNull(network);
        name = NetworkNameBook.sanitize(name).orElseThrow(() -> new IllegalArgumentException("Invalid network name"));
    }

    public String encode() {
        return network.value() + "/" + name;
    }

    public static Optional<NetworkRenameTarget> parse(String value) {
        var separator = value.indexOf('/');
        if (separator < 0) return Optional.empty();
        try {
            return Optional.of(new NetworkRenameTarget(new NetworkId(UUID.fromString(value.substring(0, separator))),
                    value.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
