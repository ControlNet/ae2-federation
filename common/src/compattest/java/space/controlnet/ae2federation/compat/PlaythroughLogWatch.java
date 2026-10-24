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
 * Phase 4 order runs. Other warnings are not recorded.
 */
final class PlaythroughLogWatch extends AbstractAppender implements AutoCloseable {
    private final List<String> events = new ArrayList<>();

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
        synchronized (events) { events.add(text); }
    }

    /** The number of recorded events so far; an order's window starts at this mark. */
    int mark() { synchronized (events) { return events.size(); } }

    /** Events recorded since {@code mark}. */
    List<String> since(int mark) { synchronized (events) { return List.copyOf(events.subList(mark, events.size())); } }

    @Override public void close() {
        var context = context();
        context.getConfiguration().getRootLogger().removeAppender(getName());
        context.updateLoggers();
        stop();
    }
}
