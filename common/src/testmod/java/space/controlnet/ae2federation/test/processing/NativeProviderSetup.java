package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.core.definitions.AEBlocks;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;

final class NativeProviderSetup {
    private static final IGridNodeListener<NativeProviderLaneFixtures> LISTENER = (owner, node) -> {
    };
    private static final BlockPos ENERGY_POS = NativeProviderLaneFixtures.HOST_POS.west();
    private static final BlockPos ISOLATED_ENERGY_POS = NativeProviderLaneFixtures.HOST_POS.west(2);

    private NativeProviderSetup() {
    }

    static State create(GameTestHelper helper, NativeProviderLaneFixtures owner, List<IntPredicate> assignments,
            boolean isolatedPower, int patternSlots, NetworkId networkId, List<Direction> targets,
            BlockPos hostPosition) {
        boolean remoteHost = !hostPosition.equals(NativeProviderLaneFixtures.HOST_POS);
        helper.setBlock(hostPosition, remoteHost ? AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST) : Blocks.CHEST.defaultBlockState());
        if (!remoteHost) helper.setBlock(NativeProviderLaneFixtures.TARGET_POS, Blocks.CHEST);
        var energyPosition = isolatedPower ? ISOLATED_ENERGY_POS : ENERGY_POS;
        if (isolatedPower && !remoteHost) {
            helper.setBlock(ENERGY_POS, Blocks.AIR);
        }
        if (!remoteHost) helper.setBlock(energyPosition, AEBlocks.CREATIVE_ENERGY_CELL.block());
        if (networkId != null && !isolatedPower && !remoteHost) {
            helper.<CreativeEnergyCellBlockEntity>getBlockEntity(energyPosition).getMainNode()
                    .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        }
        var node = remoteHost ? helper.<PatternProviderBlockEntity>getBlockEntity(hostPosition).getMainNode()
                : GridHelper.createManagedNode(owner, LISTENER)
                .setTagName("provider")
                .setInWorldNode(true)
                .setIdlePowerUsage(0)
                .setExposedOnSides(EnumSet.of(Direction.WEST));
        if (networkId != null) {
            node.loadFromNBT(NetworkIdentityNodeSeed.managedNode("provider", networkId));
        }
        if (isolatedPower && !remoteHost) {
            node.addService(IAEPowerStorage.class,
                    helper.<CreativeEnergyCellBlockEntity>getBlockEntity(energyPosition));
        }
        var mutableHosts = new ArrayList<NativeProviderLaneHost>(assignments.size());
        for (int index = 0; index < assignments.size(); index++) {
            mutableHosts.add(new NativeProviderLaneHost(helper, hostPosition, targets.get(index)));
        }
        var hosts = List.copyOf(mutableHosts);
        var composition = new MappedPatternProvider(node, owner, hosts, patternSlots);
        for (int index = 0; index < hosts.size(); index++) {
            hosts.get(index).setLogic(composition.lanes().get(index));
        }
        for (int slot = 0; slot < patternSlots; slot++) {
            var assignedLanes = new java.util.TreeSet<Integer>();
            for (int lane = 0; lane < assignments.size(); lane++) {
                if (assignments.get(lane).test(slot)) {
                    assignedLanes.add(lane);
                }
            }
            composition.replaceMapping(composition.mappingHandle(slot), assignedLanes);
        }
        if (!remoteHost) node.create(helper.getLevel(), helper.absolutePos(hostPosition));
        return new State(node, composition, hosts, remoteHost ? null : energyPosition,
                !isolatedPower && !remoteHost);
    }

    record State(IManagedGridNode node, MappedPatternProvider composition, List<NativeProviderLaneHost> hosts,
            BlockPos energyPosition, boolean explicitEnergyConnection) {
    }
}
