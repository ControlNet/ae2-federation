package space.controlnet.ae2federation.compat;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Records every log event that carries an exception or has level ERROR or above, from any logger and thread, while a
 * Phase 4 run is live. Other warnings are not recorded.
 *
 * <p>Both bases tick on the server thread in one world, so neither the thread nor the time of an event tells which base
 * caused it. An event is charged to the federated base only when Federation's own code logged it or threw it (a
 * Federation logger, or a Federation frame or mixin handler in its stack trace); the plain AE2 baseline has no Federation devices. The
 * harness's own logging is kept apart; everything else is unattributed.
 */
final class PlaythroughLogWatch extends AbstractAppender implements AutoCloseable {
    static final String FEDERATED = "federated";
    static final String HARNESS = "harness";
    static final String UNATTRIBUTED = "unattributed";
    private static final String FEDERATION = "space.controlnet.ae2federation.";
    private static final List<String> HARNESS_PACKAGES = List.of(FEDERATION + "compat.", FEDERATION + "test.");

    /** One recorded event: who it is charged to, and its text. */
    record Event(String source, String text) { }

    private final List<Event> events = new ArrayList<>();
    private boolean closed;

    private PlaythroughLogWatch() {
        super("AE2FederationPlaythroughLogWatch", null, null, true, Property.EMPTY_ARRAY);
    }

    static PlaythroughLogWatch attach() {
        var watch = new PlaythroughLogWatch();
        watch.start();
        var context = context();
        context.getConfiguration().getRootLogger().addAppender(watch, Level.ALL, null);
        context.updateLoggers();
        return watch;
    }

    /** The context the server's loggers use, reached through its root logger. */
    private static LoggerContext context() {
        return ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).getContext();
    }

    @Override public void append(LogEvent event) {
        if (event.getThrown() == null && !event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
        var thrown = event.getThrown();
        String text = event.getLevel() + " [" + event.getThreadName() + "] " + event.getLoggerName() + ": "
                + event.getMessage().getFormattedMessage()
                + (thrown == null ? "" : " | " + thrown + (thrown.getStackTrace().length > 0 ? " at " + thrown.getStackTrace()[0] : ""));
        synchronized (events) { events.add(new Event(source(event.getLoggerName(), thrown), text)); }
    }

    private static String source(String logger, Throwable thrown) {
        String name = logger == null ? "" : logger;
        if (HARNESS_PACKAGES.stream().anyMatch(name::startsWith)) return HARNESS;
        if (federation(name)) return FEDERATED;
        for (var cause = thrown; cause != null; cause = cause.getCause())
            // Mixin handlers merged into another mod's class keep the mixin's mod id in their method name.
            for (var frame : cause.getStackTrace())
                if (federation(frame.getClassName()) || frame.getMethodName().contains("$ae2federation$")) return FEDERATED;
        return UNATTRIBUTED;
    }

    private static boolean federation(String name) {
        return name.startsWith(FEDERATION) && HARNESS_PACKAGES.stream().noneMatch(name::startsWith);
    }

    /** The number of recorded events so far; a window starts at this mark. */
    int mark() { synchronized (events) { return events.size(); } }

    /** Events recorded since {@code mark}. */
    List<Event> since(int mark) { synchronized (events) { return List.copyOf(events.subList(mark, events.size())); } }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var context = context();
        context.getConfiguration().getRootLogger().removeAppender(getName());
        context.updateLoggers();
        stop();
    }
}
