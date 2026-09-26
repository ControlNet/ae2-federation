package space.controlnet.ae2federation.storage.dependency;

import java.util.Objects;

public record EffectiveSourceRelationship(EffectiveSourceRelationshipKey key, EffectiveStorageAuthority authority,
        EffectiveStorageAuthority reexportAuthority, CandidateRelationshipRevision revision, int minimumDepth) {
    public EffectiveSourceRelationship {
        Objects.requireNonNull(key);
        Objects.requireNonNull(authority);
        Objects.requireNonNull(reexportAuthority);
        Objects.requireNonNull(revision);
        if (authority.isEmpty() || minimumDepth < 1) {
            throw new IllegalArgumentException("Effective relationship requires authority and a positive depth");
        }
    }
}
