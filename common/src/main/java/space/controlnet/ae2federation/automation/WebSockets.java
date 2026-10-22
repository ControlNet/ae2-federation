package space.controlnet.ae2federation.automation;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.Pending;
import space.controlnet.ae2federation.script.RunError;

/**
 * The {@code net} websocket functions: connections kept by installation and URL, so a run that ends leaves its
 * installation's connections open for the next run, and a removed installation's are closed. Messages arrive on a
 * small pool of daemon threads and wait in a queue that {@code net.receive} reads on the server thread.
 *
 * <p>Assistant choices (2026-10-22): text messages only, binary ones are skipped; at most {@link #MAX_OPEN}
 * connections per installation; an unread queue keeps the newest {@link #MAX_QUEUED} messages; a message over the
 * string cap closes the connection with status 1009, so {@code net.connected} shows it; what arrived before a close
 * can still be read. Connecting and sending wait at most the timeout (10 s unless configured). No host is refused:
 * that waits for the permission system (user, 2026-10-22).
 */
public final class WebSockets {
    public static final int MAX_OPEN = 4;
    public static final int MAX_QUEUED = 256;
    private static final int MAX_CHARACTERS = 100_000;
    private static final int THREADS = 2;
    private static final long PING_NANOS = 1_000_000_000L;
    private static final Map<Object, WebSockets> SERVERS = new HashMap<>();

    private final Supplier<Duration> timeout;
    private final ExecutorService pool;
    private final HttpClient client;
    /** By installation, then by URL. */
    private final Map<String, Map<String, Connection>> connections = new ConcurrentHashMap<>();

