package space.controlnet.ae2federation.domain.port;

import space.controlnet.ae2federation.ae2.NativeAttachment;

public sealed interface RouterPortBinding {
    RouterPortKind kind();

    enum Disconnected implements RouterPortBinding {
        INSTANCE;

        @Override
        public RouterPortKind kind() {
            return RouterPortKind.DISCONNECTED;
        }
    }

    record Native(NativeAttachment attachment) implements RouterPortBinding {
        @Override
        public RouterPortKind kind() {
            return RouterPortKind.NATIVE_ME;
        }
    }

    record Federation(FederationPort port) implements RouterPortBinding {
        @Override
        public RouterPortKind kind() {
            return RouterPortKind.FEDERATION;
        }
    }
}
