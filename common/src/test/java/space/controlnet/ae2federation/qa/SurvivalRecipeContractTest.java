package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Pins the confirmed survival recipes as written; {@code SurvivalRecipeGameTests} checks that the game loads and
 * matches them.
 */
final class SurvivalRecipeContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path DATA = ROOT.resolve("common/src/main/resources/data/ae2federation");
    private static final String PROCESSOR = "{\"item\":\"ae2federation:nexus_processor\"}";
    private static final Pattern RESULT = Pattern.compile("\"result\":\\{\"count\":(\\d+),\"id\":\"([^\"]+)\"}");

    @Test
    void everyFederationItemHasExactlyOneRecipe() throws IOException {
        var results = new TreeMap<String, Integer>();
        recipes().forEach((name, recipe) -> {
            var result = RESULT.matcher(recipe);
            assertTrue(result.find(), name);
            assertEquals(null, results.put(result.group(2), Integer.parseInt(result.group(1))), "Two recipes make " + name);
        });
        assertEquals(Map.of("ae2federation:nexus_core", 16, "ae2federation:nexus_processor", 1, "ae2federation:bridge", 1,
                "ae2federation:router", 4, "ae2federation:cable", 16, "ae2federation:pattern_provider", 1,
                "ae2federation:processing_endpoint", 1), results);
    }

    @Test
    void coreBatchIsARowOfRedstoneOverARowOfEnderDust() throws IOException {
        var recipe = recipes().get("nexus_core");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"RRR\",\"EEE\"]"), recipe);
        assertTrue(recipe.contains("\"key\":{\"E\":{\"tag\":\"c:dusts/ender_pearl\"},"
                + "\"R\":{\"tag\":\"c:dusts/redstone\"}}"), recipe);
    }

    @Test
    void processorIsPressedFromACoreEnderDustAndPrintedSiliconConsumingAll() throws IOException {
        var recipe = recipes().get("nexus_processor");
        assertTrue(recipe.contains("\"type\":\"ae2:inscriber\""));
        // "press" spends the top and bottom inputs; "inscribe" would keep them like press plates.
        assertTrue(recipe.contains("\"mode\":\"press\""));
        assertTrue(recipe.contains("\"ingredients\":{\"bottom\":{\"item\":\"ae2:printed_silicon\"},"
                + "\"middle\":{\"tag\":\"c:dusts/ender_pearl\"},\"top\":{\"item\":\"ae2federation:nexus_core\"}}"),
                "Core on top, Ender Dust in the middle, Printed Silicon at the bottom");
    }

    @Test
    void shapelessDevicesUseTheConfirmedInputs() throws IOException {
        var recipes = recipes();
        assertShapeless(recipes.get("bridge"), "{\"item\":\"ae2:storage_bus\"},{\"item\":\"ae2:quartz_fiber\"}," + PROCESSOR);
        // The block forms: AE2's ae2:interface and ae2:pattern_provider tags would also take the cable parts.
        assertShapeless(recipes.get("pattern_provider"), "{\"item\":\"ae2:pattern_provider\"}," + PROCESSOR);
        assertShapeless(recipes.get("processing_endpoint"), "{\"item\":\"ae2:interface\"}," + PROCESSOR);
    }

    @Test
    void routerBatchHasCablesInTheCornersAndTheFourBusesAroundAFederationProcessor() throws IOException {
        var recipe = recipes().get("router");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"CIC\",\"SLN\",\"CEC\"]"));
        assertTrue(recipe.contains("\"key\":{\"C\":{\"item\":\"ae2federation:cable\"},"
                + "\"E\":{\"item\":\"ae2:export_bus\"},\"I\":{\"item\":\"ae2:import_bus\"},"
                + "\"L\":" + PROCESSOR + ",\"N\":{\"item\":\"ae2:interface\"},"
                + "\"S\":{\"item\":\"ae2:storage_bus\"}}"));
    }

    @Test
    void cableBatchRingsAnyGlassCableAroundAFederationProcessor() throws IOException {
        var recipe = recipes().get("cable");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"GGG\",\"GPG\",\"GGG\"]"));
        assertTrue(recipe.contains("\"key\":{\"G\":{\"tag\":\"ae2:glass_cable\"},\"P\":" + PROCESSOR + "}"));
    }

    @Test
    void craftingTableRecipesUnlockInTheRecipeBook() throws IOException {
        var crafted = new TreeSet<String>();
        recipes().forEach((name, recipe) -> {
            if (recipe.contains("\"type\":\"minecraft:crafting_")) crafted.add("ae2federation:" + name);
        });
        var unlocked = new TreeSet<String>();
        var reward = Pattern.compile("\"rewards\":\\{\"recipes\":\\[\"([^\"]+)\"]}");
        try (Stream<Path> files = Files.walk(DATA.resolve("advancement/recipes"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var advancement = compact(file);
                assertTrue(advancement.contains("\"parent\":\"minecraft:recipes/root\""), file.toString());
                var recipe = reward.matcher(advancement);
                assertTrue(recipe.find(), file.toString());
                assertTrue(advancement.contains("{\"recipe\":\"" + recipe.group(1) + "\"}"),
                        "Unlocks once the recipe is known: " + file);
                unlocked.add(recipe.group(1));
            }
        }
        assertEquals(crafted, unlocked);
    }

    @Test
    void theCoreAndProcessorAreRegisteredNamedAndModelled() throws IOException {
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/material/MaterialRegistration.java"));
        var assets = ROOT.resolve("common/src/main/resources/assets/ae2federation");
        for (var item : Set.of("nexus_core", "nexus_processor")) {
            assertTrue(registration.contains("\"" + item + "\""), item);
            for (var language : Set.of("en_us", "zh_cn")) {
                assertTrue(Files.readString(assets.resolve("lang/" + language + ".json"))
                        .contains("\"item.ae2federation." + item + "\""), language + ": " + item);
            }
            assertTrue(compact(assets.resolve("models/item/" + item + ".json"))
                    .contains("\"layer0\":\"ae2federation:item/" + item + "\""), item);
        }
        // Both icons are still to come from the artist; until then the game draws its missing texture.
        assertFalse(registration.contains("federation_logic_processor"), "The old processor is replaced, not kept");
    }

    private static void assertShapeless(String recipe, String ingredients) {
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shapeless\""));
        assertTrue(recipe.contains("\"ingredients\":[" + ingredients + "]"), recipe);
        assertFalse(recipe.contains("\"tag\""), recipe);
    }

    private static Map<String, String> recipes() throws IOException {
        var recipes = new TreeMap<String, String>();
        try (Stream<Path> files = Files.list(DATA.resolve("recipe"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                recipes.put(file.getFileName().toString().replace(".json", ""), compact(file));
            }
        }
        return recipes;
    }

    /** The file without whitespace; the recipe files hold no strings with spaces. */
    private static String compact(Path file) throws IOException {
        return Files.readString(file).replaceAll("\\s", "");
    }
}
