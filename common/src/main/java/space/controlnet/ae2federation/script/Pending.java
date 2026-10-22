package space.controlnet.ae2federation.script;

/**
 * A library call's answer that is not there yet, such as an HTTP response. The {@link Runner} waits for it, answering
 * {@link Runner.Status#WAITING}, and goes on in the tick it is done; the wait takes none of the ticks' actions.
 * Free of the game, so tests can complete one by hand.
 */
public interface Pending {
    /** Whether the answer is there; asked on the server thread once a tick. */
    boolean done();

    /** The answer, or the {@link RunError} it ended with; only once {@link #done}. */
    Object value();

    /** Gives the answer up: the run has stopped. */
    void cancel();
}
