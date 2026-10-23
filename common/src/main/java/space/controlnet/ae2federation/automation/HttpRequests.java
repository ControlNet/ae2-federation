package space.controlnet.ae2federation.automation;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import space.controlnet.ae2federation.script.DictValue;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.Pending;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;

/**
 * {@code net.http}: requests sent on a small pool of daemon threads, so the server thread never waits for the
 * network. A call gives a {@link Pending} the run waits on; the answer becomes a {@code Response} on the server thread.
 *
 * <p>Assistant choices: a timeout for the whole request (10 s unless configured, never unlimited); request and answer
 * texts of at most 100,000 characters, the language's string cap; at most {@link #MAX_IN_FLIGHT} requests waiting on
 * a server at once, a busy server's call stopping the run with {@code OSError}; any HTTP status is an answer, and only
 * a request that gets none fails. Header names come back in lower case, repeated ones joined with {@code ", "}.
 * Redirects are followed, except from https to http. No host is refused: that waits for the permission system
 * (user, 2026-10-22).
 */
public final class HttpRequests {
    public static final int MAX_IN_FLIGHT = 16;
    public static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final int MAX_CHARACTERS = 100_000;
    /** A character takes at most four bytes in UTF-8, so a longer answer is too long without decoding it. */
    private static final int MAX_BYTES = 4 * MAX_CHARACTERS;
    private static final int THREADS = 4;
    private static final Map<Object, HttpRequests> SERVERS = new HashMap<>();

    private final Supplier<Duration> timeout;
    private final int maxInFlight;
    private final ExecutorService pool;
    private final HttpClient client;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Set<Request> open = ConcurrentHashMap.newKeySet();

