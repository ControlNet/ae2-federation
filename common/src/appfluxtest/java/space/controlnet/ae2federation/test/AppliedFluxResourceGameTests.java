package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import com.glodblock.github.appflux.common.AFSingletons;
import com.glodblock.github.appflux.common.me.cell.FECellHandler;
import com.glodblock.github.appflux.common.me.cell.FECellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.energy.NativeEnergyFixtures;
import space.controlnet.ae2federation.test.resources.ResourceEvidence;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

@PrefixGameTestTemplate(false)
public final class AppliedFluxResourceGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final long INSERTED = 4_294_967_311L;
    private static final long EXTRACTED = 1_073_741_829L;

    private AppliedFluxResourceGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void resourcesStoredFe(GameTestHelper helper) {
        var energy = new NativeEnergyFixtures(helper, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(energy.ready(), "Waiting for AE power storage");
            energy.charge(0, 1_000);
            var powerBefore = energy.stored(0);
            var key = FluxKey.of(EnergyType.FE);
            helper.assertTrue(key != null, "Applied Flux FE key must resolve");
            helper.assertValueEqual(key.getType(), FluxKeyType.TYPE, "FE key must use the native Flux key type");
            helper.assertValueEqual(FluxKeyType.TYPE.getId(), ResourceLocation.fromNamespaceAndPath("appflux", "flux"),
                    "Flux key type must retain its registered identifier");
            helper.assertValueEqual(AEKeyTypes.get(FluxKeyType.TYPE.getId()), FluxKeyType.TYPE,
                    "Flux key type must be registered by Applied Flux");
            var roundTrip = AEKey.fromTagGeneric(helper.getLevel().registryAccess(),
                    key.toTagGeneric(helper.getLevel().registryAccess()));
            helper.assertValueEqual(roundTrip, key, "Generic AEKey serialization must preserve FE identity");
            helper.assertValueEqual(((FluxKey) roundTrip).getEnergyType(), EnergyType.FE,
                    "Codec round-trip must preserve the native energy type");
            helper.assertTrue(FluxKeyType.TYPE.filter().matches(key), "Native Flux filter must accept FE");
            helper.assertTrue(!FluxKeyType.TYPE.filter().matches(AEItemKey.of(Items.REDSTONE)),
                    "Native Flux filter must reject item keys");
            AEKeyFilter exact = key::equals;
            helper.assertTrue(exact.matches(key) && !exact.matches(FluxKey.of(EnergyType.GTEU)),
                    "Exact filter must distinguish native energy types");

            var cellStack = new ItemStack(AFSingletons.FE_CELL_256M);
            var registeredCell = StorageCells.getCellInventory(cellStack, null);
            helper.assertTrue(registeredCell instanceof FECellInventory && FECellHandler.HANDLER.isCell(cellStack),
                    "Registered Applied Flux FE cell must provide its native inventory");
            var cell = (FECellInventory) registeredCell;
            helper.assertTrue(cell.getMaxEnergy() > INSERTED, "Registered FE cell must support long quantities");
            helper.assertValueEqual(cell.insert(key, INSERTED, Actionable.SIMULATE, SOURCE), INSERTED,
                    "FE insertion simulation must report native long capacity");
            helper.assertValueEqual(cell.getAvailableStacks().get(key), 0L, "Insertion simulation must not mutate");
            helper.assertValueEqual(cell.insert(key, INSERTED, Actionable.MODULATE, SOURCE), INSERTED,
                    "FE insertion must preserve values above Integer.MAX_VALUE");
            helper.assertValueEqual(cell.getAvailableStacks().get(key), INSERTED, "FE listing must retain long quantity");
            helper.assertValueEqual(cell.extract(key, EXTRACTED, Actionable.SIMULATE, SOURCE), EXTRACTED,
                    "FE extraction simulation must preserve long quantity");
            helper.assertValueEqual(cell.getAvailableStacks().get(key), INSERTED, "Extraction simulation must not mutate");
            helper.assertValueEqual(cell.extract(key, EXTRACTED, Actionable.MODULATE, SOURCE), EXTRACTED,
                    "FE extraction must preserve long quantity");
            var remaining = INSERTED - EXTRACTED;
            helper.assertValueEqual(cell.getAvailableStacks().get(key), remaining, "FE final quantity must be exact");
            helper.assertValueEqual(energy.stored(0), powerBefore, "FE storage must not charge or drain AE power");
            helper.assertTrue(cell.getAvailableStacks().keySet().stream().allMatch(key::equals),
                    "AE power must not synthesize another FE inventory entry");

            var facts = new LinkedHashMap<String, String>();
            facts.put("apiBinding", "direct");
            facts.put("registeredKeyType", "appflux:flux");
            facts.put("keyFactory", "FluxKey.of(EnergyType.FE)");
            facts.put("genericCodecRoundTrip", "true");
            facts.put("nativeTypeFilter", "true");
            facts.put("exactFilterIdentity", "true");
            facts.put("registeredCell", "FE_CELL_256M");
            facts.put("simulationMutation", "false");
            facts.put("inserted", Long.toString(INSERTED));
            facts.put("listed", Long.toString(INSERTED));
            facts.put("simulatedExtract", Long.toString(EXTRACTED));
            facts.put("extracted", Long.toString(EXTRACTED));
            facts.put("remaining", Long.toString(remaining));
            facts.put("quantityType", "long");
            facts.put("aboveIntegerMax", "true");
            facts.put("aePowerBefore", Long.toString(Math.round(powerBefore)));
            facts.put("aePowerAfter", Long.toString(Math.round(energy.stored(0))));
            facts.put("unitConversion", "none");
            facts.put("coupled", "false");
            ResourceEvidence.write("resourcesstoredfe", 21, facts);
            energy.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void compatNativeDifferential(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        fixtures.providerChest().setCell(new ItemStack(AFSingletons.FE_CELL_256M));
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for compatibility Federation Domain");
            }
            helper.assertTrue(fixtures.firstBridgeReady(), "Compatibility Federation Domain must be ready");
            var policyKey = PolicyLifecycleGameTests.storageKey(fixtures);
            PolicyService.get(helper.getLevel()).edit(
                    new PolicyEdit(policyKey, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            var mounts = StorageMountService.get(helper.getLevel());
            var projection = mounts.projection(policyKey);
            helper.assertTrue(projection != null, "Federation projection must mount the Applied Flux backend");
            var nativeStorage = nativeSource(fixtures.outerGrid());
            helper.assertTrue(mounts.sourceDomain(policyKey).sources().stream()
                            .anyMatch(source -> source.storage() == nativeStorage)
                            && mounts.sourceDomain(policyKey).sourceNodes().stream()
                            .anyMatch(node -> node.getOwner() == fixtures.providerChest()),
                    "Federation must retain the exact native Applied Flux chest and cell delegate");
            var key = FluxKey.of(EnergyType.FE);
            var nativeInserted = nativeStorage.insert(key, 8192, Actionable.MODULATE, SOURCE);
            var nativeExtracted = nativeStorage.extract(key, 2048, Actionable.MODULATE, SOURCE);
            var nativeRemaining = nativeStorage.getAvailableStacks().get(key);
            nativeStorage.extract(key, nativeRemaining, Actionable.MODULATE, SOURCE);
            var federationInserted = projection.insert(key, 8192, Actionable.MODULATE, SOURCE);
            var federationExtracted = projection.extract(key, 2048, Actionable.MODULATE, SOURCE);
            var federationRemaining = nativeStorage.getAvailableStacks().get(key);
            helper.assertValueEqual(federationInserted, nativeInserted, "Native and Federation insertions must match");
            helper.assertValueEqual(federationExtracted, nativeExtracted, "Native and Federation extractions must match");
            helper.assertValueEqual(federationRemaining, nativeRemaining, "Both layouts must leave identical backend state");
            helper.assertValueEqual(key.getType(), FluxKeyType.TYPE, "The actual Applied Flux key type must be retained");
            helper.assertTrue(nativeStorage == nativeSource(fixtures.outerGrid()),
                    "Both layouts must retain the same native backend identity");
            ResourceEvidence.write("compatnativedifferential", 8, Map.ofEntries(
                    Map.entry("addon", "applied_flux_2.1.4"), Map.entry("backend", "FE_CELL_256M"),
                    Map.entry("keyType", "appflux:flux"),
                    Map.entry("nativeInserted", Long.toString(nativeInserted)),
                    Map.entry("federationInserted", Long.toString(federationInserted)),
                    Map.entry("nativeExtracted", Long.toString(nativeExtracted)),
                    Map.entry("federationExtracted", Long.toString(federationExtracted)),
                    Map.entry("nativeRemaining", Long.toString(nativeRemaining)),
                    Map.entry("federationRemaining", Long.toString(federationRemaining)),
                    Map.entry("sameBackend", "true"),
                    Map.entry("nativeHook", "IStorageProvider.mountInventories")));
            fixtures.close();
        });
    }

    private static MEStorage nativeSource(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).getFirst().storage();
    }
}
