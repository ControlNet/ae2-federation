package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;

/**
 * The sequenced request authority a Federation menu shares with its client: the server's menu nonce and sequence, the
 * client's copy of them from the last sync, and the one request in flight. Both the domain workspace and the
 * Provider screen use it, so their requests are checked and acknowledged the same way.
 */
final class FederationMenuAuthority {
    private final @Nullable FederationDomainPolicySession session;
    private final @Nullable UUID menuNonce;
    private long menuSequence;
    private @Nullable ClientAuthority clientAuthority;
    private final ActionRequestProgress requestProgress = new ActionRequestProgress();

    FederationMenuAuthority(@Nullable FederationDomainPolicySession session) {
        this.session = session;
        menuNonce = session == null ? null : UUID.randomUUID();
    }

    /**
     * The first reason this request does not come from the current client of {@code holder}'s menu, or null when it
     * does. The action's own authorization is the caller's.
     */
    @Nullable FederationDomainPolicyActionResult reject(Object holder, ModularUIContainerMenu menu,
            FederationDomainPolicyActionRequest request) {
        if (menu.uiHolder != holder || session == null || menuNonce == null) {
            return FederationDomainPolicyActionResult.WRONG_MENU;
        }
        if (request.containerId() != menu.containerId) {
            return FederationDomainPolicyActionResult.STALE_CONTAINER;
        }
        if (!menuNonce.equals(request.menuNonce())) {
            return FederationDomainPolicyActionResult.STALE_SESSION;
        }
        if (request.menuSequence() != menuSequence) {
            return FederationDomainPolicyActionResult.STALE_SEQUENCE;
        }
        var context = session.context().orElse(null);
        if (context == null || !context.equals(request.context())) {
            return FederationDomainPolicyActionResult.STALE_CONTEXT;
        }
        return null;
    }

    /** An accepted action moves the menu on, so a request built before it is stale. */
    void advance() {
        menuSequence = Math.incrementExact(menuSequence);
    }

    long sequence() {
        return menuSequence;
    }

    Optional<FederationDomainPolicyActionRequest> currentRequest(Object holder, ModularUIContainerMenu menu,
            FederationDomainPolicyAction action) {
        if (session == null || menuNonce == null || menu.uiHolder != holder) {
            return Optional.empty();
        }
        return session.context().map(context -> new FederationDomainPolicyActionRequest(action, menu.containerId, menuNonce,
                menuSequence, context, session.expectedRevision()));
    }

    /** What the server syncs to the client of {@code holder}'s menu; empty while it has nothing to authorize. */
    String encode(Object holder, Player player) {
        if (session == null || menuNonce == null || !(player.containerMenu instanceof ModularUIContainerMenu menu)
                || menu.uiHolder != holder) {
            return "";
        }
        var context = session.context().orElse(null);
        if (context == null) {
            return "";
        }
        var encodedFederationDomain = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(context.federationDomainId().value().getBytes(StandardCharsets.UTF_8));
        return menu.containerId + ":" + menuNonce + ":" + menuSequence + ":" + context.generation() + ":"
                + session.expectedRevision().value() + ":" + encodedFederationDomain;
    }

    /** Takes the server's latest authority on the client; an empty one withdraws it. */
    void accept(String encoded) {
        if (encoded.isEmpty()) {
            clientAuthority = null;
            return;
        }
        var fields = encoded.split(":", 6);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Malformed Federation Domain policy authority");
        }
        var federationDomainId = new String(Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
        clientAuthority = new ClientAuthority(Integer.parseInt(fields[0]), UUID.fromString(fields[1]),
                Long.parseLong(fields[2]),
                new space.controlnet.ae2federation.domain.FederationDomainReference(
                        new space.controlnet.ae2federation.domain.FederationDomainId(federationDomainId), Long.parseLong(fields[3])),
                new space.controlnet.ae2federation.policy.PolicyRevision(Long.parseLong(fields[4])));
        requestProgress.observe(clientAuthority.menuSequence());
    }

    boolean authorized() {
        return clientAuthority != null;
    }

    /** The client's view of the server sequence; -1 while it has no authority. */
    long clientSequence() {
        return clientAuthority == null ? -1 : clientAuthority.menuSequence();
    }

    /** Sends one request unless another is in flight; false when nothing was sent. */
    boolean send(FederationDomainPolicyAction action, String target) {
        var authority = clientAuthority;
        if (authority == null || requestProgress.pending()) return false;
        var requestId = UUID.randomUUID();
        requestProgress.begin(requestId, authority.menuSequence());
        if (!FederationDomainPolicyActionSink.send(new FederationDomainPolicyActionRequest(action, authority.containerId(),
                authority.menuNonce(), authority.menuSequence(), authority.context(), authority.expectedRevision(), target), requestId)) {
            requestProgress.reply(requestId, authority.menuSequence(), FederationDomainPolicyActionResult.WRONG_MENU);
        }
        return true;
    }

    /** A reply for this menu's own nonce; replies for an earlier menu are ignored. */
    boolean acceptReply(UUID nonce, UUID requestId, long sequence, FederationDomainPolicyActionResult result) {
        if (clientAuthority == null || !clientAuthority.menuNonce().equals(nonce)) return false;
        requestProgress.reply(requestId, sequence, result);
        return true;
    }

    boolean pending() {
        return requestProgress.pending();
    }

    @Nullable FederationDomainPolicyActionResult rejection() {
        return requestProgress.rejection();
    }

    private record ClientAuthority(int containerId, UUID menuNonce, long menuSequence,
            space.controlnet.ae2federation.domain.FederationDomainReference context,
            space.controlnet.ae2federation.policy.PolicyRevision expectedRevision) {
    }
}
