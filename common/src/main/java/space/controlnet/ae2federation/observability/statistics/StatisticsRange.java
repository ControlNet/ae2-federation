package space.controlnet.ae2federation.observability.statistics;

/** The time spans the statistics page offers: how long one point covers and how many points a chart shows. */
public enum StatisticsRange {
    /** Two minutes, one point a second. */
    SECONDS(1, 120),
    /** Ten minutes, one point every five seconds. */
    FIVE_SECONDS(5, 120),
    /** One hour, one point a minute. */
    MINUTES(60, 60),
    /** One day, one point every ten minutes. */
    DAY(600, 144);

    /** The longest range: what is older is forgotten. */
    public static final StatisticsRange LONGEST = DAY;

    private final int seconds;
    private final int buckets;

    StatisticsRange(int seconds, int buckets) {
        this.seconds = seconds;
        this.buckets = buckets;
    }

    /** Seconds one point covers. */
    public int seconds() {
        return seconds;
    }

    /** Points a chart of this range shows. */
    public int buckets() {
        return buckets;
    }
}
