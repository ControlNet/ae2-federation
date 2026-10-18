package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Map;

/**
 * The game's standard library as the language sees it: the {@code ae2}, {@code ae2f} and {@code mc} namespaces, the
 * typed strings, the closed sets some of them hold, and the built-in records. Signatures only; the host performs the
 * calls. Settled with the user in rounds (docs/ideas/function-system.md, "Standard library discussion").
 */
public final class StandardLibrary {
    /** Strings whose static type says what they name; completion and the checker know their values. */
    public static final List<String> TYPED_STRINGS = List.of("Item", "Fluid", "Resource", "Interface", "Network",
            "Side", "Capability", "RuleMode", "FilterMode");

    /** Typed strings with a fixed set of values, known without the game. */
    public static final Map<String, List<String>> ENUMERATIONS = Map.of(
            "Side", List.of("up", "down", "north", "south", "west", "east"),
            "Capability", List.of("storage", "crafting", "power"),
            "RuleMode", List.of("off", "on", "reexport"),
            "FilterMode", List.of("all", "allow", "deny"));

    /** Values the program can hold but not build: a block selected through an interface. */
    public static final List<String> OPAQUE = List.of("Block");

    /** Built-in records and their fields, all read-only. */
    public static final Map<String, List<Signature.Arg>> RECORDS = Map.of(
            "Stack", List.of(arg("resource", "Resource"), arg("amount", "int")));

    public static final Map<String, List<Signature>> NAMESPACES = Map.of(
            "ae2", List.of(
                    fn("amount", "int", "How much of the resource is stored.", arg("resource", "Resource"), network()),
                    fn("storage", "list[Stack]", "Everything stored.", network()),
                    fn("craftable", "bool", "Whether a pattern makes the resource.", arg("resource", "Resource"),
                            network()),
                    fn("crafting", "int", "How much of the resource running jobs are still making.",
                            arg("resource", "Resource"), network()),
                    act("craft", "bool", "Starts a crafting job; False if it could not start.",
                            arg("resource", "Resource"), amount(), network()),
                    act("cancel", "bool", "Cancels the jobs this program started for the resource.",
                            arg("resource", "Resource"), network()),
                    act("import_from", "bool", "Moves the resource from the block into the network.",
                            arg("block", "Block"), arg("resource", "Resource"), amount(), network()),
                    act("export_to", "bool", "Moves the resource from the network into the block.",
                            arg("block", "Block"), arg("resource", "Resource"), amount(), network()),
                    fn("free_cpus", "int", "Crafting CPUs not busy.", network()),
                    fn("energy", "float", "AE stored.", network())),
            "ae2f", List.of(
                    fn("select", "Block", "The block in front of the interface, reach blocks away, seen from a side.",
                            arg("interface", "Interface"),
                            new Signature.Arg("reach", type("int"), "1", new Signature.Range(1, 4)),
                            new Signature.Arg("side", type("Side"), "None")),
                    fn("networks", "list[Network]", "The networks of the domain."),
                    fn("online", "bool", "Whether the network is usable now.", arg("network", "Network")),
                    fn("rule", "RuleMode", "Whether the provider shares the capability with the consumer.",
                            arg("provider", "Network"), arg("consumer", "Network"), arg("capability", "Capability")),
                    act("set_rule", "bool", "Sets whether the provider shares the capability with the consumer.",
                            arg("provider", "Network"), arg("consumer", "Network"), arg("capability", "Capability"),
                            arg("mode", "RuleMode")),
                    act("set_filter", "bool", "Sets which resources pass while the rule is on.",
                            arg("provider", "Network"), arg("consumer", "Network"), arg("capability", "Capability"),
                            arg("mode", "FilterMode"), arg("resources", "list[Resource]"))),
            "mc", List.of(
                    act("move", "bool", "Moves the resource from one block to another.", arg("source", "Block"),
                            arg("target", "Block"), arg("resource", "Resource"), amount()),
                    fn("amount", "int", "How much of the resource the block holds.", arg("block", "Block"),
                            arg("resource", "Resource")),
                    fn("storage", "list[Stack]", "What the block holds.", arg("block", "Block")),
                    fn("block", "str", "The block's ID.", arg("block", "Block")),
                    fn("redstone", "int", "The redstone signal the block receives, 0 to 15.", arg("block", "Block")),
                    act("set_redstone", "bool", "Emits redstone from the interface's front face.",
                            arg("interface", "Interface"),
                            new Signature.Arg("level", type("int"), null, new Signature.Range(0, 15))),
                    act("break_block", "bool", "Breaks the block; its drops go into the domain's storage.",
                            arg("block", "Block")),
                    act("place_block", "bool", "Places the item, taken from the domain's storage, as a block.",
                            arg("block", "Block"), arg("item", "Item")),
                    fn("time", "int", "Game time in ticks."),
                    act("wait", "None", "Pauses the run.",
                            new Signature.Arg("ticks", type("int"), null, new Signature.Range(0, Long.MAX_VALUE))),
                    fn("fe", "int", "FE stored in the block.", arg("block", "Block")),
                    act("move_fe", "bool", "Moves FE from one block to another.", arg("source", "Block"),
                            arg("target", "Block"), amount())));

    private StandardLibrary() {
    }

    /** {@code list[Stack]} and the like, as a type hint is written. */
    static TypeRef type(String text) {
        int open = text.indexOf('[');
        return open < 0 ? new TypeRef(text, List.of())
                : new TypeRef(text.substring(0, open), List.of(type(text.substring(open + 1, text.length() - 1))));
    }

    private static Signature.Arg arg(String name, String type) {
        return new Signature.Arg(name, type(type));
    }

    /** An amount to move or craft: never negative. */
    private static Signature.Arg amount() {
        return new Signature.Arg("amount", type("int"), null, new Signature.Range(0, Long.MAX_VALUE));
    }

    private static Signature.Arg network() {
        return new Signature.Arg("network", type("Network"), "None");
    }

    private static Signature fn(String name, String result, String summary, Signature.Arg... args) {
        return new Signature(name, List.of(args), type(result), summary);
    }

    /** A function that acts on the world: it takes a tick. */
    private static Signature act(String name, String result, String summary, Signature.Arg... args) {
        return new Signature(name, List.of(args), type(result), summary, true);
    }
}
