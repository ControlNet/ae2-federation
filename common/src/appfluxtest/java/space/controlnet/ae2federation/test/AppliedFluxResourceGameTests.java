package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.StorageCells;
import com.glodblock.github.appflux.common.AFSingletons;
import com.glodblock.github.appflux.common.me.cell.FECellHandler;
import com.glodblock.github.appflux.common.me.cell.FECellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import java.util.LinkedHashMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.energy.NativeEnergyFixtures;
import space.controlnet.ae2federation.test.resources.ResourceEvidence;

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
}
