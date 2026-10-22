package space.controlnet.ae2federation.test.automation;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only WebSocket server on this machine (RFC 6455, text frames only), standing in for the outside world in
 * JUnit tests and GameTests: it answers each text with {@code "echo: <text>"}, and {@link #push} sends a text to every
 * client. Neither the JDK nor this game's libraries carry a WebSocket server, so it is written out here.
 */
public final class WebSocketEchoServer implements AutoCloseable {
    private static final String ACCEPT_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket socket;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final List<String> received = new CopyOnWriteArrayList<>();

    public WebSocketEchoServer() throws IOException {
        // The loopback address can be ::1 in the game; url() names 127.0.0.1.
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        var accepting = new Thread(this::accept, "websocket-echo-accept");
        accepting.setDaemon(true);
        accepting.start();
    }

    /** {@code ws://127.0.0.1:<port>/<path>}. */
    public String url(String path) {
        return "ws://127.0.0.1:" + socket.getLocalPort() + "/" + path;
    }

    /** The texts the clients sent, in order. */
    public List<String> received() {
        return List.copyOf(received);
    }

    public int clients() {
        return (int) clients.stream().filter(client -> !client.closed).count();
    }

    /** Sends the text to every open client. */
    public void push(String text) throws IOException {
        for (var client : clients) {
            if (!client.closed) {
                client.send(1, text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
        for (var client : clients) {
            client.connection.close();
        }
    }

    private void accept() {
        while (!socket.isClosed()) {
            try {
                var connection = socket.accept();
                var client = new Client(connection);
                clients.add(client);
                var reading = new Thread(client::serve, "websocket-echo-client");
                reading.setDaemon(true);
                reading.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    private final class Client {
        final Socket connection;
        volatile boolean closed;

        Client(Socket connection) {
            this.connection = connection;
        }

        void serve() {
            try (connection) {
                var in = new DataInputStream(connection.getInputStream());
                handshake(in, connection.getOutputStream());
                var message = new ByteArrayOutputStream();
                while (true) {
                    int first = in.readUnsignedByte();
                    int second = in.readUnsignedByte();
                    boolean fin = (first & 0x80) != 0;
                    int opcode = first & 0x0f;
                    long length = second & 0x7f;
                    if (length == 126) {
                        length = in.readUnsignedShort();
                    } else if (length == 127) {
                        length = in.readLong();
                    }
                    var mask = new byte[4];
                    if ((second & 0x80) != 0) {
                        in.readFully(mask);
                    }
                    var payload = new byte[(int) length];
                    in.readFully(payload);
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                    if (opcode == 8) {
                        send(8, payload.length >= 2 ? new byte[] {payload[0], payload[1]} : new byte[0]);
                        return;
                    }
                    if (opcode == 9) {
                        send(10, payload);
                        continue;
                    }
                    if (opcode == 1 || opcode == 0) {
                        message.write(payload);
                        if (fin) {
                            var text = message.toString(StandardCharsets.UTF_8);
                            message.reset();
                            received.add(text);
                            send(1, ("echo: " + text).getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException ended) {
                // The client went away or the server closed.
            } finally {
                closed = true;
            }
        }

        /** One unmasked frame, as a server sends them. */
        synchronized void send(int opcode, byte[] payload) throws IOException {
            var out = connection.getOutputStream();
            out.write(0x80 | opcode);
            if (payload.length < 126) {
                out.write(payload.length);
            } else if (payload.length < 65536) {
                out.write(126);
                out.write(payload.length >> 8);
                out.write(payload.length & 0xff);
            } else {
                out.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    out.write((int) ((long) payload.length >> shift) & 0xff);
                }
            }
            out.write(payload);
            out.flush();
        }
    }

    private static void handshake(InputStream in, OutputStream out) throws IOException {
        String key = null;
        var line = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) {
                throw new IOException("closed during the handshake");
            }
            if (c == '\n') {
                var text = line.toString().strip();
                line.setLength(0);
                if (text.isEmpty()) {
                    break;
                }
                int colon = text.indexOf(':');
                if (colon > 0 && text.substring(0, colon).equalsIgnoreCase("Sec-WebSocket-Key")) {
                    key = text.substring(colon + 1).strip();
                }
            } else {
                line.append((char) c);
            }
        }
        if (key == null) {
            throw new IOException("no Sec-WebSocket-Key");
        }
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + ACCEPT_SUFFIX).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
}
