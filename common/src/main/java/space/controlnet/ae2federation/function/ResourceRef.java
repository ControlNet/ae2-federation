package space.controlnet.ae2federation.function;

import java.util.Objects;

/**
 * An AE2 resource named in a program: a key type ({@code item}, {@code fluid} or an addon's type ID) and the
 * resource's ID. Plain strings here; the game side turns them into AE2 keys and policy resources.
 */
public record ResourceRef(String type, String id) {
    public static final String ITEM = "item";

    public ResourceRef {
        Objects.requireNonNull(type);
        Objects.requireNonNull(id);
        if (!id.matches("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+")) throw new IllegalArgumentException("Not a resource ID: " + id);
        if (!type.matches("[a-z0-9_.\\-]+(:[a-z0-9_.\\-/]+)?")) throw new IllegalArgumentException("Not a key type: " + type);
    }
}
