package space.controlnet.ae2federation.function;

/** What a function's parameter, result or expression carries. */
public enum ValueType {
    BOOL("bool"),
    NUMBER("number"),
    TEXT("text"),
    /** One AE2 resource: a key type and an ID, such as an item or a fluid. */
    RESOURCE("resource"),
    /** Which resources a sharing rule exposes: all, an allow list or a deny list. */
    FILTER("filter"),
    /** An action's result: it returns nothing. */
    NOTHING("nothing");

    private final String word;

    ValueType(String word) {
        this.word = word;
    }

    /** The name scripts write. */
    public String word() {
        return word;
    }

    public static ValueType of(String word) {
        for (var type : values()) {
            if (type.word.equals(word)) return type;
        }
        throw new IllegalArgumentException("Unknown type " + word);
    }
}
