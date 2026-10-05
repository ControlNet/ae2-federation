package space.controlnet.ae2federation.compat;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.blockentity.storage.MEChestBlockEntity;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * The guide's Data Energistics observatory: three networks on one Router. A solar outpost has only ME Solar Panels and
 * the one energy cell they charge, without which no network holds the 4,000 AE the Observatory takes at once; the main
 * network has only its chest; an observatory network has an Astronomical Observatory and a Digital Storage Cell for
 * the Stellar Flux it makes. ME power pools the outpost's panels with both other networks through the main one, and
 * the main network reads the Stellar Flux through its Storage rule on the observatory. At night the Observatory turns
 * 4,000 AE/t into 8 Stellar Flux/t. The exercise switches off ME power between the main network and the observatory:
 * the Stellar Flux stops rising while the main network stays powered, and rises again once the rule is back on.
 */
final class SolarObservatoryScene implements AutoCloseable {
    private static final Direction OUTPOST = Direction.EAST;
    private static final Direction MAIN = Direction.SOUTH;
    private static final Direction OBSERVATORY = Direction.WEST;
    /** Inside the Observatory's window, from 13,000 to 23,000, with time to spare. */
    private static final long NIGHT = 14_000;
    /** Ticks the Stellar Flux is watched for, each way. */
    private static final int WATCHED = 100;

    private final GameTestHelper helper;
    private final BlockPos center;
    private final RouterFixtures routers;
    private final List<BlockPos> panels;
    private final long dayTime;
    private final Set<PolicyKey> configured = new java.util.LinkedHashSet<>();
    private int stage;
    private long mark;
    private long markTick;

    SolarObservatoryScene(GameTestHelper helper, BlockPos center) {
        this.helper = helper;
        this.center = center;
        routers = new RouterFixtures(helper);
        // A 2x3 array on the outpost's chest: a panel joins the AE network only through its bottom face, and the
        // array shares its energy, so one panel on the chest is enough. Six make 6,000 AE/t at night, the
        // Observatory's 4,000 and more.
        var base = center.relative(OUTPOST).above();
        panels = List.of(base, base.east(), base.east(2), base.south(), base.south().east(), base.south().east(2));
        dayTime = helper.getLevel().getDayTime();
    }

    void tick() {
        var level = helper.getLevel();
        switch (stage) {
            case 0 -> {
                level.setDayTime(NIGHT);
                level.setWeatherParameters(12_000, 0, false, false);
                for (var face : List.of(OUTPOST, MAIN, OBSERVATORY)) routers.placeNativeDevice(center, face);
                chest(OBSERVATORY).setCell(new ItemStack(AddonCraftingScene.item("data_energistics:digital_storage_cell_1k")));
                // An empty AE2 energy cell under the array, the only one in the scene: the panels charge it, and the
                // Observatory takes its 4,000 AE in one piece each tick, far more than the networks hold without it.
                helper.setBlock(center.relative(OUTPOST, 2), appeng.core.definitions.AEBlocks.ENERGY_CELL.block());
                for (var panel : panels) helper.setBlock(panel, AddonCraftingScene.block("data_energistics:me_solar_panel"));
                helper.setBlock(observatory(), AddonCraftingScene.block("data_energistics:astronomical_observatory"));
                stage = 1;
                helper.fail("Placed the outpost, the main network and the observatory");
            }
            case 1 -> {
                helper.assertTrue(settled(), "Waiting for every network's identity");
                routers.placeRouter(center);
                stage = 2;
                helper.fail("Placed the Router");
            }
            case 2 -> {
                var router = routers.router(center);
                helper.assertTrue(List.of(OUTPOST, MAIN, OBSERVATORY).stream().allMatch(face -> router.binding(face)
                        instanceof RouterPortBinding.Native), "Waiting for the Router's faces to join the networks");
                helper.assertValueEqual(Set.of(grid(OUTPOST), grid(MAIN), grid(OBSERVATORY)).size(), 3,
                        "Each face must be its own network");
                helper.assertTrue(settled() && sharedDomain(), "Waiting for one Federation domain");
                helper.assertFalse(grid(OBSERVATORY).getEnergyService().isNetworkPowered(),
                        "The observatory must have no power of its own");
                rule(new PolicyKey(network(MAIN), network(OUTPOST), PolicyCapability.ME_POWER), power(true));
                rule(new PolicyKey(network(OBSERVATORY), network(MAIN), PolicyCapability.ME_POWER), power(true));
                rule(new PolicyKey(network(MAIN), network(OBSERVATORY), PolicyCapability.STORAGE),
                        PolicyRule.storageDefaults());
                stage = 3;
                helper.fail("Switched on ME power and the Storage rule");
            }
            case 3 -> {
                helper.assertTrue(grid(OBSERVATORY).getEnergyService().isNetworkPowered(),
                        "Waiting for the outpost's panels to power the observatory; " + describePower());
                helper.assertTrue(flux() > 0, "Waiting for Stellar Flux in the main network's storage; the "
                        + "Observatory is " + level.getBlockState(helper.absolutePos(observatory())) + "; "
                        + describeObservatory() + "; " + describePower());
                watch(4);
            }
            case 4 -> {
                helper.assertTrue(helper.getTick() - markTick >= WATCHED, "Watching the Stellar Flux rise");
                // 8 Stellar Flux/t, less what a tick or two of rule changes may cost.
                helper.assertTrue(flux() - mark >= 8L * (WATCHED - 2), "The Observatory must make Stellar Flux every "
                        + "tick on the panels' power, but made " + (flux() - mark) + " in " + WATCHED + " ticks");
                rule(new PolicyKey(network(OBSERVATORY), network(MAIN), PolicyCapability.ME_POWER), power(false));
                stage = 5;
                helper.fail("Switched off ME power between the main network and the observatory");
            }
            case 5 -> {
                helper.assertFalse(grid(OBSERVATORY).getEnergyService().isNetworkPowered(),
                        "Waiting for the observatory to lose power");
                watch(6);
            }
            case 6 -> {
                helper.assertValueEqual(flux(), mark, "Without ME power the Observatory must make no Stellar Flux");
                helper.assertTrue(grid(MAIN).getEnergyService().isNetworkPowered(),
                        "The main network must stay on the outpost's power");
                helper.assertTrue(helper.getTick() - markTick >= WATCHED, "Watching the Stellar Flux stand still");
                rule(new PolicyKey(network(OBSERVATORY), network(MAIN), PolicyCapability.ME_POWER), power(true));
                stage = 7;
                helper.fail("Switched ME power back on");
            }
            default -> {
                helper.assertTrue(flux() > mark, "Waiting for the Stellar Flux to rise again");
                close();
            }
        }
    }

