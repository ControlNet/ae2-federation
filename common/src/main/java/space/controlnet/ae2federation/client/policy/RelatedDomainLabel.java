package space.controlnet.ae2federation.client.policy;

import java.util.Locale;

/**
 * A readable name for another Federation Domain: how it is formed (one Bridge, or Routers joined by federation
 * cable) and a short tag that tells domains of the same kind apart. The raw identity is internal and not shown.
 */
public record RelatedDomainLabel(String kind, String tag) {
    public static RelatedDomainLabel of(String domainId) {
        var kind = domainId.startsWith("direct:") ? "bridge" : domainId.startsWith("physical:") ? "router" : "other";
        // Four hex digits of a stable digest; String.hashCode is specified, so the tag is the same on every JVM.
        int digest = domainId.hashCode();
        var tag = String.format(Locale.ROOT, "%04X", (digest ^ digest >>> 16) & 0xffff);
        return new RelatedDomainLabel(kind, tag);
    }
}
