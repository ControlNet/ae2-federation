package space.controlnet.ae2federation.test.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * TEST-ONLY: a provider network that spans two dimensions over a real AE2 Quantum Network Bridge. In the overworld,
 * {@link PolicyBridgeFixtures}' two networks share a Bridge; the provider (outer) network's cable leads west to one
 * Quantum Bridge. Its twin is in a nether site with its own creative energy cell, as each side of a Quantum Bridge needs
 * power, and the network's only pattern provider, which holds a processing pattern for stone.
 */
public final class QuantumBridgeProjectionScene implements AutoCloseable {
    public static final BlockPos SITE_SIZE = new BlockPos(5, 2, 5);
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    /** The overworld Quantum Bridge's link chamber; its ring's east edge touches the provider network's cable. */
    private static final BlockPos OVERWORLD_CHAMBER = BASE.offset(-4, 0, -1);
    private static final BlockPos NETHER_CHAMBER = new BlockPos(1, 0, 1);
    private static final BlockPos NETHER_CELL = NETHER_CHAMBER.east(2);
    private static final BlockPos NETHER_PROVIDER = NETHER_CHAMBER.south(2);

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final OtherDimensionSite site;
    /** A random singularity frequency, so no other test's Quantum Bridge pairs with these. */
    private final long frequency;
    private int step;

    public QuantumBridgeProjectionScene(GameTestHelper helper) {
        this.helper = helper;
        frequency = QuantumBridges.randomFrequency(helper);
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells();
        site = OtherDimensionSite.nether(helper, SITE_SIZE);
    }

    /** Advances the build one step at a time; true once the nether pattern provider is on the provider network. */
    public boolean ready() {
        if (!bridge.networksSettled() || !site.ready()) return false;
        switch (step) {
            case 0 -> {
                // Every new node carries the provider network's identity, so the Quantum link merges no second one.
                var outer = bridge.outerNetwork();
                for (var cable : new BlockPos[] { BASE.north().west(), BASE.north().west(2) }) {
                    appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, null,
                            appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.BLUE));
                }
                QuantumBridges.build(OVERWORLD_CHAMBER, frequency, outer, (position, state) -> {
                    helper.setBlock(position, state);
                    return helper.getLevel().getBlockEntity(helper.absolutePos(position));
                });
                QuantumBridges.build(NETHER_CHAMBER, frequency, outer, (position, state) -> {
                    site.setBlock(position, state);
                    return site.getBlockEntity(position);
                });
                site.setBlock(NETHER_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                QuantumBridges.seed(site.getBlockEntity(NETHER_CELL), outer);
                site.setBlock(NETHER_PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                QuantumBridges.seed(site.getBlockEntity(NETHER_PROVIDER), outer);
                step = 1;
                return false;
            }
            case 1 -> {
                bridge.placeFirstBridge();
                step = 2;
                return false;
            }
            case 2 -> {
                if (!bridge.firstBridgeReady()) {
                    bridge.refreshFirstBridge();
                    return false;
                }
                if (netherProvider().getMainNode().getGrid() != providerGrid()) return false;
                netherProvider().getLogic().getPatternInv().addItems(stonePattern());
                netherProvider().getLogic().updatePatterns();
                step = 3;
                return false;
            }
            default -> {
                return providerGrid().getCraftingService().isCraftable(stone());
            }
        }
    }

    /** The consumer uses the provider's crafting, with the storage rule crafting needs, in one edit. */
    public void enableRules() {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.editAll(List.of(
                new PolicyEdit(storage(), policies.revision(storage()), PolicyRule.storageDefaults()),
                new PolicyEdit(crafting(), policies.revision(crafting()),
                        PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)))));
        if (!(result instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("Rule edit refused: " + result);
        }
    }

    public int projections() {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(crafting());
    }

    public boolean consumerCraftsStone() {
        return bridge.mainGrid().getCraftingService().isCraftable(stone());
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    private PolicyKey crafting() {
        return new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.CRAFTING);
    }

    private PolicyKey storage() {
        return new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.STORAGE);
    }

    private PatternProviderBlockEntity netherProvider() {
        return site.getBlockEntity(NETHER_PROVIDER);
    }

    private static ItemStack stonePattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(stone(), 1)));
    }

    @Override
    public void close() {
        bridge.close();
        site.close();
    }
}
