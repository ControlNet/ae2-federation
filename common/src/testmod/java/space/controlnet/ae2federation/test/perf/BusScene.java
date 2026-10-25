package space.controlnet.ae2federation.test.perf;

/**
 * TEST-ONLY: what the bus benchmarks drive in a scene of a main network and subnets running bus pairs, whatever links
 * the subnets to the main network.
 */
public interface BusScene extends AutoCloseable {
    /** Runs the next batch of placements; true once the whole scene is placed. */
    boolean build();

    /** Advances the networks towards their working state; true once every subnet reaches its item on main. */
    boolean ready();

    /** Inserts and extracts on the main network's bulk storage so far. */
    long operations();

    /** Items waiting in the pairs' chests. */
    long itemsInChests();

    int placements();

    /** How long the one edit that turned on every subnet's rules took; 0 without Federation rules. */
    long ruleEditNanos();

    /** Turns the first subnet's storage rule off and on again; returns the nanoseconds of each edit. */
    long[] toggleOneRule();

    /** Removes every block the scene placed and releases its chunks; returns the nanoseconds it took. */
    long clear();

    @Override
    void close();
}