    private String describePower() {
        var text = new StringBuilder();
        for (var face : List.of(OUTPOST, MAIN, OBSERVATORY)) {
            var energy = grid(face).getEnergyService();
            text.append(face).append(": powered ").append(energy.isNetworkPowered()).append(", stored ")
                    .append(energy.getStoredPower()).append("/").append(energy.getMaxStoredPower()).append(", nodes ")
                    .append(grid(face).size()).append("; ");
        }
        return text + "panel sky " + helper.getLevel().canSeeSky(helper.absolutePos(panels.getFirst()).above())
                + ", day " + helper.getLevel().getDayTime();
    }

    private String describeObservatory() {
        var level = helper.getLevel();
        var entity = level.getBlockEntity(helper.absolutePos(observatory()));
        var node = entity instanceof appeng.blockentity.grid.AENetworkedBlockEntity host ? host.getMainNode().getNode()
                : null;
        double drawable = grid(OBSERVATORY).getEnergyService().extractAEPower(4000, appeng.api.config.Actionable.SIMULATE,
                appeng.api.config.PowerMultiplier.CONFIG);
        return "active " + (node != null && node.isActive()) + ", channels " + (node == null ? -1 : node.getUsedChannels())
                + ", on observatory grid " + (node != null && node.getGrid() == grid(OBSERVATORY)) + ", sky "
                + level.canSeeSky(helper.absolutePos(observatory()).above()) + ", thunder " + level.isThundering()
                + ", rain " + level.isRaining() + ", drawable " + drawable;
    }

    /** Notes the Stellar Flux now and moves on to {@code next}. */
    private void watch(int next) {
        mark = flux();
        markTick = helper.getTick();
        stage = next;
        helper.fail("Watching the Stellar Flux from " + mark);
    }

    /** The Stellar Flux the main network sees, all of it on the observatory's cell. */
    private long flux() {
        return grid(MAIN).getStorageService().getInventory().getAvailableStacks().get(stellarFlux());
    }

    private static AEKey stellarFlux() {
        try {
            return (AEKey) Class.forName("com.fish_dan_.data_energistics.ae2.key.StellarFluxKey").getField("INSTANCE")
                    .get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Data Energistics' Stellar Flux key is missing", exception);
        }
    }

    private BlockPos observatory() {
        return center.relative(OBSERVATORY, 2);
    }

    private static PolicyRule power(boolean enabled) {
        return PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)).withEnabled(enabled);
    }

    private MEChestBlockEntity chest(Direction face) {
        return helper.getBlockEntity(center.relative(face));
    }

    private IGrid grid(Direction face) {
        return routers.nativeDeviceNode(center, face).getGrid();
    }

    private NetworkId network(Direction face) {
        var id = FederationDomainRegistryAccess.confirmedNetworkId(grid(face));
        helper.assertTrue(id.isPresent(), "Waiting for the identity of the network on " + face);
        return id.get();
    }

    private boolean settled() {
        return List.of(OUTPOST, MAIN, OBSERVATORY).stream().allMatch(face -> grid(face) != null
                && FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).isPresent());
    }

    private boolean sharedDomain() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = new java.util.HashSet<>(registry.federationdomainsFor(network(OUTPOST)));
        common.retainAll(registry.federationdomainsFor(network(MAIN)));
        common.retainAll(registry.federationdomainsFor(network(OBSERVATORY)));
        return !common.isEmpty();
    }

    private void rule(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule " + key + " must be accepted");
        configured.add(key);
    }

    @Override
    public void close() {
        helper.getLevel().setDayTime(dayTime);
        var policies = PolicyService.get(helper.getLevel());
        for (var key : configured) {
            policies.configured(key).ifPresent(record -> policies.edit(new PolicyEdit(key, policies.revision(key),
                    record.rule().withEnabled(false))));
        }
        routers.close();
    }
}
