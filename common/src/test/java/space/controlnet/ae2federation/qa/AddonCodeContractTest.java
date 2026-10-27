package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Code for one optional mod lives in {@code compat/<mod id>}: the rest names only Java, Minecraft, NeoForge, AE2 and the
 * libraries the mod requires, by import or by class name in a string, and never asks whether a mod is loaded by its id.
 * Comments may name anything, such as the source of a borrowed technique. No class sits in {@code compat/} itself: the
 * compatibility test mod owns that package, and a module system refuses a package split between two mods.
 */
final class AddonCodeContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final List<Path> SOURCES = List.of(ROOT.resolve("common/src/main/java"),
            ROOT.resolve("neoforge-1.21.1/src/main/java"));
    private static final String COMPAT = "space/controlnet/ae2federation/compat/";
    /** Reverse-domain packages of the platform and the required libraries, by their first two segments. */
    private static final Set<String> REQUIRED = Set.of("net.minecraft", "net.neoforged", "com.mojang", "com.lowdragmc",
            "dev.vfyjxf", "com.llamalad7", "org.spongepowered", "org.jetbrains", "org.joml", "org.lwjgl", "org.slf4j",
            "com.google", "it.unimi");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+)");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    /** A reverse-domain name: a first segment of two or three letters, such as {@code com} or {@code cn}. */
    private static final Pattern DOMAIN = Pattern.compile("^([a-z]{2,3}\\.[a-z0-9_]+)\\.");
    private static final Pattern MOD_CHECK = Pattern.compile("isLoaded\\(\\s*\"");
    private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    @Test
    void onlyCompatNamesOptionalMods() throws IOException {
        var found = new ArrayList<String>();
        for (var root : SOURCES) {
            try (Stream<Path> files = Files.walk(root)) {
                for (var file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    var relative = root.relativize(file).toString().replace('\\', '/');
                    if (relative.startsWith(COMPAT)) {
                        if (relative.indexOf('/', COMPAT.length()) < 0) {
                            found.add(relative + ": in the test mod's package");
                        }
                        continue;
                    }
                    var code = COMMENT.matcher(Files.readString(file)).replaceAll(" ");
                    var imports = IMPORT.matcher(code);
                    while (imports.find()) optional(imports.group(1), relative, found);
                    var strings = STRING.matcher(code);
                    while (strings.find()) optional(strings.group(1), relative, found);
                    if (MOD_CHECK.matcher(code).find()) found.add(relative + ": checks a mod id");
                }
            }
        }
        assertEquals(List.of(), found, "move code for optional mods to " + COMPAT + "<mod id>");
    }

    private static void optional(String name, String file, List<String> found) {
        var domain = DOMAIN.matcher(name);
        if (domain.find() && !REQUIRED.contains(domain.group(1))) found.add(file + ": " + name);
    }
}
