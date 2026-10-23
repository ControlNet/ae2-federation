package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import space.controlnet.ae2federation.script.Pending;
import space.controlnet.ae2federation.script.RunError;
import space.controlnet.ae2federation.test.automation.WebSocketEchoServer;

/** The {@code net} websocket functions against a local echo server, by installation. */
final class WebSocketsTest {
    private static final String OWNER = "installation-a";

    private WebSocketEchoServer server;
    private WebSockets sockets;

    @BeforeEach
    void start() throws IOException {
        server = new WebSocketEchoServer();
        sockets = new WebSockets(() -> Duration.ofSeconds(2));
    }

    @AfterEach
    void stop() throws IOException {
        sockets.close();
        server.close();
    }

    /** The answer once it is there, waiting at most five seconds. */
    private static Object answer(Pending pending) throws InterruptedException {
        waitFor(pending::done);
        return pending.value();
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        waitFor(condition, 5_000);
    }

    private static void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
        long until = System.nanoTime() + millis * 1_000_000L;
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < until, "not within " + millis + " ms");
            Thread.sleep(10);
        }
    }

    private static String error(Executable call) {
        return assertThrows(RunError.class, call).toString();
    }

    /** The next message, waiting at most five seconds for one. */
    private String next(String owner, String url) throws InterruptedException {
        var message = new String[1];
        waitFor(() -> (message[0] = sockets.receive(owner, url)) != null);
        return message[0];
    }

    @Test
    void aConnectionSendsAndReceivesTexts() throws InterruptedException {
        var url = server.url("feed");
        assertFalse(sockets.connected(OWNER, url));
        assertEquals(true, answer(sockets.connect(OWNER, url)));
        assertTrue(sockets.connected(OWNER, url));
        assertEquals(true, answer(sockets.connect(OWNER, url)), "connecting again keeps the open connection");
        assertEquals(1, server.clients());
        assertEquals(true, answer(sockets.send(OWNER, url, "iron 64")));
        assertEquals(true, answer(sockets.send(OWNER, url, "铁")));
        assertEquals("echo: iron 64", next(OWNER, url));
        assertEquals("echo: 铁", next(OWNER, url));
        assertNull(sockets.receive(OWNER, url), "nothing more queued");
        assertEquals(2, sockets.received(OWNER, url), "two messages arrived in all");
    }

    @Test
    void eachInstallationHasItsOwnConnections() throws InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        assertFalse(sockets.connected("installation-b", url));
        assertEquals("OSError: not connected to " + "'" + url + "'",
                error(() -> sockets.send("installation-b", url, "x")));
        assertNull(sockets.receive("installation-b", url));
        sockets.closeOwner(OWNER);
        assertFalse(sockets.connected(OWNER, url));
        waitFor(() -> server.clients() == 0);
    }

    @Test
    void disconnectClosesTheConnection() throws InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        assertTrue(sockets.disconnect(OWNER, url));
        assertFalse(sockets.connected(OWNER, url));
        assertFalse(sockets.disconnect(OWNER, url), "nothing left to close");
        waitFor(() -> server.clients() == 0);
    }

    @Test
    void disconnectWithoutAConnectionIsFalse() throws InterruptedException {
        var url = server.url("feed");
        assertFalse(sockets.disconnect("installation-new", url), "an installation that never connected");
        answer(sockets.connect(OWNER, url));
        sockets.closeOwner(OWNER);
        assertFalse(sockets.disconnect(OWNER, url), "after the installation was removed");
        answer(sockets.connect(OWNER, url));
        sockets.disconnectAll();
        assertFalse(sockets.disconnect(OWNER, url), "after every connection was closed");
    }

    @Test
    void theServerClosingIsSeen() throws IOException, InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        server.push("last words");
        server.close();
        waitFor(() -> !sockets.connected(OWNER, url));
        assertEquals("last words", next(OWNER, url), "what arrived before the close can still be read");
    }

    @Test
    void aBadAddressStopsTheRun() throws IOException {
        assertEquals("ValueError: url must start with ws:// or wss://: 'http://example.org/'",
                error(() -> sockets.connect(OWNER, "http://example.org/")));
        int port;
        try (var free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        var refused = "ws://127.0.0.1:" + port + "/";
        var pending = sockets.connect(OWNER, refused);
        assertEquals("OSError: could not connect to '" + refused + "'", error(() -> answer(pending)));
        assertFalse(sockets.connected(OWNER, refused));
    }

    @Test
    void anInstallationKeepsAtMostFourConnections() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            answer(sockets.connect(OWNER, server.url("feed" + i)));
        }
        assertEquals("OSError: an installation keeps at most 4 connections open",
                error(() -> sockets.connect(OWNER, server.url("feed4"))));
        assertTrue(sockets.disconnect(OWNER, server.url("feed0")));
        assertEquals(true, answer(sockets.connect(OWNER, server.url("feed4"))));
    }

    @Test
    void anUnreadQueueKeepsTheNewestMessages() throws IOException, InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        for (int i = 0; i < WebSockets.MAX_QUEUED + 10; i++) {
            server.push("m" + i);
        }
        waitFor(() -> sockets.received(OWNER, url) == WebSockets.MAX_QUEUED + 10);
        assertEquals("m10", sockets.receive(OWNER, url), "the oldest ten were dropped");
    }

    @Test
    void anUnreadQueueKeepsAtMostAMillionCharacters() throws IOException, InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        for (int i = 0; i < 15; i++) {
            server.push(i + "a".repeat(99_000));
        }
        waitFor(() -> sockets.received(OWNER, url) == 15);
        var first = sockets.receive(OWNER, url);
        assertEquals("5", first.substring(0, first.length() - 99_000), "the oldest five were dropped");
    }

    @Test
    void closedConnectionsDoNotPileUp() throws IOException, InterruptedException {
        for (int i = 0; i < 12; i++) {
            var url = server.url("feed" + i);
            answer(sockets.connect(OWNER, url));
            server.push("m" + i);
            waitFor(() -> sockets.received(OWNER, url) == 1);
            server.dropClients();
            waitFor(() -> !sockets.connected(OWNER, url));
        }
        assertEquals(WebSockets.MAX_CLOSED, sockets.kept(OWNER), "only the latest closed connections are kept");
        assertEquals("m11", sockets.receive(OWNER, server.url("feed11")), "what they received can still be read");
        assertNull(sockets.receive(OWNER, server.url("feed0")), "the one closed longest ago was let go");
    }

    @Test
    void reconnectingKeepsWhatArrivedBefore() throws IOException, InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        server.push("before the close");
        waitFor(() -> sockets.received(OWNER, url) == 1);
        server.dropClients();
        waitFor(() -> !sockets.connected(OWNER, url));
        assertEquals(true, answer(sockets.connect(OWNER, url)));
        assertEquals(1, sockets.received(OWNER, url), "the count goes on across the new connection");
        assertEquals("before the close", sockets.receive(OWNER, url));
    }

    @Test
    void closingAsTheHandshakeEndsLeavesNothingOpen() throws InterruptedException {
        var url = server.url("feed");
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        sockets.beforePublish = () -> {
            reached.countDown();
            try {
                release.await();
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            }
        };
        var pending = sockets.connect(OWNER, url);
        assertTrue(reached.await(5, TimeUnit.SECONDS), "the handshake ended");
        sockets.closeOwner(OWNER);
        release.countDown();
        waitFor(pending::done);
        assertEquals("OSError: could not connect to '" + url + "'", error(pending::value));
        waitFor(() -> server.clients() == 0);
        assertFalse(sockets.connected(OWNER, url));
    }

    /** More than the operating system buffers while the server reads nothing, so the last sends cannot finish. */
    private List<Pending> stuckSends(String url) {
        server.pause();
        var sends = new ArrayList<Pending>();
        var text = "铁".repeat(99_000);
        for (int i = 0; i < 100; i++) {
            sends.add(sockets.send(OWNER, url, text));
        }
        return sends;
    }

    @Test
    void aSendStillGoingWhenItsWaitEndsClosesTheConnection() throws InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        var sends = stuckSends(url);
        waitFor(() -> sends.stream().allMatch(Pending::done));
        assertFalse(sockets.connected(OWNER, url), "no send may start while one is still going");
        assertEquals("OSError: could not send to '" + url + "'", error(sends.getLast()::value));
    }

    @Test
    void cancellingASendStillGoingClosesTheConnection() throws InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        var sends = stuckSends(url);
        sends.getLast().cancel();
        waitFor(() -> !sockets.connected(OWNER, url), 1_000);
        waitFor(() -> sends.stream().allMatch(Pending::done));
    }

    @Test
    void aLongMessageIsRefusedBeforeItIsSent() throws InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        assertEquals("ValueError: a message holds at most 100000 characters",
                error(() -> sockets.send(OWNER, url, "铁".repeat(100_001))));
        assertTrue(sockets.connected(OWNER, url), "the connection stays open");
    }

    @Test
    void aMessageOverTheStringCapClosesTheConnection() throws IOException, InterruptedException {
        var url = server.url("feed");
        answer(sockets.connect(OWNER, url));
        server.push("a".repeat(100_001));
        waitFor(() -> !sockets.connected(OWNER, url));
        assertNull(sockets.receive(OWNER, url));
    }
}
