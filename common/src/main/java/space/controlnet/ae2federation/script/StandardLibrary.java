package space.controlnet.ae2federation.script;

import java.util.List;
import java.util.Map;

/**
 * The game's standard library as the language sees it: the {@code ae2}, {@code ae2f}, {@code mc}, {@code fs} and
 * {@code net} namespaces, the
 * typed strings, the closed sets some of them hold, and the built-in records. Signatures and an example of each
 * function only; the host performs the calls. Settled with the user in rounds
 * (docs/ideas/script-system.md, "Standard library discussion").
 */
public final class StandardLibrary {
    /** Strings whose static type says what they name; completion and the checker know their values. */
    public static final List<String> TYPED_STRINGS = List.of("Item", "Fluid", "Resource", "Interface", "Network",
            "Side", "Capability", "RuleMode", "FilterMode", "HttpMethod", "CpuMode", "SettingName", "SettingValue", "RecipeKind",
            "ChannelMode", "ControllerState");

    /** Typed strings with a fixed set of values, known without the game. */
    public static final Map<String, List<String>> ENUMERATIONS = Map.of(
            "Side", List.of("up", "down", "north", "south", "west", "east"),
            "Capability", List.of("storage", "crafting", "power"),
            "RuleMode", List.of("off", "on", "reexport"),
            "FilterMode", List.of("all", "allow", "deny"),
            "HttpMethod", List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"),
            "CpuMode", List.of("any", "player_only", "machine_only"),
            "RecipeKind", List.of("crafting", "stonecutting", "smithing", "processing"),
            "ChannelMode", List.of("default", "x2", "x3", "x4", "infinite"),
            "ControllerState", List.of("none", "online", "conflict"));

    /**
     * Typed strings whose values depend on another argument of the same call: a {@code SettingValue} is one of the
     * values of the call's {@code SettingName}. Completion offers, and the checker allows, only those when that
     * argument is a literal.
     */
    public static final Map<String, String> DEPENDENT = Map.of("SettingValue", "SettingName");

    /** Values the program can hold but not build: a block selected through an interface. */
    public static final List<String> OPAQUE = List.of("Block");

