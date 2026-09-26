package space.controlnet.ae2federation.client.policy;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Presentation of authoritative result codes; unknown or malformed codes never imply success. */
public record MappingFeedback(String key, List<String> arguments, String tone) {
    private static final Pattern ACCEPTED = Pattern.compile("accepted-([0-9]+)-([0-9]+)");
    private static final Pattern RELEASED = Pattern.compile("released-[0-9]+");
    private static final Pattern CLEARED = Pattern.compile("cleared-stale-[0-9]+");
    private static final Set<String> REJECTIONS = Set.of("session", "no-provider", "no-endpoint", "invalid-selection",
            "policy_denied", "claim_mismatch", "stale-slot", "provider-offline", "not-retained", "still-mapped",
            "pending-send", "pending-return", "endpoint-unloaded", "claim-changed", "owner_conflict", "stale_epoch",
            "wrong_endpoint");

    public static MappingFeedback fromCode(String code) {
        if ("ready".equals(code)) return simple("ready", "neutral");
        if ("pending".equals(code)) return simple("pending", "waiting");
        if ("confirm-release".equals(code)) return simple("confirm", "waiting");
        var accepted = ACCEPTED.matcher(code);
        if (accepted.matches()) return new MappingFeedback("accepted", List.of(accepted.group(1)), "success");
        if (RELEASED.matcher(code).matches()) return simple("released", "success");
        if (CLEARED.matcher(code).matches()) return simple("cleared", "success");
        if (code.startsWith("rejected-") && REJECTIONS.contains(code.substring(9))) {
            return simple(code, "error");
        }
        return simple("unknown", "error");
    }

    private static MappingFeedback simple(String key, String tone) {
        return new MappingFeedback(key, List.of(), tone);
    }

    public String translationKey() {
        return "ae2federation.ui.mapping_feedback." + key;
    }
}
