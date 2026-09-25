package space.controlnet.ae2federation.observability.id;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.observability.ObservationLimits;

final class StableObservationId {
    private StableObservationId() {
    }

    static String create(FederationDomainId federationDomainId, String kind, String nativeKey) {
        ObservationLimits.boundedString(federationDomainId.value(), "Federation Domain ID");
        ObservationLimits.boundedString(kind, "Observation ID kind");
        ObservationLimits.boundedString(nativeKey, "Observation native key");
        try {
            var input = federationDomainId.value() + '\u0000' + kind + '\u0000' + nativeKey;
            var digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return kind + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static void validate(FederationDomainId federationDomainId, String kind, String value) {
        ObservationLimits.boundedString(federationDomainId.value(), "Federation Domain ID");
        ObservationLimits.boundedString(value, kind + " ID");
        if (!value.startsWith(kind + ":")) {
            throw new IllegalArgumentException("Observation ID has the wrong type prefix");
        }
    }
}
