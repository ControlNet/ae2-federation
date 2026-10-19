package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Map;

/**
 * The game's standard library as the language sees it: the {@code ae2}, {@code ae2f} and {@code mc} namespaces, the
 * typed strings, the closed sets some of them hold, and the built-in records. Signatures and an example of each
 * function only; the host performs the calls. Settled with the user in rounds
 * (docs/ideas/function-system.md, "Standard library discussion").
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
                    fn("amount", "int", "How much of the resource is stored.", """
                            def iron_is_low() -> bool:
                                return ae2.amount("minecraft:iron_ingot") < 64
                            """, arg("resource", "Resource"), network()),
                    fn("storage", "list[Stack]", "Everything stored.", """
                            def total_stored() -> int:
                                total = 0
                                for stack in ae2.storage():
                                    total += stack.amount
                                return total
                            """, network()),
                    fn("craftable", "bool", "Whether a pattern makes the resource.", """
                            def craft_if_possible(item: Item, amount: int) -> bool:
                                if ae2.craftable(item):
                                    return ae2.craft(item, amount)
                                return False
                            """, arg("resource", "Resource"), network()),
                    fn("crafting", "int", "How much of the resource running jobs are still making.",
                            """
                            def iron_on_the_way() -> int:
                                return ae2.amount("minecraft:iron_ingot") + ae2.crafting("minecraft:iron_ingot")
                            """, arg("resource", "Resource"), network()),
                    fn("patterns", "list[Resource]", "Everything that can be crafted.", """
                            def keep_everything_stocked(target: int) -> None:
                                for resource in ae2.patterns():
                                    missing = target - ae2.amount(resource) - ae2.crafting(resource)
                                    if missing > 0:
                                        ae2.craft(resource, missing)
                            """, network()),
                    act("craft", "bool", "Starts a crafting job; False if it could not start.", """
                            def keep_iron() -> None:
                                if ae2.amount("minecraft:iron_ingot") < 64:
                                    ae2.craft("minecraft:iron_ingot", 64)
                            """, arg("resource", "Resource"), amount(), network()),
                    act("cancel", "bool", "Cancels the jobs this program started for the resource.", """
                            def stop_iron_when_full() -> None:
                                if ae2.amount("minecraft:iron_ingot") >= 1000:
                                    ae2.cancel("minecraft:iron_ingot")
                            """, arg("resource", "Resource"), network()),
                    act("import_from", "bool", "Moves the resource from the block into the network.",
                            """
                            def empty_chest(port: Interface) -> None:
                                chest = ae2f.select(port)
                                for stack in mc.storage(chest):
                                    ae2.import_from(chest, stack.resource, stack.amount)
                            """, arg("block", "Block"), arg("resource", "Resource"), amount(), network()),
                    act("export_to", "bool", "Moves the resource from the network into the block.",
                            """
                            def feed_machine(port: Interface, item: Item) -> None:
                                machine = ae2f.select(port)
                                if mc.amount(machine, item) < 16:
                                    ae2.export_to(machine, item, 16)
                            """, arg("block", "Block"), arg("resource", "Resource"), amount(), network()),
                    act("move", "bool", "Moves the resource from one network of the domain to another.",
                            """
                            def stock_farm(base: Network, farm: Network, item: Item) -> None:
                                if ae2.amount(item, farm) < 64:
                                    ae2.move(base, farm, item, 64)
                            """, arg("source", "Network"), arg("target", "Network"), arg("resource", "Resource"),
                            amount()),
                    fn("free_cpus", "int", "Crafting CPUs not busy.", """
                            def craft_when_idle(item: Item, amount: int) -> None:
                                if ae2.free_cpus() > 0:
                                    ae2.craft(item, amount)
                            """, network()),
                    fn("energy", "float", "AE stored.", """
                            def low_power() -> bool:
                                return ae2.energy() < 10000
                            """, network())),
            "ae2f", List.of(
                    fn("select", "Block", "The block in front of the interface, reach blocks away, seen from a side.",
                            """
                            def smelted(port: Interface) -> int:
                                # A furnace hands out its output to the side below it.
                                furnace = ae2f.select(port, side="down")
                                return mc.amount(furnace, "minecraft:iron_ingot")
                            """, arg("interface", "Interface"),
                            new Signature.Arg("reach", type("int"), "1", new Signature.Range(1, 4)),
                            new Signature.Arg("side", type("Side"), "None")),
                    fn("networks", "list[Network]", "The networks of the domain.", """
                            def offline_count() -> int:
                                count = 0
                                for network in ae2f.networks():
                                    if not ae2f.online(network):
                                        count += 1
                                return count
                            """),
                    fn("online", "bool", "Whether the network is usable now.", """
                            def alarm(port: Interface, mine: Network) -> None:
                                if ae2f.online(mine):
                                    mc.set_redstone(port, 0)
                                else:
                                    mc.set_redstone(port, 15)
                            """, arg("network", "Network")),
                    fn("rule", "RuleMode", "Whether the provider shares the capability with the consumer.",
                            """
                            def sharing_storage(provider: Network, consumer: Network) -> bool:
                                return ae2f.rule(provider, consumer, "storage") != "off"
                            """, arg("provider", "Network"), arg("consumer", "Network"),
                            arg("capability", "Capability")),
                    act("set_rule", "bool", "Sets whether the provider shares the capability with the consumer.",
                            """
                            def share_power_when_full(base: Network, farm: Network) -> None:
                                if ae2.energy(base) > 100000:
                                    ae2f.set_rule(base, farm, "power", "on")
                                else:
                                    ae2f.set_rule(base, farm, "power", "off")
                            """, arg("provider", "Network"), arg("consumer", "Network"),
                            arg("capability", "Capability"), arg("mode", "RuleMode")),
                    act("set_filter", "bool", "Sets which resources pass while the rule is on.",
                            """
                            def lend_only_iron(base: Network, outpost: Network) -> None:
                                ae2f.set_rule(base, outpost, "storage", "on")
                                ae2f.set_filter(base, outpost, "storage", "allow", ["minecraft:iron_ingot"])
                            """, arg("provider", "Network"), arg("consumer", "Network"),
                            arg("capability", "Capability"), arg("mode", "FilterMode"),
                            arg("resources", "list[Resource]"))),
            "mc", List.of(
                    act("move", "bool", "Moves the resource from one block to another.", """
                            def pass_along(port_in: Interface, port_out: Interface) -> None:
                                source = ae2f.select(port_in)
                                target = ae2f.select(port_out)
                                mc.move(source, target, "minecraft:iron_ingot", 64)
                            """, arg("source", "Block"), arg("target", "Block"), arg("resource", "Resource"),
                            amount()),
                    fn("amount", "int", "How much of the resource the block holds.", """
                            def chest_has_iron(port: Interface) -> bool:
                                return mc.amount(ae2f.select(port), "minecraft:iron_ingot") > 0
                            """, arg("block", "Block"), arg("resource", "Resource")),
                    fn("storage", "list[Stack]", "What the block holds.", """
                            def chest_is_empty(port: Interface) -> bool:
                                return len(mc.storage(ae2f.select(port))) == 0
                            """, arg("block", "Block")),
                    fn("block_id", "str", "The block's ID, such as \"minecraft:chest\".", """
                            def facing_a_chest(port: Interface) -> bool:
                                return mc.block_id(ae2f.select(port)) == "minecraft:chest"
                            """, arg("block", "Block")),
                    fn("state", "Optional[str]",
                            "A block state property, such as a crop's \"age\"; None if the block has no such property.",
                            """
                            def harvest_when_ripe(port: Interface) -> None:
                                crop = ae2f.select(port)
                                if mc.state(crop, "age") == "7":
                                    mc.break_block(crop)
                            """, arg("block", "Block"), arg("property", "str")),
                    fn("redstone", "int", "The redstone signal the block receives, 0 to 15.", """
                            def lever_is_on(port: Interface) -> bool:
                                # The interface itself is a block: the signal it receives.
                                return mc.redstone(port) > 0
                            """, arg("block", "Block")),
                    act("set_redstone", "bool", "Emits redstone from the interface's front face; it stays until set again.",
                            """
                            def signal_low_iron(port: Interface) -> None:
                                if ae2.amount("minecraft:iron_ingot") < 64:
                                    mc.set_redstone(port, 15)
                                else:
                                    mc.set_redstone(port, 0)
                            """, arg("interface", "Interface"),
                            new Signature.Arg("level", type("int"), null, new Signature.Range(0, 15))),
                    act("break_block", "bool", "Breaks the block; its drops go into the domain's storage.",
                            """
                            def mine_ahead(port: Interface) -> None:
                                block = ae2f.select(port)
                                if mc.block_id(block) != "minecraft:air":
                                    mc.break_block(block)
                            """, arg("block", "Block")),
                    act("place_block", "bool", "Places the item, taken from the domain's storage, as a block.",
                            """
                            def replant(port: Interface, seed: Item) -> None:
                                ground = ae2f.select(port)
                                if mc.block_id(ground) == "minecraft:air":
                                    mc.place_block(ground, seed)
                            """, arg("block", "Block"), arg("item", "Item")),
                    fn("time", "int", "Game time in ticks.", """
                            def a_minute_since(last: int) -> bool:
                                return mc.time() - last >= 1200
                            """),
                    act("wait", "None", "Pauses the run.", """
                            def pulse(port: Interface) -> None:
                                mc.set_redstone(port, 15)
                                mc.wait(10)
                                mc.set_redstone(port, 0)
                            """, new Signature.Arg("ticks", type("int"), null, new Signature.Range(0, Long.MAX_VALUE))),
                    fn("fe", "int", "FE stored in the block.", """
                            def battery_full(port: Interface) -> bool:
                                return mc.fe(ae2f.select(port)) >= 1000000
                            """, arg("block", "Block")),
                    act("move_fe", "bool", "Moves FE from one block to another.", """
                            def charge(port_from: Interface, port_to: Interface) -> None:
                                mc.move_fe(ae2f.select(port_from), ae2f.select(port_to), 10000)
                            """, arg("source", "Block"), arg("target", "Block"), amount())));

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

    /** A library function, with a whole function showing it in use. */
    /** The language key of each library function's summary; the summary written here is the English text. */
    private static final Map<Signature, String> SUMMARY_KEYS = summaryKeys();

    private static Map<Signature, String> summaryKeys() {
        var keys = new java.util.HashMap<Signature, String>();
        NAMESPACES.forEach((namespace, functions) -> functions.forEach(function ->
                keys.put(function, "ae2federation.function." + namespace + "." + function.name())));
        return Map.copyOf(keys);
    }

    /** {@code ae2federation.function.<namespace>.<name>} for a library function; empty for anything else. */
    public static String summaryKey(Signature function) {
        return SUMMARY_KEYS.getOrDefault(function, "");
    }

    private static Signature fn(String name, String result, String summary, String example, Signature.Arg... args) {
        return new Signature(name, List.of(args), type(result), summary, false, example);
    }

    /** A function that acts on the world: it takes a tick. */
    private static Signature act(String name, String result, String summary, String example, Signature.Arg... args) {
        return new Signature(name, List.of(args), type(result), summary, true, example);
    }
}
