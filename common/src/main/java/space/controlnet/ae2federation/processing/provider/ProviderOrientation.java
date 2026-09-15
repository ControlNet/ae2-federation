package space.controlnet.ae2federation.processing.provider;

import java.util.EnumSet;
import java.util.Objects;

public record ProviderOrientation(ProviderFace federationFace) {
    public ProviderOrientation {
        Objects.requireNonNull(federationFace);
    }

    public EnumSet<ProviderFace> nativeFaces() {
        return EnumSet.complementOf(EnumSet.of(federationFace));
    }
}
