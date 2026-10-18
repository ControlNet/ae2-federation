package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The function system is plain Java until it is wired into the game: no Minecraft, NeoForge or AE2 types. */
final class BoundaryTest {
    @Test
    void thePackageImportsNoGameCode() throws IOException {
        var root = Path.of(System.getProperty("user.dir")).getParent()
                .resolve("common/src/main/java/space/controlnet/ae2federation/function");
        assertTrue(Files.isDirectory(root), root.toString());
        try (var files = Files.walk(root)) {
            int[] count = {0};
            files.filter(file -> file.toString().endsWith(".java")).forEach(file -> {
                count[0]++;
                try {
                    for (var line : Files.readAllLines(file)) {
                        assertFalse(line.matches("import (static )?(net\\.minecraft|net\\.neoforged|appeng|com\\.mojang)\\..*"),
                                file.getFileName() + ": " + line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(count[0] >= 10, "found " + count[0]);
        }
    }
}