    public HttpRequests(Supplier<Duration> timeout, int maxInFlight) {
        this.timeout = timeout;
        this.maxInFlight = maxInFlight;
        var threads = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(THREADS, task -> {
            var thread = new Thread(task, "ae2federation-http-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder().executor(pool).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** The server's requests, made on first use; {@code server} is only a key. */
    public static synchronized HttpRequests of(Object server) {
        return SERVERS.computeIfAbsent(server, key -> new HttpRequests(AutomationRule::timeout, MAX_IN_FLIGHT));
    }

    /** Drops the server's open requests and threads. */
    public static synchronized void closeServer(Object server) {
        var requests = SERVERS.remove(server);
        if (requests != null) {
            requests.close();
        }
    }

    /** Sends the request; a bad method, URL or header stops the run at once with {@code ValueError}. */
    public Pending send(String method, String url, String body, DictValue headers) {
        if (!METHODS.contains(method)) {
            throw new RunError("ValueError", "method must be one of " + String.join(", ", METHODS) + ", not "
                    + Ops.repr(method));
        }
        if (body.codePointCount(0, body.length()) > MAX_CHARACTERS) {
            throw new RunError("ValueError", "a request body holds at most " + MAX_CHARACTERS + " characters");
        }
        var wait = timeout.get();
        var builder = HttpRequest.newBuilder(uri(url)).timeout(wait)
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        var names = headers.keys();
        var values = headers.values();
        for (int i = 0; i < names.size(); i++) {
            var name = (String) names.get(i);
            try {
                builder.header(name, (String) values.get(i));
            } catch (IllegalArgumentException refused) {
                throw new RunError("ValueError", "header " + Ops.repr(name) + " cannot be set");
            }
        }
        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet();
            throw new RunError("OSError", "at most " + maxInFlight + " HTTP requests can wait at once");
        }
        var sent = client.sendAsync(builder.build(), info -> new Limited());
        var request = new Request(sent, wait);
        open.add(request);
        request.answer.whenComplete((answer, failure) -> {
            inFlight.decrementAndGet();
            open.remove(request);
        });
        return request;
    }

    /** Cancels the open requests and stops the threads. */
    public void close() {
        open.forEach(Request::cancel);
        client.shutdownNow();
        pool.shutdownNow();
    }

    private static URI uri(String url) {
        try {
            var uri = new URI(url);
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
            // Reported below, as any other URL that is not http or https.
        }
        throw new RunError("ValueError", "url must start with http:// or https://: " + Ops.repr(url));
    }

    /** What came back, as plain data: made on a pool thread, turned into a {@code Response} on the server thread. */
    private record Answer(int status, String text, Map<String, String> headers) {
    }

    private static final class Request implements Pending {
        final CompletableFuture<HttpResponse<byte[]>> sent;
        final CompletableFuture<Answer> answer;
        final Duration wait;

        Request(CompletableFuture<HttpResponse<byte[]>> sent, Duration wait) {
            this.sent = sent;
            this.wait = wait;
            // The request's own timeout ends at the headers; this one also bounds a body that keeps trickling in.
            this.answer = sent.thenApply(HttpRequests::answer).orTimeout(wait.toMillis(), TimeUnit.MILLISECONDS);
            answer.whenComplete((done, failure) -> {
                if (failure != null) {
                    sent.cancel(true);
                }
            });
        }

        @Override
        public boolean done() {
            return answer.isDone();
        }

        @Override
        public Object value() {
            try {
                return response(answer.join());
            } catch (CancellationException cancelled) {
                throw new RunError("OSError", "the request was cancelled");
            } catch (CompletionException failed) {
                throw failure(failed.getCause(), wait);
            }
        }

        @Override
        public void cancel() {
            answer.cancel(true);
            sent.cancel(true);
        }
    }

    private static Answer answer(HttpResponse<byte[]> response) {
        var charset = charset(response.headers().firstValue("content-type").orElse(""));
        var text = new String(response.body(), charset);
        if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) {
            throw new TooLong();
        }
        var headers = new TreeMap<String, String>();
        response.headers().map().forEach((name, values) -> {
            if (!name.startsWith(":")) {
                headers.merge(name.toLowerCase(Locale.ROOT), String.join(", ", values), (a, b) -> a + ", " + b);
            }
        });
        return new Answer(response.statusCode(), text, headers);
    }

    /** The charset a {@code Content-Type} names, or UTF-8; bytes it cannot read become U+FFFD. */
    private static Charset charset(String contentType) {
        for (var part : contentType.split(";")) {
            var pair = part.strip().split("=", 2);
            if (pair.length == 2 && pair[0].equalsIgnoreCase("charset")) {
                try {
                    return Charset.forName(pair[1].strip().replace("\"", ""));
                } catch (IllegalArgumentException unknown) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static RecordValue response(Answer answer) {
        var headers = new DictValue();
        answer.headers().forEach(headers::put);
        var fields = new LinkedHashMap<String, Object>();
        fields.put("status", (long) answer.status());
        fields.put("text", answer.text());
        fields.put("headers", headers);
        return new RecordValue("Response", fields, true);
    }

    private static RunError failure(Throwable cause, Duration wait) {
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return new RunError("OSError", "the request timed out after " + shown(wait));
        }
        if (cause instanceof TooLong) {
            return new RunError("OSError", "the response is longer than " + MAX_CHARACTERS + " characters");
        }
        if (cause instanceof ConnectException connect) {
            return new RunError("OSError", connect.getCause() instanceof UnresolvedAddressException
                    ? "the host was not found" : "the connection was refused");
        }
        if (cause instanceof CancellationException) {
            return new RunError("OSError", "the request was cancelled");
        }
        return new RunError("OSError", "the request failed (" + cause.getClass().getSimpleName() + ")");
    }

    private static String shown(Duration wait) {
        return wait.toMillis() % 1000 == 0 ? wait.toSeconds() + " s" : wait.toMillis() + " ms";
    }

    /** An answer longer than the string cap. */
    private static final class TooLong extends RuntimeException {
        TooLong() {
            super(null, null, false, false);
        }
    }

    /** Collects an answer's body, giving up as soon as it passes {@link #MAX_BYTES}. */
    private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (var item : items) {
                if (bytes.size() + item.remaining() > MAX_BYTES) {
                    subscription.cancel();
                    body.completeExceptionally(new TooLong());
                    return;
                }
                var chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
