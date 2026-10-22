package space.controlnet.ae2federation.automation;

import java.time.Duration;
import space.controlnet.ae2federation.script.RunError;

/**
 * The server's rule for programs reaching outside the game: whether the {@code fs} functions, {@code net.http} and the
 * websocket functions work, how long a network call waits, and how big the workspace folder may grow. All on by
 * default (user, 2026-10-22); the server configuration sets them when it loads, and until then, and in tests, the
 * defaults hold. A switch that is off stops the run at the call with {@code PermissionError}, in a dry run too.
 */
public final class AutomationRule {
    public static final int DEFAULT_TIMEOUT_SECONDS = 10;
    public static final int MAX_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_WORKSPACE_MB = 16;
    public static final int MAX_WORKSPACE_MB = 4096;

    private static volatile boolean fileAccess = true;
    private static volatile boolean http = true;
    private static volatile boolean websockets = true;
    private static volatile Duration timeout = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    private static volatile long workspaceCapBytes = megabytes(DEFAULT_WORKSPACE_MB);

    private AutomationRule() {
    }

    public static boolean fileAccess() {
        return fileAccess;
    }

    public static boolean http() {
        return http;
    }

    public static boolean websockets() {
        return websockets;
    }

    /** How long {@code net.http}, connecting and sending wait at most. */
    public static Duration timeout() {
        return timeout;
    }

    public static long workspaceCapBytes() {
        return workspaceCapBytes;
    }

    public static void set(boolean files, boolean requests, boolean sockets, int timeoutSeconds, int workspaceMb) {
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Network timeout out of range: " + timeoutSeconds);
        }
        if (workspaceMb < 1 || workspaceMb > MAX_WORKSPACE_MB) {
            throw new IllegalArgumentException("Workspace size out of range: " + workspaceMb);
        }
        fileAccess = files;
        http = requests;
        websockets = sockets;
        timeout = Duration.ofSeconds(timeoutSeconds);
        workspaceCapBytes = megabytes(workspaceMb);
    }

    public static void reset() {
        set(true, true, true, DEFAULT_TIMEOUT_SECONDS, DEFAULT_WORKSPACE_MB);
    }

    /** Stops the run if the library call belongs to a group the server turned off; other calls pass. */
    public static void require(String call) {
        if (call.startsWith("fs.") && !fileAccess) {
            throw new RunError("PermissionError", "file access is turned off on this server (allowFileAccess)");
        }
        if (call.equals("net.http") && !http) {
            throw new RunError("PermissionError", "HTTP requests are turned off on this server (allowHttp)");
        }
        if (call.startsWith("net.") && !call.equals("net.http") && !websockets) {
            throw new RunError("PermissionError", "websockets are turned off on this server (allowWebsockets)");
        }
    }

    private static long megabytes(int megabytes) {
        return megabytes * 1024L * 1024L;
    }
}
