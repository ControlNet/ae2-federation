package space.controlnet.ae2federation.fabric.port;

import space.controlnet.ae2federation.ae2.NativeAttachment;

public sealed interface HubPortBinding {
    HubPortKind kind();

    enum Disconnected implements HubPortBinding {
        INSTANCE;

        @Override
        public HubPortKind kind() {
            return HubPortKind.DISCONNECTED;
        }
    }

    record Native(NativeAttachment attachment) implements HubPortBinding {
        @Override
        public HubPortKind kind() {
            return HubPortKind.NATIVE_ME;
        }
    }

    record Federation(FederationPort port) implements HubPortBinding {
        @Override
        public HubPortKind kind() {
            return HubPortKind.FEDERATION;
        }
    }
}
