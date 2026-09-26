package space.controlnet.ae2federation.client.menu;

import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class FederationDomainPolicyActionSink {
    private static @Nullable java.util.function.BiConsumer<FederationDomainPolicyActionRequest, java.util.UUID> sender;
    private static @Nullable java.util.function.IntConsumer returnSender;

    private FederationDomainPolicyActionSink() {
    }

    public static void register(java.util.function.BiConsumer<FederationDomainPolicyActionRequest, java.util.UUID> replacement) {
        sender = Objects.requireNonNull(replacement);
    }

    public static void registerReturn(java.util.function.IntConsumer replacement) {
        returnSender = Objects.requireNonNull(replacement);
    }

    static void returnToProvider(int containerId) {
        if (returnSender != null) returnSender.accept(containerId);
    }

    static boolean send(FederationDomainPolicyActionRequest request, java.util.UUID requestId) {
        var current = sender;
        if (current == null) {
            return false;
        }
        current.accept(request, requestId);
        return true;
    }
}
