package space.controlnet.ae2federation.function;

/** Hands out fresh node IDs: {@code prefix} and a counter. A program's IDs only need to be unique within it. */
public final class Ids {
    private final String prefix;
    private int next;

    public Ids(String prefix) {
        this.prefix = prefix;
    }

    public String next() {
        return prefix + ++next;
    }
}