    /** Built-in records and their fields, all read-only. */
    public static final Map<String, List<Signature.Arg>> RECORDS = Map.of(
            "Stack", List.of(arg("resource", "Resource"), arg("amount", "int")),
            "Response", List.of(arg("status", "int"), arg("text", "str"), arg("headers", "dict[str, str]")),
            "Cpu", List.of(arg("name", "Optional[str]"), arg("network", "Network"), arg("busy", "bool"),
                    arg("mode", "CpuMode"), arg("storage", "int"), arg("co_processors", "int"),
                    arg("job", "Optional[Stack]"), arg("progress", "int"), arg("total", "int")),
            "Setting", List.of(arg("name", "SettingName"), arg("value", "SettingValue"),
                    arg("values", "list[SettingValue]")),
            // One type for a recipe and a pattern: the slots of the crafting grid (9, row by row), the stonecutter (1),
            // the smithing table (template, base, addition) or a processing recipe's inputs, each with the stacks it
            // accepts. A processing pattern has no id or type; width and height are 0 but for a shaped crafting
            // recipe. byproducts are outputs that may not come every time, never written on a pattern; machines are
            // the items of the machines that run the recipe.
            "Recipe", List.of(arg("id", "Optional[str]"), arg("kind", "RecipeKind"), arg("type", "Optional[str]"),
                    arg("inputs", "list[list[Stack]]"), arg("outputs", "list[Stack]"),
                    arg("byproducts", "list[Stack]"), arg("machines", "list[Item]"), arg("width", "int"),
                    arg("height", "int")),
            "Provider", List.of(arg("id", "str"), arg("name", "Optional[str]"), arg("targets", "list[Item]"),
                    arg("group", "str"), arg("block", "Item"), arg("network", "Network"), arg("x", "int"),
                    arg("y", "int"), arg("z", "int"), arg("dimension", "str"), arg("slots", "int"), arg("free", "int"),
                    arg("makes", "list[Resource]"), arg("in_terminal", "bool")),
            // A network's channels as AE2's Network Status shows them; missing counts devices waiting for a channel.
            "Channels", List.of(arg("network", "Network"), arg("mode", "ChannelMode"),
                    arg("controller", "ControllerState"), arg("used", "int"), arg("missing", "int"),
                    arg("booting", "bool")),
            // A frequency of 0 is a tunnel not linked yet; linked lists the IDs at the other end.
            "Tunnel", List.of(arg("id", "str"), arg("type", "Item"), arg("name", "Optional[str]"),
                    arg("frequency", "int"), arg("output", "bool"), arg("linked", "list[str]"), arg("active", "bool"),
                    arg("network", "Network"), arg("x", "int"), arg("y", "int"), arg("z", "int"),
                    arg("dimension", "str"), arg("side", "Side")));

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
                    fn("crafting", "int",
                            "How much of the resource running and requested jobs will still put into storage.",
                            """
                            def iron_on_the_way() -> int:
                                return ae2.amount("minecraft:iron_ingot") + ae2.crafting("minecraft:iron_ingot")
                            """, arg("resource", "Resource"), network()),
                    fn("craftables", "list[Resource]", "Everything that can be crafted.", """
                            def keep_everything_stocked(target: int) -> None:
                                for resource in ae2.craftables():
                                    missing = target - ae2.amount(resource) - ae2.crafting(resource)
                                    if missing > 0:
                                        ae2.craft(resource, missing)
                            """, network()),
                    act("craft", "bool",
                            "Asks for a crafting job, on the named CPU if given; False if nothing makes the resource "
                                    + "or no CPU is free.", """
                            def keep_iron() -> None:
                                if ae2.amount("minecraft:iron_ingot") < 64:
                                    ae2.craft("minecraft:iron_ingot", 64)
                            """, arg("resource", "Resource"), amount(), network(),
                            new Signature.Arg("cpu", type("Optional[str]"), "None")),
                    act("cancel", "bool", "Cancels the jobs this installation started for the resource.", """
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
                    fn("cpus", "list[Cpu]", "The crafting CPUs and the jobs they run.", """
                            def busy_cpus() -> int:
                                count = 0
                                for cpu in ae2.cpus():
                                    if cpu.busy:
                                        count += 1
                                return count
                            """, network()),
                    fn("channels", "list[Channels]", "Each network's channels: how many are used and how many "
                            + "devices wait for one.", """
                            def short_of_channels() -> list[Network]:
                                short: list[Network] = []
                                for network in ae2.channels():
                                    if network.missing > 0:
                                        short.append(network.network)
                                return short
                            """, network()),
                    fn("free_cpus", "int", "Free crafting CPUs that take automation requests.", """
                            def craft_when_idle(item: Item, amount: int) -> None:
                                if ae2.free_cpus() > 0:
                                    ae2.craft(item, amount)
                            """, network()),
                    fn("energy", "float", "AE stored.", """
                            def low_power() -> bool:
                                return ae2.energy() < 10000
                            """, network()),
                    fn("settings", "list[Setting]", "The device's settings, each with its value and the values it takes.",
                            """
                            def setting_names(port: Interface) -> list[str]:
                                names: list[str] = []
                                for setting in ae2.settings(ae2f.select(port)):
                                    names.append(setting.name + "=" + setting.value)
                                return names
                            """, arg("block", "Block")),
                    fn("setting", "Optional[SettingValue]", "A setting's value; None if the device has no such setting.",
                            """
                            def bus_is_fuzzy(port: Interface) -> bool:
                                fuzzy = ae2.setting(ae2f.select(port), "fuzzy_mode")
                                return fuzzy is not None and fuzzy != "ignore_all"
                            """, arg("block", "Block"), arg("name", "SettingName")),
                    act("set_setting", "bool", "Changes a setting of the device, as its button in the device's screen does.",
                            """
                            def pause_bus(port: Interface, paused: bool) -> None:
                                bus = ae2f.select(port)
                                if paused:
                                    ae2.set_setting(bus, "redstone_controlled", "low_signal")
                                else:
                                    ae2.set_setting(bus, "redstone_controlled", "ignore")
                            """, arg("block", "Block"), arg("name", "SettingName"), arg("value", "SettingValue")),
                    fn("priority", "Optional[int]", "The device's priority; None if it has none.", """
                            def bus_priority(port: Interface) -> int:
                                priority = ae2.priority(ae2f.select(port))
                                if priority is None:
                                    return 0
                                return priority
                            """, arg("block", "Block")),
                    act("set_priority", "bool", "Sets the device's priority.", """
                            def prefer_bus(port: Interface) -> None:
                                ae2.set_priority(ae2f.select(port), 100)
                            """, arg("block", "Block"), new Signature.Arg("priority", type("int"), null,
                                    new Signature.Range(Integer.MIN_VALUE, Integer.MAX_VALUE))),
                    fn("config", "list[Optional[Stack]]",
                            "The device's config or filter slots in order; None for an empty slot.", """
                            def filtered(port: Interface) -> list[Resource]:
                                resources: list[Resource] = []
                                for slot in ae2.config(ae2f.select(port)):
                                    if slot is not None:
                                        resources.append(slot.resource)
                                return resources
                            """, arg("block", "Block")),
                    act("set_config", "bool",
                            "Sets a config or filter slot of the device; without a resource it empties the slot.", """
                            def stock_iron(port: Interface) -> None:
                                ae2.set_config(ae2f.select(port), 0, "minecraft:iron_ingot", 64)
                            """, arg("block", "Block"), new Signature.Arg("slot", type("int"), null,
                                    new Signature.Range(0, Long.MAX_VALUE)),
                            new Signature.Arg("resource", type("Resource"), "None"),
                            new Signature.Arg("amount", type("int"), "1", new Signature.Range(1, Long.MAX_VALUE))),
                    fn("providers", "list[Provider]", "The pattern providers, as the Pattern Access Terminal lists them.",
                            """
                            def providers_with_room() -> list[Provider]:
                                found: list[Provider] = []
                                for provider in ae2.providers():
                                    if "ae2:molecular_assembler" in provider.targets and provider.free > 0:
                                        found.append(provider)
                                return found
                            """, network()),
                    fn("provider_at", "Optional[Provider]",
                            "The pattern provider at the block; None if there is none of this domain.", """
                            def free_slots(port: Interface) -> int:
                                provider = ae2.provider_at(ae2f.select(port))
                                if provider is None:
                                    return 0
                                return provider.free
                            """, arg("block", "Block")),
                    fn("provider_patterns", "list[Recipe]", "The provider's patterns, each read as its recipe.", """
                            def written(port: Interface) -> list[str]:
                                ids: list[str] = []
                                provider = ae2.provider_at(ae2f.select(port))
                                if provider is not None:
                                    for recipe in ae2.provider_patterns(provider):
                                        if recipe.id is not None:
                                            ids.append(recipe.id)
                                return ids
                            """, arg("provider", "Provider")),
                    act("write_pattern", "bool",
                            "Writes the recipe on a blank pattern from storage and puts it in the provider; False if "
                                    + "there is no blank pattern or no free slot.", """
                            def learn(port: Interface, item: Item) -> bool:
                                provider = ae2.provider_at(ae2f.select(port))
                                if provider is None or ae2.craftable(item):
                                    return False
                                for recipe in mc.recipes_for(item):
                                    if recipe.kind == "crafting":
                                        return ae2.write_pattern(provider, recipe)
                                return False
                            """, arg("provider", "Provider"), arg("recipe", "Recipe"),
                            new Signature.Arg("items", type("dict[int, Resource]"), "{}"),
                            new Signature.Arg("substitutes", type("bool"), "False")),
                    fn("tunnels", "list[Tunnel]", "The P2P tunnels.", """
                            def unlinked() -> list[Tunnel]:
                                found: list[Tunnel] = []
                                for tunnel in ae2.tunnels():
                                    if tunnel.frequency == 0:
                                        found.append(tunnel)
                                return found
                            """, network()),
                    fn("tunnel_at", "Optional[Tunnel]",
                            "The P2P tunnel at the block; None if there is none of this domain.", """
                            def tunnel_below(port: Interface) -> Optional[Tunnel]:
                                return ae2.tunnel_at(ae2f.select(port, side="up"))
                            """, arg("block", "Block")),
                    act("link_tunnel", "bool",
                            "Links the output to the input, as a memory card does: the input gets a frequency if it "
                                    + "has none, and the output becomes the input's kind of tunnel.", """
                            def feed_from(port: Interface, source: Tunnel) -> bool:
                                machine = ae2.tunnel_at(ae2f.select(port))
                                if machine is None:
                                    return False
                                return ae2.link_tunnel(source, machine)
                            """, arg("input", "Tunnel"), arg("output", "Tunnel")),
                    act("unlink_tunnel", "bool",
                            "Unlinks the tunnel, as it was when placed; an input's outputs are left without one.", """
                            def stop_feeding(port: Interface) -> bool:
                                machine = ae2.tunnel_at(ae2f.select(port))
                                return machine is not None and ae2.unlink_tunnel(machine)
                            """, arg("tunnel", "Tunnel"))),
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
                    act("set_filter", "bool", "Sets which resources a storage rule passes while it is on.",
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
                            """, arg("source", "Block"), arg("target", "Block"), amount()),
                    fn("recipes_for", "list[Recipe]",
                            "The recipes that make the item, as AE2 can write them on patterns: crafting, "
                                    + "stonecutting, smithing and processing.", """
                            def crafting_recipes(item: Item) -> int:
                                count = 0
                                for recipe in mc.recipes_for(item):
                                    if recipe.kind == "crafting":
                                        count += 1
                                return count
                            """, arg("item", "Item"))),
            "fs", List.of(
                    fn("read", "Optional[str]",
                            "The text of a file in the world's workspace folder; None if there is none.", """
                            def saved_limit() -> int:
                                text = fs.read("limits/iron.txt")
                                if text is None:
                                    return 64
                                return int(text)
                            """, arg("path", "str")),
                    fn("exists", "bool", "Whether a file or folder is in the workspace.", """
                            def first_run() -> bool:
                                return not fs.exists("state/started.txt")
                            """, arg("path", "str")),
                    fn("list", "list[str]", "The names in a workspace folder, in order; folders end with \"/\".", """
                            def order_files() -> list[str]:
                                names: list[str] = []
                                for name in fs.list("orders"):
                                    if name.endswith(".txt"):
                                        names.append(name)
                                return names
                            """, new Signature.Arg("path", type("str"), "\"\"")),
                    act("write", "bool", "Writes the text to a workspace file, making its folders.", """
                            def save_stock() -> None:
                                fs.write("stock/iron.txt", str(ae2.amount("minecraft:iron_ingot")))
                            """, arg("path", "str"), arg("text", "str")),
                    act("append", "bool", "Adds the text to the end of a workspace file, making it if needed.", """
                            def log_iron() -> None:
                                line = str(mc.time()) + " " + str(ae2.amount("minecraft:iron_ingot")) + "\\n"
                                fs.append("logs/iron.txt", line)
                            """, arg("path", "str"), arg("text", "str")),
                    act("delete", "bool", "Deletes a workspace file or empty folder; False if there was nothing.", """
                            def take_order() -> str:
                                text = fs.read("orders/next.txt")
                                if text is None:
                                    return ""
                                fs.delete("orders/next.txt")
                                return text
                            """, arg("path", "str"))),
            "net", List.of(
                    act("http", "Response",
                            "Sends an HTTP request and gives the answer.", """
                            def report_iron(url: str) -> bool:
                                amount = ae2.amount("minecraft:iron_ingot")
                                answer = net.http("POST", url, str(amount), {"Content-Type": "text/plain"})
                                return answer.status == 200
                            """, arg("method", "HttpMethod"), arg("url", "str"),
                            new Signature.Arg("body", type("str"), "\"\""),
                            new Signature.Arg("headers", type("dict[str, str]"), "{}")),
                    act("connect", "bool",
                            "Opens a websocket connection for this installation, or keeps the open one.", """
                            def follow(url: str) -> bool:
                                return net.connected(url) or net.connect(url)
                            """, arg("url", "str")),
                    act("send", "bool", "Sends a text message on the installation's connection.", """
                            def say_stock(url: str) -> None:
                                net.connect(url)
                                net.send(url, "iron " + str(ae2.amount("minecraft:iron_ingot")))
                            """, arg("url", "str"), arg("text", "str")),
                    fn("receive", "Optional[str]", "The oldest message not read yet; None if there is none.", """
                            def obey(url: str, lamp: Interface) -> None:
                                message = net.receive(url)
                                if message == "on":
                                    mc.set_redstone(lamp, 15)
                                elif message == "off":
                                    mc.set_redstone(lamp, 0)
                            """, arg("url", "str")),
                    fn("connected", "bool", "Whether the installation's connection is open.", """
                            def keep_connected(url: str) -> None:
                                if not net.connected(url):
                                    net.connect(url)
                            """, arg("url", "str")),
                    act("disconnect", "bool", "Closes the connection; False if there was none.", """
                            def hang_up(url: str) -> bool:
                                return net.disconnect(url)
                            """, arg("url", "str"))));

    private StandardLibrary() {
    }

    /** {@code list[Stack]} and the like, as a type hint is written. */
    static TypeRef type(String text) {
        int open = text.indexOf('[');
        if (open < 0) {
            return new TypeRef(text, List.of());
        }
        // Arguments split at the commas outside any inner brackets: dict[str, list[int]].
        var args = new java.util.ArrayList<TypeRef>();
        int depth = 0;
        int start = open + 1;
        for (int i = start; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            depth += c == '[' ? 1 : c == ']' ? -1 : 0;
            if (c == ',' && depth == 0) {
                args.add(type(text.substring(start, i).strip()));
                start = i + 1;
            }
        }
        args.add(type(text.substring(start, text.length() - 1).strip()));
        return new TypeRef(text.substring(0, open), args);
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

    /**
     * The long actions: actions that span ticks (user, 2026-10-23). Each starts as the tick's one action, then the run
     * waits until it finishes, which takes no actions.
     */
    private static final java.util.Set<String> LONG_ACTIONS = java.util.Set.of("mc.wait", "net.http", "net.connect",
            "net.send");
    private static final java.util.Set<Signature> LASTING = lasting();

    private static java.util.Set<Signature> lasting() {
        var lasting = new java.util.HashSet<Signature>();
        NAMESPACES.forEach((namespace, functions) -> functions.stream()
                .filter(function -> LONG_ACTIONS.contains(namespace + "." + function.name())).forEach(lasting::add));
        return java.util.Set.copyOf(lasting);
    }

    /** Whether the function is a library long action; false for anything else. */
    public static boolean lasts(Signature function) {
        return LASTING.contains(function);
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
