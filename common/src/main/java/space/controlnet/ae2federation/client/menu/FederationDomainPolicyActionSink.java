package space.controlnet.ae2federation.client.menu;

import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class FederationDomainPolicyActionSink {
    private static @Nullable Consumer<FederationDomainPolicyActionRequest> sender;

    private FederationDomainPolicyActionSink() {
    }

    public static void register(Consumer<FederationDomainPolicyActionRequest> replacement) {
        sender = Objects.requireNonNull(replacement);
    }

    static boolean send(FederationDomainPolicyActionRequest request) {
        var current = sender;
        if (current == null) {
            return false;
        }
        current.accept(request);
        return true;
    }
}
