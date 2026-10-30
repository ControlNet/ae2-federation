package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of the Automation tab's traffic: the last index the server sent, the program texts it sent, put
 * back together from chunks, and the answers to this client's requests. The tab reads it; nothing here waits for the
 * server. The loader's network code registers how requests travel and feeds in what arrives.
 */
public final class ClientPrograms {
    public interface ToServer {
        void open(boolean open);

        void fetch(String name);

        void upload(int request, String name, String base, int index, int count, String part);

        void request(int request, String action, List<String> args);
    }

    /** A program's text as the server sent it, with its version. */
    public record Received(String version, String text) {
    }

    private static @Nullable ToServer toServer;
    private static final ClientPrograms INSTANCE = new ClientPrograms();

    private @Nullable ProgramSync.Index index;
    private final Map<String, Received> texts = new HashMap<>();
    private final Map<String, String[]> partial = new HashMap<>();
    private final Map<Integer, Consumer<ProgramSync.Answer>> waiting = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int nextRequest = 1;

    public static void register(ToServer server) {
        toServer = Objects.requireNonNull(server);
    }

    public static ClientPrograms get() {
        return INSTANCE;
    }

    /** The last index the server sent; empty until the tab has opened once. */
    public Optional<ProgramSync.Index> index() {
        return Optional.ofNullable(index);
    }

    public Optional<Received> text(String name) {
        return Optional.ofNullable(texts.get(name));
    }

    /** Called whenever an index or a whole text arrives. */
    public void listen(Runnable listener) {
        listeners.add(listener);
    }

    public void open(boolean open) {
        if (toServer != null) {
            toServer.open(open);
        }
        if (!open) {
            listeners.clear();
            waiting.clear();
        }
    }

    public void fetch(String name) {
        if (toServer != null) {
            toServer.fetch(name);
        }
    }

    /** Uploads a program's text in chunks; {@code base} is the version the edit started from. */
    public void upload(String name, String base, String text, Consumer<ProgramSync.Answer> answer) {
        var request = nextRequest++;
        List<String> chunks;
        try {
            chunks = ChunkAssembler.split(text);
        } catch (IllegalArgumentException tooLong) {
            // Refused here as the server would, so the caller hears back as for any refusal.
            answer.accept(new ProgramSync.Answer(request, false, tooLong.getMessage()));
            return;
        }
        waiting.put(request, answer);
        if (toServer != null) {
            for (int i = 0; i < chunks.size(); i++) {
                toServer.upload(request, name, base, i, chunks.size(), chunks.get(i));
            }
        }
    }

    /** Sends a request (see {@link ProgramSync#perform}); the answer comes to {@code answer}. */
    public void request(String action, List<String> args, Consumer<ProgramSync.Answer> answer) {
        var request = nextRequest++;
        waiting.put(request, answer);
        if (toServer != null) {
            toServer.request(request, action, args);
        }
    }

    /** The connection ended: what the last server sent and every answer still awaited are forgotten. */
    public void reset() {
        index = null;
        texts.clear();
        partial.clear();
        waiting.clear();
    }

    public void receive(ProgramSync.Index index) {
        this.index = index;
        listeners.forEach(Runnable::run);
    }

    public void receive(ProgramSync.Text text) {
        if (text.count() < 1 || text.index() < 0 || text.index() >= text.count()) {
            return;
        }
        var key = text.name() + "@" + text.version();
        var parts = partial.get(key);
        if (parts == null || parts.length != text.count()) {
            parts = new String[text.count()];
            partial.put(key, parts);
        }
        parts[text.index()] = text.part();
        for (var part : parts) {
            if (part == null) {
                return;
            }
        }
        partial.remove(key);
        texts.put(text.name(), new Received(text.version(), String.join("", parts)));
        listeners.forEach(Runnable::run);
    }

    public void receive(ProgramSync.Answer answer) {
        var callback = waiting.remove(answer.request());
        if (callback != null) {
            callback.accept(answer);
        }
    }
}
