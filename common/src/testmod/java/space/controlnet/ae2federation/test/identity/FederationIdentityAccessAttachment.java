package space.controlnet.ae2federation.test.identity;

import appeng.api.networking.IGridNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class FederationIdentityAccessAttachment implements AutoCloseable {
    private static final IdentityHashMap<IGridNode, Set<UUID>> ACTIVE = new IdentityHashMap<>();

    private final IGridNode node;
    private final UUID attachmentId;
    private final NetworkId networkId;
    private boolean active = true;

    private FederationIdentityAccessAttachment(IGridNode node) {
        this.node = node;
        attachmentId = UUID.randomUUID();
        networkId = node.getGrid().getService(NetworkIdentityService.class).lineage(node).networkId();
        ACTIVE.computeIfAbsent(node, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(attachmentId);
    }

    public static FederationIdentityAccessAttachment attach(IGridNode node) {
        return new FederationIdentityAccessAttachment(node);
    }

    public static int activeCount(IGridNode node) {
        var attachments = ACTIVE.get(node);
        return attachments == null ? 0 : attachments.size();
    }

    public UUID attachmentId() {
        return attachmentId;
    }

    public NetworkId networkId() {
        return networkId;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        var attachments = ACTIVE.get(node);
        if (attachments == null) {
            return;
        }
        attachments.remove(attachmentId);
        if (attachments.isEmpty()) {
            ACTIVE.remove(node);
        }
    }
}
