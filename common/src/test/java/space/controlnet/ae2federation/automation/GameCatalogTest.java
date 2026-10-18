package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.function.Candidate;
import space.controlnet.ae2federation.function.TypeRef;

/** Interfaces and networks named by ID or by alias; resources passed through to the game's registries. */
final class GameCatalogTest {
    private static final List<GameCatalog.Named> INTERFACES = List.of(
            new GameCatalog.Named("interface-k7m2qx", "north_gate"),
            new GameCatalog.Named("interface-r4tw9e", ""),
            new GameCatalog.Named("interface-b2c3dd", "mine_entry"));
    private static final List<GameCatalog.Named> NETWORKS = List.of(
            new GameCatalog.Named("network-p3x9ka", "Main Base"),
            new GameCatalog.Named("network-h7j8kk", "network-p3x9ka"));

    /** TEST-ONLY stand-in for the registries: one item and one fluid. */
    private static final GameCatalog.Resources RESOURCES = new GameCatalog.Resources() {
        @Override
        public Set<String> kinds(String id) {
            return switch (id) {
                case "minecraft:iron_ingot" -> Set.of("Item");
                case "minecraft:water" -> Set.of("Fluid");
                default -> Set.of();
            };
        }

        @Override
        public List<Candidate> values(String type, String prefix) {
            return type.equals("Fluid") ? List.of() : List.of(
                    new Candidate("minecraft:iron_ingot", Candidate.Kind.VALUE, "Iron Ingot"));
        }
    };

    private static final GameCatalog CATALOG = new GameCatalog(() -> INTERFACES, () -> NETWORKS, RESOURCES);

    private static TypeRef type(String name) {
        return new TypeRef(name, List.of());
    }

    @Test
    void anInterfaceIsNamedByItsIdOrItsAlias() {
        assertTrue(CATALOG.exists(type("Interface"), "north_gate"));
        assertTrue(CATALOG.exists(type("Interface"), "interface-k7m2qx"));
        assertTrue(CATALOG.exists(type("Interface"), "interface-r4tw9e"), "an interface without an alias");
        assertFalse(CATALOG.exists(type("Interface"), "south_gate"));
        assertFalse(CATALOG.exists(type("Interface"), "network-p3x9ka"));
    }

    @Test
    void anIdShapedStringIsOnlyEverAnId() {
        assertTrue(CATALOG.exists(type("Network"), "Main Base"));
        assertTrue(CATALOG.exists(type("Network"), "network-p3x9ka"));
        assertFalse(CATALOG.exists(type("Network"), "network-zzzzzz"));
        // The second network is named like the first one's ID: the string still means the first network.
        assertEquals(List.of("network-p3x9ka"), CATALOG.matches(type("Network"), "network-p3x9ka"));
    }

    @Test
    void completionListsAliasesFirstThenIds() {
        assertEquals(List.of(
                new Candidate("mine_entry", Candidate.Kind.VALUE, "interface-b2c3dd"),
                new Candidate("north_gate", Candidate.Kind.VALUE, "interface-k7m2qx"),
                new Candidate("interface-b2c3dd", Candidate.Kind.VALUE, "mine_entry"),
                new Candidate("interface-k7m2qx", Candidate.Kind.VALUE, "north_gate"),
                new Candidate("interface-r4tw9e", Candidate.Kind.VALUE, "")), CATALOG.values(type("Interface"), ""));
        assertEquals(List.of(new Candidate("north_gate", Candidate.Kind.VALUE, "interface-k7m2qx")),
                CATALOG.values(type("Interface"), "nor"));
        assertEquals(List.of(new Candidate("interface-r4tw9e", Candidate.Kind.VALUE, "")),
                CATALOG.values(type("Interface"), "interface-r"));
    }

    @Test
    void resourcesComeFromTheRegistries() {
        assertEquals(Set.of("Item"), CATALOG.kinds("minecraft:iron_ingot"));
        assertEquals(Set.of(), CATALOG.kinds("minecraft:nope"));
        assertEquals(1, CATALOG.values(type("Item"), "iron").size());
        assertEquals(List.of(), CATALOG.values(type("Side"), ""), "closed sets are the core's own");
    }
}
