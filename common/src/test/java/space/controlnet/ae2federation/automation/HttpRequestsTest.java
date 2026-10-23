package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import space.controlnet.ae2federation.script.DictValue;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.Pending;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;

/** {@code net.http} against a local server: answers, what is sent, and how a request fails. */
final class HttpRequestsTest {
    private HttpServer server;
    /** The server's handler threads, stopped after each test so slow handlers do not outlive it. */
    private ExecutorService handlers;
    private HttpRequests requests;
    private final AtomicReference<String> received = new AtomicReference<>("");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            exchange.getResponseHeaders().add("X-Answer", "42");
            exchange.getResponseHeaders().add("X-Answer", "43");
            var body = "hello 铁".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/echo", exchange -> {
            received.set(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("X-Token") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            var body = "no such page".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/huge", exchange -> {
            var body = "a".repeat(500_000).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        handlers = Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.start();
        requests = new HttpRequests(() -> Duration.ofMillis(800), 4);
    }

    @AfterEach
    void stop() throws InterruptedException {
        try {
            requests.close();
            server.stop(0);
        } finally {
            handlers.shutdownNow();
        }
        assertTrue(handlers.awaitTermination(5, TimeUnit.SECONDS), "the handler threads stop");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static DictValue headers(String... pairs) {
        var dict = new DictValue();
        for (int i = 0; i < pairs.length; i += 2) {
            dict.put(pairs[i], pairs[i + 1]);
        }
        return dict;
    }

    /** The answer once it is there, waiting at most five seconds. */
    private static Object answer(Pending pending) throws InterruptedException {
        long until = System.nanoTime() + 5_000_000_000L;
        while (!pending.done()) {
            assertTrue(System.nanoTime() < until, "no answer within five seconds");
            Thread.sleep(10);
        }
        return pending.value();
    }

    private static String error(Executable call) {
        return assertThrows(RunError.class, call).toString();
    }

    @Test
    void anAnswerGivesItsStatusTextAndHeaders() throws InterruptedException {
        var response = (RecordValue) answer(requests.send("GET", url("/hello"), "", headers()));
        assertEquals("Response", response.type());
        assertEquals(200L, response.field("status"));
        assertEquals("hello 铁", response.field("text"));
        var answered = (DictValue) response.field("headers");
        assertEquals("'42, 43'", Ops.repr(answered.values().get(answered.keys().indexOf("x-answer"))),
                "names are lower case, and repeated headers are joined");
    }

    @Test
    void theMethodBodyAndHeadersAreSent() throws InterruptedException {
        var response = (RecordValue) answer(requests.send("POST", url("/echo"), "iron=64",
                headers("X-Token", "abc")));
        assertEquals(204L, response.field("status"));
        assertEquals("POST abc iron=64", received.get());
    }

    @Test
    void anyStatusIsAnAnswer() throws InterruptedException {
        var response = (RecordValue) answer(requests.send("GET", url("/missing"), "", headers()));
        assertEquals(404L, response.field("status"));
        assertEquals("no such page", response.field("text"));
    }

    @Test
    void aSlowServerTimesOut() {
        var pending = requests.send("GET", url("/slow"), "", headers());
        assertEquals("OSError: the request timed out after 800 ms", error(() -> answer(pending)));
    }

    @Test
    void aClosedPortRefusesTheConnection() throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var pending = requests.send("GET", "http://127.0.0.1:" + port + "/", "", headers());
        assertEquals("OSError: the connection was refused", error(() -> answer(pending)));
    }

    @Test
    void aLongAnswerIsRefused() {
        var pending = requests.send("GET", url("/huge"), "", headers());
        assertEquals("OSError: the response is longer than 100000 characters", error(() -> answer(pending)));
    }

    @Test
    void aLongBodyIsRefusedBeforeItIsSent() {
        assertEquals("ValueError: a request body holds at most 100000 characters",
                error(() -> requests.send("POST", url("/echo"), "铁".repeat(100_001), headers())));
        assertEquals("", received.get(), "nothing reached the server");
    }

    @Test
    void aBadRequestStopsTheRunAtOnce() {
        assertEquals("ValueError: url must start with http:// or https://: 'ftp://example.org/'",
                error(() -> requests.send("GET", "ftp://example.org/", "", headers())));
        assertEquals("ValueError: url must start with http:// or https://: 'not a url'",
                error(() -> requests.send("GET", "not a url", "", headers())));
        assertEquals("ValueError: url must start with http:// or https://: 'http://'",
                error(() -> requests.send("GET", "http://", "", headers())));
        assertEquals("ValueError: header 'Host' cannot be set",
                error(() -> requests.send("GET", url("/hello"), "", headers("Host", "example.org"))));
        assertEquals("ValueError: method must be one of GET, POST, PUT, PATCH, DELETE, HEAD, not 'BREW'",
                error(() -> requests.send("BREW", url("/hello"), "", headers())));
    }

    @Test
    void onlySoManyRequestsWaitAtOnce() throws InterruptedException {
        var one = new HttpRequests(() -> Duration.ofSeconds(5), 1);
        try {
            var first = one.send("GET", url("/slow"), "", headers());
            assertEquals("OSError: at most 1 HTTP requests can wait at once",
                    error(() -> one.send("GET", url("/hello"), "", headers())));
            first.cancel();
            assertTrue(first.done());
            assertEquals("OSError: the request was cancelled", error(first::value));
            assertEquals(200L, ((RecordValue) answer(one.send("GET", url("/hello"), "", headers()))).field("status"),
                    "a cancelled request frees its place");
        } finally {
            one.close();
        }
    }
}
