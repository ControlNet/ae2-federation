package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.parts.PartHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortKind;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * Loads a structure exported by the pre-rename build (the exporter source is kept next to the sample). Its block
 * palette, block entities, AE2 cable-bus part and chest item stacks use {@code ae2federation:hub},
 * {@code ae2federation:federation_cable} and {@code ae2federation:multipart_bridge}; after load they must be the
 * Router, Cable and Bridge with their native node identities, ports and Domain relationships intact.
 */
@PrefixGameTestTemplate(false)
public final class LegacySaveImportGameTests {
    private static final String SAMPLE = "/data/ae2federation_test/legacy/legacy_save";

    private LegacySaveImportGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void legacySaveImport(GameTestHelper helper) {
        var expected = new Properties();
        var template = new StructureTemplate();
        try (InputStream structure = LegacySaveImportGameTests.class.getResourceAsStream(SAMPLE + ".nbt");
                InputStream facts = LegacySaveImportGameTests.class.getResourceAsStream(SAMPLE + ".properties")) {
            expected.load(facts);
            template.load(helper.getLevel().holderLookup(Registries.BLOCK),
                    NbtIo.readCompressed(structure, NbtAccounter.unlimitedHeap()));
        } catch (IOException exception) {
            throw new IllegalStateException("Legacy save sample is missing", exception);
        }
        var origin = helper.absolutePos(BlockPos.ZERO);
        template.placeInWorld(helper.getLevel(), origin, origin, new StructurePlaceSettings(),
                helper.getLevel().getRandom(), 2);
        var router = new BlockPos(1, 1, 1);
        var bridgePosition = new BlockPos(4, 1, 4);
        var chestPosition = new BlockPos(7, 1, 1);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(RouterRegistration.ROUTER.get(), router);
            helper.assertBlockPresent(RouterRegistration.FEDERATION_CABLE.get(), router.east());
            helper.assertBlockPresent(RouterRegistration.FEDERATION_CABLE.get(), router.east(2));
            var routerEntity = helper.<RouterBlockEntity>getBlockEntity(router);
            helper.assertValueEqual(routerEntity.binding(Direction.WEST).kind(), RouterPortKind.NATIVE_ME,
                    "Legacy Hub face ports must restore as Router native ports");
            helper.assertValueEqual(routerEntity.binding(Direction.EAST).kind(), RouterPortKind.FEDERATION,
                    "Legacy Federation Cable must reconnect to the Router");
            var part = PartHelper.getPartHost(helper.getLevel(), helper.absolutePos(bridgePosition))
                    .getPart(Direction.NORTH);
            helper.assertTrue(part instanceof MultipartBridgePart, "Legacy multipart_bridge part must load as Bridge");
            var bridge = (MultipartBridgePart) part;
            helper.assertValueEqual(bridge.operationalReason(), BridgeOperationalReason.VALID,
                    "Restored Bridge must be operational");
            var mainNetwork = network(bridge.getMainNode().getGrid());
            var outerNetwork = network(bridge.getExternalFacingNode().getGrid());
            var routerNetwork = network(GridHelper.getExposedNode(helper.getLevel(),
                    helper.absolutePos(router.west()), Direction.EAST).getGrid());
            helper.assertValueEqual(mainNetwork, expected.getProperty("bridgeMainNetwork"),
                    "Bridge main NetworkId must be restored, not regenerated");
            helper.assertValueEqual(outerNetwork, expected.getProperty("bridgeOuterNetwork"),
                    "Bridge outer NetworkId must be restored, not regenerated");
            helper.assertValueEqual(routerNetwork, expected.getProperty("hubNativeNetwork"),
                    "Router-attached NetworkId must be restored, not regenerated");
            var domains = FederationDomainRegistryAccess.get(helper.getLevel()).snapshot().federationDomains()
                    .values();
            helper.assertTrue(domains.stream().anyMatch(domain -> domain.memberships().keySet().stream()
                    .map(id -> id.value().toString()).toList().containsAll(java.util.List.of(mainNetwork, outerNetwork))),
                    "Restored Bridge must re-form its Federation Domain with both ME Networks");
            var chest = helper.<ChestBlockEntity>getBlockEntity(chestPosition);
            helper.assertTrue(chest.getItem(0).is(RouterRegistration.ROUTER_ITEM.get()) && chest.getItem(0).getCount() == 3,
                    "Legacy hub items must load as ME Federation Router items");
            helper.assertTrue(chest.getItem(1).is(RouterRegistration.FEDERATION_CABLE_ITEM.get())
                    && chest.getItem(1).getCount() == 17, "Legacy federation_cable items must load as Cable items");
            helper.assertTrue(chest.getItem(2).is(BridgeRegistration.BRIDGE.get()) && chest.getItem(2).getCount() == 5,
                    "Legacy multipart_bridge items must load as Bridge items");
            var facts = new LinkedHashMap<String, String>();
            facts.put("routerRestored", "true");
            facts.put("cableRestored", "true");
            facts.put("bridgePartRestored", "true");
            facts.put("networkIdsRestored", "true");
            facts.put("domainReformed", "true");
            facts.put("legacyItemStacksMigrated", "router=3,cable=17,bridge=5");
            facts.forEach((name, value) -> org.slf4j.LoggerFactory.getLogger(LegacySaveImportGameTests.class)
                    .info("AE2F_LEGACY_IMPORT fact={} value={}", name, value));
            writeEvidence(facts);
            helper.succeed();
        });
    }

    private static void writeEvidence(java.util.Map<String, String> facts) {
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = java.nio.file.Path.of(configured).toAbsolutePath().normalize();
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", "legacysaveimport");
        properties.setProperty("structure", "ae2federation_test:harness_native_smoke");
        properties.setProperty("assertions", "14");
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        facts.forEach(properties::setProperty);
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            try (var output = java.nio.file.Files.newOutputStream(path)) {
                properties.store(output, "AE2 Federation legacy save import evidence");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write legacy import evidence to " + path, exception);
        }
    }

    private static String network(IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow(
                () -> new net.minecraft.gametest.framework.GameTestAssertException("Waiting for settled identity"))
                .value().toString();
    }
}
