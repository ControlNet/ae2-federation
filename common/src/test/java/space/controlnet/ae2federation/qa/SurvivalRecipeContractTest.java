package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    private static final String CORE = "{\"item\":\"ae2federation:nexus_core\"}";
    private static final Pattern RESULT = Pattern.compile("\"result\":\\{\"count\":(\\d+),\"id\":\"([^\"]+)\"}");

    // Each item has one recipe, named after it; the press also has the copy every AE2 press has.
    @Test
    void everyFederationItemHasItsRecipe() throws IOException {
        var results = new TreeMap<String, String>();
        recipes().forEach((name, recipe) -> {
            var result = RESULT.matcher(recipe);
            assertTrue(result.find(), name);
            results.put(name, result.group(2) + " x" + result.group(1));
        });
        var expected = new TreeMap<String, String>(Map.of("nexus_processor_press", "ae2federation:nexus_processor_press x1",
                "nexus_processor_press_from_iron", "ae2federation:nexus_processor_press x1",
                "printed_nexus_circuit", "ae2federation:printed_nexus_circuit x1",
                "nexus_processor", "ae2federation:nexus_processor x1", "nexus_core", "ae2federation:nexus_core x2",
                "bridge", "ae2federation:bridge x1", "router", "ae2federation:router x1", "switch", "ae2federation:switch x1",
                "cable", "ae2federation:cable x8", "pattern_provider", "ae2federation:pattern_provider x1"));
        expected.put("processing_endpoint", "ae2federation:processing_endpoint x1");
        assertEquals(expected, results);
    }

    // Like Advanced AE's Quantum Press: the Engineering Press on top, the Logic Press at the bottom, both spent.
    @Test
    void pressIsPressedFromTheEngineeringAndLogicPressesAroundAnEnderPearl() throws IOException {
        var recipe = recipes().get("nexus_processor_press");
        assertTrue(recipe.contains("\"type\":\"ae2:inscriber\""));
        assertTrue(recipe.contains("\"mode\":\"press\""), "Making the press spends both AE2 presses");
        assertTrue(recipe.contains("\"ingredients\":{\"bottom\":{\"item\":\"ae2:logic_processor_press\"},"
                + "\"middle\":{\"tag\":\"c:ender_pearls\"},"
                + "\"top\":{\"item\":\"ae2:engineering_processor_press\"}}"), recipe);
    }

    // As every AE2 press: the press on top keeps, an iron block in the middle becomes another press.
    @Test
    void pressIsCopiedFromAnIronBlock() throws IOException {
        var recipe = recipes().get("nexus_processor_press_from_iron");
        assertTrue(recipe.contains("\"type\":\"ae2:inscriber\""));
        assertTrue(recipe.contains("\"mode\":\"inscribe\""), "Copying keeps the press");
        assertTrue(recipe.contains("\"ingredients\":{\"middle\":{\"item\":\"minecraft:iron_block\"},"
                + "\"top\":{\"item\":\"ae2federation:nexus_processor_press\"}}"), recipe);
    }

    // AE2's ae2:inscriber_presses tag is what meteorites' Mysterious Cubes drop; the Nexus Press is only made.
    @Test
    void pressIsNotAMeteoriteDrop() {
        assertFalse(Files.exists(ROOT.resolve("common/src/main/resources/data/ae2/tags/item/inscriber_presses.json")));
    }

    // The chain follows AE2's processors and cores: inscribe a circuit, press it into a processor, craft cores.
    @Test
    void circuitIsInscribedFromAnEnderPearlUnderTheNexusPress() throws IOException {
        var recipe = recipes().get("printed_nexus_circuit");
        assertTrue(recipe.contains("\"type\":\"ae2:inscriber\""));
        // "inscribe" keeps the press, as AE2's printed circuits do.
        assertTrue(recipe.contains("\"mode\":\"inscribe\""));
        assertTrue(recipe.contains("\"ingredients\":{\"middle\":{\"tag\":\"c:ender_pearls\"},"
                + "\"top\":{\"item\":\"ae2federation:nexus_processor_press\"}}"), recipe);
    }

    @Test
    void processorIsPressedFromTheCircuitRedstoneAndPrintedSiliconConsumingAll() throws IOException {
        var recipe = recipes().get("nexus_processor");
        assertTrue(recipe.contains("\"type\":\"ae2:inscriber\""));
        // "press" spends the top and bottom inputs; "inscribe" would keep them like press plates.
        assertTrue(recipe.contains("\"mode\":\"press\""));
        assertTrue(recipe.contains("\"ingredients\":{\"bottom\":{\"item\":\"ae2:printed_silicon\"},"
                + "\"middle\":{\"tag\":\"c:dusts/redstone\"},"
                + "\"top\":{\"item\":\"ae2federation:printed_nexus_circuit\"}}"),
                "Circuit on top, redstone in the middle, Printed Silicon at the bottom");
    }

    @Test
    void coreBatchIsAFluixCrystalEnderDustAndProcessorInARow() throws IOException {
        var recipe = recipes().get("nexus_core");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"FEP\"]"), recipe);
        assertTrue(recipe.contains("\"key\":{\"E\":{\"tag\":\"c:dusts/ender_pearl\"},"
                + "\"F\":{\"tag\":\"c:gems/fluix\"},\"P\":" + PROCESSOR + "}"), recipe);
    }

    @Test
    void shapelessDevicesUseTheConfirmedInputs() throws IOException {
        var recipes = recipes();
        assertShapeless(recipes.get("bridge"), "{\"item\":\"ae2:storage_bus\"},{\"item\":\"ae2:quartz_fiber\"}," + CORE);
        // The block forms: AE2's ae2:interface and ae2:pattern_provider tags would also take the cable parts.
        assertShapeless(recipes.get("pattern_provider"), "{\"item\":\"ae2:pattern_provider\"}," + CORE);
        assertShapeless(recipes.get("processing_endpoint"), "{\"item\":\"ae2:interface\"}," + CORE);
    }

    // What a Switch face exchanges, as on the Bridge: storage (Storage Bus), crafting and processing (ME Interface)
    // and energy without channels (Quartz Fiber).
    @Test
    void switchHasCablesInTheCornersQuartzFibersStorageBusAndInterfaceAroundANexusCore() throws IOException {
        var recipe = recipes().get("switch");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"CQC\",\"SLN\",\"CQC\"]"), recipe);
        assertTrue(recipe.contains("\"key\":{\"C\":{\"item\":\"ae2federation:cable\"},"
                + "\"L\":" + CORE + ",\"N\":{\"item\":\"ae2:interface\"},"
                + "\"Q\":{\"item\":\"ae2:quartz_fiber\"},\"S\":{\"item\":\"ae2:storage_bus\"}}"), recipe);
    }

    @Test
    void routerBatchHasCablesInTheCornersAndFluixAroundANexusCore() throws IOException {
        var recipe = recipes().get("router");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"CFC\",\"FLF\",\"CFC\"]"), recipe);
        assertTrue(recipe.contains("\"key\":{\"C\":{\"item\":\"ae2federation:cable\"},"
                + "\"F\":{\"tag\":\"c:gems/fluix\"},\"L\":" + CORE + "}"), recipe);
    }

    @Test
    void cableBatchRingsAnyGlassCableAroundANexusCore() throws IOException {
        var recipe = recipes().get("cable");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"GGG\",\"GPG\",\"GGG\"]"));
        assertTrue(recipe.contains("\"key\":{\"G\":{\"tag\":\"ae2:glass_cable\"},\"P\":" + CORE + "}"));
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
    void theChainItemsAreRegisteredNamedAndModelled() throws IOException {
        var registration = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/material/MaterialRegistration.java"));
        var assets = ROOT.resolve("common/src/main/resources/assets/ae2federation");
        for (var item : Set.of("nexus_core", "nexus_processor", "printed_nexus_circuit", "nexus_processor_press")) {
            assertTrue(registration.contains("\"" + item + "\""), item);
            for (var language : Set.of("en_us", "zh_cn")) {
                assertTrue(Files.readString(assets.resolve("lang/" + language + ".json"))
                        .contains("\"item.ae2federation." + item + "\""), language + ": " + item);
            }
            assertTrue(compact(assets.resolve("models/item/" + item + ".json"))
                    .contains("\"layer0\":\"ae2federation:item/" + item + "\""), item);
            assertTrue(Files.isRegularFile(assets.resolve("textures/item/" + item + ".png")), item + " icon");
        }
        assertFalse(registration.contains("federation_logic_processor"), "The old processor is replaced, not kept");
        assertFalse(Files.exists(assets.resolve("models/item/federation_logic_processor.json")),
                "The old processor's model goes with it");
    }

    /**
     * Named like AE2's Inscriber Logic Press, Printed Logic Circuit, Logic Processor and Formation Core (逻辑压印模板,
     * 逻辑电路板, 逻辑处理器, 成型核心).
     */
    @Test
    void chainItemsAreNamedAfterTheirAe2Counterparts() throws IOException {
        var lang = ROOT.resolve("common/src/main/resources/assets/ae2federation/lang");
        var english = Files.readString(lang.resolve("en_us.json"));
        var chinese = Files.readString(lang.resolve("zh_cn.json"));
        Map.of("nexus_processor_press", List.of("Inscriber Nexus Press", "联结压印模板"),
                "printed_nexus_circuit", List.of("Printed Nexus Circuit", "联结电路板"),
                "nexus_processor", List.of("Nexus Processor", "联结处理器"),
                "nexus_core", List.of("Nexus Core", "联结核心")).forEach((item, names) -> {
            assertTrue(english.contains("\"item.ae2federation." + item + "\": \"" + names.get(0) + "\""), item);
            assertTrue(chinese.contains("\"item.ae2federation." + item + "\": \"" + names.get(1) + "\""), item);
        });
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