    public WebSockets(Supplier<Duration> timeout) {
        this.timeout = timeout;
        var threads = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(THREADS, task -> {
            var thread = new Thread(task, "ae2federation-websocket-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder().executor(pool).build();
    }

    /** The server's connections, made on first use; {@code server} is only a key. */
    public static synchronized WebSockets of(Object server) {
        return SERVERS.computeIfAbsent(server, key -> new WebSockets(AutomationRule::timeout));
    }

    /** Runs {@code use} on the server's connections if it has any, without making them. */
    public static synchronized void ifPresent(Object server, java.util.function.Consumer<WebSockets> use) {
        var sockets = SERVERS.get(server);
        if (sockets != null) {
            use.accept(sockets);
        }
    }

    /** Closes the server's connections and stops their threads. */
    public static synchronized void closeServer(Object server) {
        var sockets = SERVERS.remove(server);
        if (sockets != null) {
            sockets.close();
        }
    }

    /** Opens a connection, or keeps the open one; True once open. A bad URL stops the run at once. */
    public Pending connect(String owner, String url) {
        var uri = uri(url);
        var own = connections.computeIfAbsent(owner, key -> new ConcurrentHashMap<>());
        var existing = own.get(url);
        if (existing != null && !existing.closed) {
            return existing.opened(url);
        }
        long open = own.values().stream().filter(connection -> !connection.closed).count();
        if (open >= MAX_OPEN) {
            throw new RunError("OSError", "an installation keeps at most " + MAX_OPEN + " connections open");
        }
        var wait = timeout.get();
        var connection = new Connection();
        connection.building = client.newWebSocketBuilder().connectTimeout(wait).buildAsync(uri, connection)
                .orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS);
        // Done once the connection counts as open, so net.connected agrees with net.connect's answer.
        connection.opening = connection.building.whenComplete((socket, failure) -> {
            if (failure != null) {
                connection.closed = true;
                own.remove(url, connection);
            } else {
                // Ready to send before it counts as open.
                connection.sending = CompletableFuture.completedFuture(socket);
                connection.socket = socket;
            }
        });
        own.put(url, connection);
        return connection.opened(url);
    }

    /** Sends the text; True once it has gone. Not being connected stops the run. */
    public Pending send(String owner, String url, String text) {
        var connection = open(owner, url);
        if (connection == null) {
            throw new RunError("OSError", "not connected to " + Ops.repr(url));
        }
        var wait = timeout.get();
        // One message at a time on a connection: a send starts when the one before it has gone.
        CompletableFuture<WebSocket> sent;
        synchronized (connection) {
            sent = connection.sending.thenCompose(socket -> socket.sendText(text, true))
                    .orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS);
            connection.sending = sent.exceptionally(failure -> connection.socket);
        }
        return new Waiting(sent, sent, () -> new RunError("OSError", "could not send to " + Ops.repr(url)));
    }

    /** The oldest message not read yet, or {@code null}. */
    public @Nullable String receive(String owner, String url) {
        var connection = connections.getOrDefault(owner, Map.of()).get(url);
        return connection == null ? null : connection.queue.pollFirst();
    }


    public boolean connected(String owner, String url) {
        return open(owner, url) != null;
    }

    /** Closes the connection; False when there was none. Unread messages go with it. */
    public boolean disconnect(String owner, String url) {
        var connection = connections.getOrDefault(owner, Map.of()).remove(url);
        if (connection == null) {
            return false;
        }
        connection.close();
        return true;
    }

    /** How many messages arrived on the connection in all: a new message makes it grow. */
    public long received(String owner, String url) {
        var connection = connections.getOrDefault(owner, Map.of()).get(url);
        return connection == null ? 0 : connection.received.get();
    }

    /** Closes an installation's connections: it was removed. */
    public void closeOwner(String owner) {
        var own = connections.remove(owner);
        if (own != null) {
            own.values().forEach(Connection::close);
        }
    }

    /** Closes every connection, keeping the threads for new ones. */
    public void disconnectAll() {
        connections.values().forEach(own -> own.values().forEach(Connection::close));
        connections.clear();
    }

    public void close() {
        disconnectAll();
        client.shutdownNow();
        pool.shutdownNow();
    }

    private @Nullable Connection open(String owner, String url) {
        var connection = connections.getOrDefault(owner, Map.of()).get(url);
        if (connection == null || connection.socket == null || connection.closed) {
            return null;
        }
        connection.check();
        return connection;
    }

    /** Refuses a URL that is not ws or wss with {@code ValueError}, as {@link #connect} does. */
    public static void check(String url) {
        uri(url);
    }

    private static URI uri(String url) {
        try {
            var uri = new URI(url);
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("ws") || scheme.equals("wss")) && uri.getHost() != null) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
            // Reported below, as any other URL that is not ws or wss.
        }
        throw new RunError("ValueError", "url must start with ws:// or wss://: " + Ops.repr(url));
    }

    /** A step that ends in True, or in the error {@code failed} gives; cancelling stops {@code work}. */
    private record Waiting(CompletableFuture<?> future, CompletableFuture<?> work, Supplier<RunError> failed)
            implements Pending {
        @Override
        public boolean done() {
            return future.isDone();
        }

        @Override
        public Object value() {
            try {
                future.join();
                return true;
            } catch (CompletionException | java.util.concurrent.CancellationException failure) {
                throw failed.get();
            }
        }

        @Override
        public void cancel() {
            work.cancel(true);
            future.cancel(true);
        }
    }

    /** One connection and what it received; the listener runs on the pool, the rest on the server thread. */
    private static final class Connection implements WebSocket.Listener {
        final ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
        final AtomicLong received = new AtomicLong();
        /** The part of a message that arrived so far; listener threads only, one at a time. */
        final StringBuilder partial = new StringBuilder();
        /** The JDK's handshake, which cancelling stops. */
        CompletableFuture<WebSocket> building;
        CompletableFuture<WebSocket> opening;
        CompletableFuture<WebSocket> sending;
        volatile WebSocket socket;
        volatile boolean closed;
        volatile long lastPing = System.nanoTime();

        Pending opened(String url) {
            return new Waiting(opening, building,
                    () -> new RunError("OSError", "could not connect to " + Ops.repr(url)));
        }

        /**
         * Pings at most once a second while the connection is looked at. The JDK does not always report a server that
         * dropped the connection without a close frame; a ping to it fails, which marks the connection closed.
         */
        void check() {
            long now = System.nanoTime();
            if (now - lastPing < PING_NANOS) {
                return;
            }
            lastPing = now;
            var open = socket;
            open.sendPing(ByteBuffer.allocate(0)).whenComplete((done, failure) -> {
                if (failure != null) {
                    closed = true;
                    open.abort();
                }
            });
        }

        void close() {
            closed = true;
            building.cancel(true);
            var open = socket;
            if (open != null) {
                open.sendClose(WebSocket.NORMAL_CLOSURE, "").whenComplete((done, failure) -> open.abort());
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            // Every message goes to the queue, which drops the oldest itself: no need to hold the server back.
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (partial.codePointCount(0, partial.length()) > MAX_CHARACTERS) {
                partial.setLength(0);
                closed = true;
                webSocket.sendClose(1009, "message too big").whenComplete((done, failure) -> webSocket.abort());
                return null;
            }
            if (last) {
                queue.addLast(partial.toString());
                partial.setLength(0);
                while (queue.size() > MAX_QUEUED) {
                    queue.pollFirst();
                }
                received.incrementAndGet();
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
        }
    }
}
