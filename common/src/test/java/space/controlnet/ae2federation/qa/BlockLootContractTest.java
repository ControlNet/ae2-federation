package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every block of the mod drops itself when broken. */
final class BlockLootContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path RESOURCES = ROOT.resolve("common/src/main/resources");
    private static final Path BLOCKSTATES = RESOURCES.resolve("assets/ae2federation/blockstates");
    private static final Path LOOT = RESOURCES.resolve("data/ae2federation/loot_table/blocks");

    @Test
    void everyBlockHasALootTableThatDropsIt() throws IOException {
        var blocks = names(BLOCKSTATES);
        assertEquals(blocks, names(LOOT));
        for (var block : blocks) {
            var table = Files.readString(LOOT.resolve(block + ".json")).replaceAll("\\s", "");
            assertTrue(table.contains("\"name\":\"ae2federation:" + block + "\""), block);
        }
    }

    /**
     * The Neo ECO blocks exist only with Neo ECO, so their tables load only then, as their recipes do.
     */
    @Test
    void neoEcoBlocksDropOnlyWhileTheyExist() throws IOException {
        for (var block : names(LOOT)) {
            if (!block.startsWith("eco_")) continue;
            var table = Files.readString(LOOT.resolve(block + ".json")).replaceAll("\\s", "");
            assertTrue(table.contains("\"neoforge:conditions\":[{\"type\":\"neoforge:item_exists\",\"item\":"
                    + "\"ae2federation:" + block + "\"}]"), block);
        }
    }

    private static TreeSet<String> names(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            var names = new TreeSet<String>();
            files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .forEach(name -> names.add(name.substring(0, name.length() - ".json".length())));
            return names;
        }
    }
}
