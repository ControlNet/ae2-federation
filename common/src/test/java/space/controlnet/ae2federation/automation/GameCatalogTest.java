package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.TypeRef;

/** Interfaces and networks named by ID or by alias; resources passed through to the game's registries. */
final class GameCatalogTest {
    private static final List<GameCatalog.Named> INTERFACES = List.of(
            new GameCatalog.Named("interface-k7m2qx", "north_gate"),
            new GameCatalog.Named("interface-r4tw9e", ""),
            new GameCatalog.Named("interface-b2c3dd", "mine_entry"));
    private static final List<GameCatalog.Named> NETWORKS = List.of(
            new GameCatalog.Named("network-082a5c", "Main Base", "Main Base", "082a5c1e-0000-4000-8000-000000000001",
                    List.of("network-7d3e01")),
            new GameCatalog.Named("network-4f1c9d", "network-082a5c", "network-082a5c", "4f1c9d2b-0000-4000-8000-000000000002"));

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
        assertFalse(CATALOG.exists(type("Interface"), "network-082a5c"));
    }

    @Test
    void anIdShapedStringIsOnlyEverAnId() {
        assertTrue(CATALOG.exists(type("Network"), "Main Base"));
        assertTrue(CATALOG.exists(type("Network"), "network-082a5c"));
        assertFalse(CATALOG.exists(type("Network"), "network-ffffff"));
        // The second network is named like the first one's ID: the string still means the first network.
        assertEquals(List.of("network-082a5c"), CATALOG.matches(type("Network"), "network-082a5c"));
    }

    @Test
    void aNetworkIsNamedByItsIdInEitherCaseOrByItsWholeNetworkId() {
        assertEquals(List.of("network-082a5c"), CATALOG.matches(type("Network"), "network-082A5C"));
        assertEquals(List.of("network-4f1c9d"),
                CATALOG.matches(type("Network"), "4f1c9d2b-0000-4000-8000-000000000002"));
        assertEquals(List.of("network-4f1c9d"),
                CATALOG.matches(type("Network"), "4F1C9D2B-0000-4000-8000-000000000002"));
        assertEquals(List.of(), CATALOG.matches(type("Network"), "ffffffff-0000-4000-8000-000000000002"));
    }

    @Test
    void theIdOfANetworkMergedIntoAnotherNamesTheSurvivor() {
        assertEquals(List.of("network-082a5c"), CATALOG.matches(type("Network"), "network-7d3e01"));
        assertEquals(List.of("network-082a5c"), CATALOG.matches(type("Network"), "NETWORK-7D3E01"));
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
    void networksCompleteFromTheirOwnListAndShowTheTopologyName() {
        var here = List.of(new GameCatalog.Named("network-4f1c9d", "", "Network 4F1C9D", ""));
        var catalog = new GameCatalog(() -> INTERFACES, () -> NETWORKS, () -> here, RESOURCES);
        assertEquals(List.of(new Candidate("network-4f1c9d", Candidate.Kind.VALUE, "Network 4F1C9D")),
                catalog.values(type("Network"), "network-"));
        // A network that is not completed here still names a network.
        assertTrue(catalog.exists(type("Network"), "network-082a5c"));
    }

    @Test
    void resourcesComeFromTheRegistries() {
        assertEquals(Set.of("Item"), CATALOG.kinds("minecraft:iron_ingot"));
        assertEquals(Set.of(), CATALOG.kinds("minecraft:nope"));
        assertEquals(1, CATALOG.values(type("Item"), "iron").size());
        assertEquals(List.of(), CATALOG.values(type("Side"), ""), "closed sets are the core's own");
    }
}
