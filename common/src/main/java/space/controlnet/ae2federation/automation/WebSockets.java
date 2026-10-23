package space.controlnet.ae2federation.automation;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Comparator;
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
 * <p>What arrived at a URL waits in a {@link Channel}, which outlives the connection to it: a connection made again
 * goes on with the same unread messages and count, so a message that came just before a close is neither lost nor
 * counted twice. A closed channel keeps its messages for {@code net.receive} until it is one of more than
 * {@link #MAX_CLOSED} closed ones; the one closed longest ago goes first.
 *
 * <p>Assistant choices (2026-10-22, limits 2026-10-23): text messages only, binary ones are skipped; at most
 * {@link #MAX_OPEN} connections per installation; an unread queue keeps the newest {@link #MAX_QUEUED} messages and at
 * most {@link #MAX_QUEUED_CHARACTERS} characters; a message over the string cap closes the connection with status
 * 1009, so {@code net.connected} shows it. Connecting and sending wait at most the timeout (10 s unless configured);
 * a send still going when its wait ends, or when its run stops, closes the connection, since the JDK cannot take it
 * back and no later send may start behind it. No host is refused: that waits for the permission system (user,
 * 2026-10-22).
 */
public final class WebSockets {
    public static final int MAX_OPEN = 4;
    public static final int MAX_CLOSED = 4;
    public static final int MAX_QUEUED = 256;
    public static final int MAX_QUEUED_CHARACTERS = 1_000_000;
    private static final int MAX_CHARACTERS = 100_000;
    private static final int THREADS = 2;
    private static final long PING_NANOS = 1_000_000_000L;
    private static final Map<Object, WebSockets> SERVERS = new HashMap<>();

    private final Supplier<Duration> timeout;
    private final ExecutorService pool;
    private final HttpClient client;
    /** By installation, then by URL. */
    private final Map<String, Map<String, Channel>> channels = new ConcurrentHashMap<>();
    /** Tests only: runs when a handshake has ended, before its connection counts as open. */
    volatile Runnable beforePublish = () -> { };

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
        var own = channels.computeIfAbsent(owner, key -> new ConcurrentHashMap<>());
        Connection connection;
        synchronized (own) {
            var channel = own.get(url);
            var existing = channel == null ? null : channel.connection;
            if (existing != null && !existing.closed) {
                return existing.opened(url);
            }
            long open = own.values().stream().filter(Channel::linked).count();
            if (open >= MAX_OPEN) {
                throw new RunError("OSError", "an installation keeps at most " + MAX_OPEN + " connections open");
            }
            if (channel == null) {
                channel = new Channel(own);
                own.put(url, channel);
            }
            connection = new Connection(channel);
            channel.attach(connection);
        }
        var wait = timeout.get();
        connection.building = client.newWebSocketBuilder().connectTimeout(wait).buildAsync(uri, connection)
                .orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS);
        connection.building.whenComplete((socket, failure) -> {
            if (failure == null) {
                beforePublish.run();
            }
            connection.publish(socket, failure);
        });
        return connection.opened(url);
    }

    /** Sends the text; True once it has gone. Not being connected stops the run. */
    public Pending send(String owner, String url, String text) {
        if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) {
            throw new RunError("ValueError", "a message holds at most " + MAX_CHARACTERS + " characters");
        }
        var connection = open(owner, url);
        if (connection == null) {
            throw new RunError("OSError", "not connected to " + Ops.repr(url));
        }
        var wait = timeout.get();
        CompletableFuture<WebSocket> sending;
        synchronized (connection) {
            // One message at a time on a connection: a send starts when the one before it has finished, either way.
            sending = connection.free.thenCompose(ignored -> connection.closed
                    ? CompletableFuture.failedFuture(new IOException("the connection closed"))
                    : connection.socket.sendText(text, true));
            connection.free = sending.handle((done, failure) -> null);
        }
        // The wait is a copy, so its timeout or cancelling leaves the send itself as it is.
        var answer = sending.copy().orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS);
        Runnable giveUp = () -> {
            if (!sending.isDone()) {
                connection.abort();
            }
        };
        answer.whenComplete((done, failure) -> {
            if (failure != null) {
                giveUp.run();
            }
        });
        return new Waiting(answer, giveUp, () -> new RunError("OSError", "could not send to " + Ops.repr(url)));
    }

    /** The oldest message not read yet, or {@code null}. */
    public @Nullable String receive(String owner, String url) {
        var channel = channel(owner, url);
        return channel == null ? null : channel.poll();
    }

    public boolean connected(String owner, String url) {
        return open(owner, url) != null;
    }

    /** Closes the connection; False when there was none. Unread messages go with it. */
    public boolean disconnect(String owner, String url) {
        var own = channels.get(owner);
        var channel = own == null ? null : own.remove(url);
        if (channel == null) {
            return false;
        }
        channel.close();
        return true;
    }

    /** How many messages arrived at the URL in all, across its connections: a new message makes it grow. */
    public long received(String owner, String url) {
        var channel = channel(owner, url);
        return channel == null ? 0 : channel.received.get();
    }

    /** Closes an installation's connections: it was removed. */
    public void closeOwner(String owner) {
        var own = channels.remove(owner);
        if (own != null) {
            own.values().forEach(Channel::close);
        }
    }

    /** Closes every connection, keeping the threads for new ones. */
    public void disconnectAll() {
        channels.values().forEach(own -> own.values().forEach(Channel::close));
        channels.clear();
    }

    public void close() {
        disconnectAll();
        client.shutdownNow();
        pool.shutdownNow();
    }

    /** How many URLs the installation keeps, open or closed. */
    int kept(String owner) {
        var own = channels.get(owner);
        return own == null ? 0 : own.size();
    }

    private @Nullable Channel channel(String owner, String url) {
        var own = channels.get(owner);
        return own == null ? null : own.get(url);
    }

    private @Nullable Connection open(String owner, String url) {
        var channel = channel(owner, url);
        var connection = channel == null ? null : channel.connection;
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

    /** A step that ends in True, or in the error {@code failed} gives; cancelling runs {@code stop}. */
    private record Waiting(CompletableFuture<?> future, Runnable stop, Supplier<RunError> failed) implements Pending {
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
            stop.run();
            future.cancel(true);
        }
    }

    /**
     * What arrived at one URL for one installation, and the connection to it while there is one. The queue is filled
     * on the pool and read on the server thread.
     */
    private static final class Channel {
        final Map<String, Channel> own;
        final ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
        final AtomicLong characters = new AtomicLong();
        final AtomicLong received = new AtomicLong();
        volatile @Nullable Connection connection;
        volatile long endedAt;

        Channel(Map<String, Channel> own) {
            this.own = own;
        }

        boolean linked() {
            var open = connection;
            return open != null && !open.closed;
        }

        synchronized void attach(Connection next) {
            connection = next;
        }

        /** The connection closed: the channel lets go of it and keeps the messages. */
        void ended(Connection which) {
            synchronized (this) {
                if (connection != which) {
                    return;
                }
                connection = null;
                endedAt = System.nanoTime();
            }
            forget(own);
        }

        void close() {
            var open = connection;
            if (open != null) {
                open.close();
            }
        }

        void add(String message) {
            queue.addLast(message);
            characters.addAndGet(message.length());
            while (queue.size() > MAX_QUEUED || characters.get() > MAX_QUEUED_CHARACTERS) {
                var dropped = queue.pollFirst();
                if (dropped == null) {
                    break;
                }
                characters.addAndGet(-dropped.length());
            }
            received.incrementAndGet();
        }

        @Nullable String poll() {
            var message = queue.pollFirst();
            if (message != null) {
                characters.addAndGet(-message.length());
            }
            return message;
        }
    }

    /** Lets go of an installation's closed channels past {@link #MAX_CLOSED}, the one closed longest ago first. */
    private static void forget(Map<String, Channel> own) {
        synchronized (own) {
            var closed = own.entrySet().stream().filter(entry -> entry.getValue().connection == null)
                    .sorted(Comparator.comparingLong(entry -> entry.getValue().endedAt)).toList();
            for (int i = 0; i < closed.size() - MAX_CLOSED; i++) {
                own.remove(closed.get(i).getKey(), closed.get(i).getValue());
            }
        }
    }

    /** One connection; the listener runs on the pool, the rest on the server thread. */
    private static final class Connection implements WebSocket.Listener {
        final Channel channel;
        /** The part of a message that arrived so far; listener threads only, one at a time. */
        final StringBuilder partial = new StringBuilder();
        /** Done once the connection counts as open, so net.connected agrees with net.connect's answer. */
        final CompletableFuture<Boolean> opening = new CompletableFuture<>();
        /** The JDK's handshake, which cancelling stops. */
        CompletableFuture<WebSocket> building;
        /** Done when the last send given to the JDK has finished; guarded by this. */
        CompletableFuture<?> free;
        volatile WebSocket socket;
        volatile boolean closed;
        volatile long lastPing = System.nanoTime();

        Connection(Channel channel) {
            this.channel = channel;
        }

        Pending opened(String url) {
            return new Waiting(opening, () -> building.cancel(true),
                    () -> new RunError("OSError", "could not connect to " + Ops.repr(url)));
        }

        /**
         * The handshake ended. Publishing and closing hold the same lock: a connection closed meanwhile aborts the
         * socket it was given instead of counting as open.
         */
        void publish(@Nullable WebSocket made, @Nullable Throwable failure) {
            synchronized (this) {
                if (failure == null && !closed) {
                    // Ready to send before it counts as open.
                    free = CompletableFuture.completedFuture(null);
                    socket = made;
                    opening.complete(true);
                    return;
                }
            }
            if (made != null) {
                made.abort();
            }
            end();
            opening.completeExceptionally(failure != null ? failure : new IOException("the connection closed"));
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
            socket.sendPing(ByteBuffer.allocate(0)).whenComplete((done, failure) -> {
                if (failure != null) {
                    abort();
                }
            });
        }

        void close() {
            WebSocket open;
            synchronized (this) {
                closed = true;
                open = socket;
            }
            end();
            building.cancel(true);
            if (open != null) {
                open.sendClose(WebSocket.NORMAL_CLOSURE, "").whenComplete((done, failure) -> open.abort());
            }
        }

        /** Closes at once, failing a send that is still going and every send queued behind it. */
        void abort() {
            WebSocket open;
            synchronized (this) {
                closed = true;
                open = socket;
            }
            end();
            building.cancel(true);
            if (open != null) {
                open.abort();
            }
        }

        private void end() {
            closed = true;
            channel.ended(this);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            // Every message goes to the queue, which drops the oldest itself: no need to hold the server back.
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (closed) {
                return null;
            }
            partial.append(data);
            if (partial.codePointCount(0, partial.length()) > MAX_CHARACTERS) {
                partial.setLength(0);
                end();
                webSocket.sendClose(1009, "message too big").whenComplete((done, failure) -> webSocket.abort());
                return null;
            }
            if (last) {
                channel.add(partial.toString());
                partial.setLength(0);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            end();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            end();
        }
    }
}
