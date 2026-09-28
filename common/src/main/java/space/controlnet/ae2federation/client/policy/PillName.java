package space.controlnet.ae2federation.client.policy;

import java.util.Locale;

/**
 * The short network names on a link's pill ("Main▸Mine"): the first word of the player's name for the network, or
 * its identity tag when it has none. Two names that would read the same fall back to the tags, so a pill never
 * shows an ambiguous direction.
 */
public final class PillName {
    private static final int MAX_LENGTH = 8;

    private PillName() {
    }

    public record Pair(String consumer, String provider) {
    }

    public static String of(String name, String id) {
        var trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) return tag(id);
        var word = trimmed.split("\\s+", 2)[0];
        return word.length() <= MAX_LENGTH ? word : word.substring(0, MAX_LENGTH);
    }

    public static Pair pair(String consumerName, String consumerId, String providerName, String providerId) {
        var consumer = of(consumerName, consumerId);
        var provider = of(providerName, providerId);
        return consumer.equals(provider) ? new Pair(tag(consumerId), tag(providerId)) : new Pair(consumer, provider);
    }

    private static String tag(String id) {
        return id.substring(0, Math.min(4, id.length())).toUpperCase(Locale.ROOT);
    }
}
