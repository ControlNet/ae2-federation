package space.controlnet.ae2federation.client.menu;

import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class FabricPolicyActionSink {
    private static @Nullable Consumer<FabricPolicyActionRequest> sender;

    private FabricPolicyActionSink() {
    }

    public static void register(Consumer<FabricPolicyActionRequest> replacement) {
        sender = Objects.requireNonNull(replacement);
    }

    static boolean send(FabricPolicyActionRequest request) {
        var current = sender;
        if (current == null) {
            return false;
        }
        current.accept(request);
        return true;
    }
}
