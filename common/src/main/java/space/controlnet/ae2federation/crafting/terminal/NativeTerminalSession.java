package space.controlnet.ae2federation.crafting.terminal;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.binding.CraftingSubmissionSnapshot;

public final class NativeTerminalSession implements AutoCloseable {
    private final ServerLevel level;
    private final Thread serverThread;
    private final CraftingBindingService authority;
    private final CraftingSubmissionSnapshot snapshot;
    private final IActionSource actionSource;
    private final Set<AEKey> craftables;
    private boolean closed;

    NativeTerminalSession(ServerLevel level, CraftingBindingService authority, CraftingSubmissionSnapshot snapshot,
            IActionSource actionSource, Set<AEKey> craftables) {
        this.level = Objects.requireNonNull(level);
        serverThread = Thread.currentThread();
        this.authority = Objects.requireNonNull(authority);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.actionSource = Objects.requireNonNull(actionSource);
        this.craftables = Set.copyOf(craftables);
    }

    public Set<AEKey> craftables() {
        requireActive();
        return craftables;
    }

    public Optional<NativeTerminalRequest> begin(AEKey output, long amount) {
        requireActive();
        Objects.requireNonNull(output);
        if (amount <= 0) {
            throw new IllegalArgumentException("Native terminal amount must be positive");
        }
        if (!craftables.contains(output) || !authority.submissionAuthorityCurrent(snapshot)) {
            return Optional.empty();
        }
        return Optional.of(new NativeTerminalRequest(level, authority, snapshot, actionSource, output, amount));
    }

    public CraftingSubmissionSnapshot snapshot() {
        requireActive();
        return snapshot;
    }

    public boolean isClosed() {
        requireServerThread();
        return closed;
    }

    @Override
    public void close() {
        requireServerThread();
        closed = true;
    }

    private void requireServerThread() {
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("Native terminal session is server-thread owned");
        }
    }

    private void requireActive() {
        requireServerThread();
        requireOpen();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Native terminal session is closed");
        }
    }
}
