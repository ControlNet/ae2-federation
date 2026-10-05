package space.controlnet.ae2federation.client.guide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A topology diagram as a guide page writes it in a {@code <FederationTopology>} tag: network cards on a grid, the rules
 * between them and the pairs that share energy. It describes an example build, not a live domain.
 */
public record TopologyDiagram(List<Network> networks, List<Rule> rules, List<Energy> energy) {
    public TopologyDiagram {
        networks = List.copyOf(networks);
        rules = List.copyOf(rules);
        energy = List.copyOf(energy);
    }

    /** A network card at grid cell ({@code column}, {@code row}), framed in {@code color} (0xRRGGBB). */
    public record Network(String key, String label, int color, int column, int row, List<String> details) {
        public Network {
            details = List.copyOf(details);
        }
    }

    /** "{@code user} uses {@code source}'s {@code capability}". */
    public record Rule(String user, String source, Capability capability, State state) {
    }

    /** The two networks share one energy pool. */
    public record Energy(String first, String second) {
    }

    public enum Capability {
        STORAGE, CRAFTING;

        public static Optional<Capability> parse(String value) {
            return parseEnum(Capability.class, value);
        }
    }

    /** The topology screen's chip colours: {@code active}, {@code reexport}, {@code waiting}, {@code error}. */
    public enum State {
        ACTIVE, REEXPORT, WAITING, ERROR;

        public static Optional<State> parse(String value) {
            return parseEnum(State.class, value);
        }
    }

    /** One child tag of {@code <FederationTopology>}: its name and attributes. */
    public record Element(String name, Map<String, String> attributes) {
        String attribute(String name, String fallback) {
            return attributes.getOrDefault(name, fallback);
        }
    }

    /** A diagram read from its tags, and what is wrong with them; it can be drawn only without problems. */
    public record Parsed(TopologyDiagram diagram, List<String> problems) {
    }

    /**
     * Reads {@code <Network key label color column row details>}, {@code <Rule user source capability state>} and
     * {@code <Energy first second>}; {@code details} separates its lines with "|".
     */
    public static Parsed parse(List<Element> elements) {
        var networks = new ArrayList<Network>();
        var rules = new ArrayList<Rule>();
        var energy = new ArrayList<Energy>();
        var problems = new ArrayList<String>();
        for (var element : elements) {
            switch (element.name()) {
                case "Network" -> {
                    var key = element.attribute("key", "");
                    var color = element.attribute("color", "#dddddd");
                    var column = parseInt(element.attribute("column", "0"));
                    var row = parseInt(element.attribute("row", "0"));
                    if (key.isEmpty() || !color.matches("#[0-9a-fA-F]{6}") || column == null || row == null) {
                        problems.add("A Network needs a key, a #RRGGBB color and whole column and row numbers");
                        continue;
                    }
                    var details = element.attribute("details", "");
                    networks.add(new Network(key, element.attribute("label", key), Integer.parseInt(color.substring(1), 16),
                            column, row, details.isEmpty() ? List.of() : List.of(details.split("\\|"))));
                }
                case "Rule" -> {
                    var capability = Capability.parse(element.attribute("capability", ""));
                    var state = State.parse(element.attribute("state", "active"));
                    if (capability.isEmpty() || state.isEmpty()) {
                        problems.add("A Rule needs capability storage or crafting, and a state of active, reexport, "
                                + "waiting or error");
                        continue;
                    }
                    rules.add(new Rule(element.attribute("user", ""), element.attribute("source", ""), capability.get(),
                            state.get()));
                }
                case "Energy" -> energy.add(new Energy(element.attribute("first", ""), element.attribute("second", "")));
                default -> problems.add("Unknown FederationTopology element " + element.name());
            }
        }
        var diagram = new TopologyDiagram(networks, rules, energy);
        problems.addAll(diagram.problems());
        return new Parsed(diagram, List.copyOf(problems));
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public Optional<Network> network(String key) {
        return networks.stream().filter(network -> network.key().equals(key)).findFirst();
    }

    /** What is wrong with the diagram as written, one sentence each; empty when it can be drawn. */
    public List<String> problems() {
        var problems = new ArrayList<String>();
        if (networks.isEmpty()) problems.add("A topology needs at least one Network");
        var keys = new HashSet<String>();
        var cells = new HashSet<List<Integer>>();
        for (var network : networks) {
            if (!keys.add(network.key())) problems.add("Network key \"" + network.key() + "\" is used twice");
            if (network.column() < 0 || network.row() < 0) problems.add("Network \"" + network.key() + "\" has a negative cell");
            if (!cells.add(List.of(network.column(), network.row()))) {
                problems.add("Network \"" + network.key() + "\" shares its cell with another network");
            }
        }
        var rules = new HashSet<Rule>();
        for (var rule : this.rules) {
            pair(problems, "Rule", rule.user(), rule.source());
            if (!rules.add(new Rule(rule.user(), rule.source(), rule.capability(), State.ACTIVE))) {
                problems.add("The rule " + rule.user() + " uses " + rule.source() + "'s " + rule.capability() + " is listed twice");
            }
        }
        var pools = new HashSet<List<String>>();
        for (var shared : energy) {
            pair(problems, "Energy", shared.first(), shared.second());
            var pair = shared.first().compareTo(shared.second()) < 0 ? List.of(shared.first(), shared.second())
                    : List.of(shared.second(), shared.first());
            if (!pools.add(pair)) problems.add("Energy between " + pair.get(0) + " and " + pair.get(1) + " is listed twice");
        }
        return problems;
    }

    private void pair(List<String> problems, String what, String first, String second) {
        for (var key : List.of(first, second)) {
            if (network(key).isEmpty()) problems.add(what + " names the unknown network \"" + key + "\"");
        }
        if (first.equals(second)) problems.add(what + " joins network \"" + first + "\" to itself");
    }

    private static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String value) {
        try {
            return Optional.of(Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
