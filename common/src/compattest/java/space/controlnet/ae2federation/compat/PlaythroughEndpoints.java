package space.controlnet.ae2federation.compat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AEColor;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** Player-built, preprogrammed assemblers fed by real Endpoint storage-bus subnets. */
final class PlaythroughEndpoints {
    static PlaythroughRecipes.Proof processing(GameTestHelper helper, PlaythroughRecipes.Proof original) {
        var patterns = new LinkedHashMap<String, ItemStack>();
        var signatures = new LinkedHashMap<String, PlaythroughAccounting.Signature>();
        original.patterns().forEach((id, stack) -> {
            var recipe = PatternDetailsHelper.decodePattern(stack, helper.getLevel());
            if (!original.signatures().get(id).remainders().isEmpty())
                throw new IllegalArgumentException("Endpoint assembler matrix currently requires remainder-free recipes: " + id);
            var inputs = new KeyCounter();
            for (var input : recipe.getInputs()) {
                var possible = input.getPossibleInputs()[0];
                inputs.add(possible.what(), Math.multiplyExact(possible.amount(), input.getMultiplier()));
            }
            var encodedInputs = new ArrayList<GenericStack>();
            for (var input : inputs) encodedInputs.add(new GenericStack(input.getKey(), input.getLongValue()));
            var encoded = PatternDetailsHelper.encodeProcessingPattern(encodedInputs, recipe.getOutputs());
            var decoded = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
            var signature = PlaythroughAccounting.signature(decoded, helper.getLevel());
            if (!signature.equals(original.signatures().get(id))) throw new IllegalStateException("Endpoint recipe conversion changed outputs: " + id);
            patterns.put(id, encoded); signatures.put(id, signature);
        });
        return new PlaythroughRecipes.Proof(patterns, original.supplies(), original.standIns(), signatures);
    }

    /** {@code lateral} points from the hub along the factory arm, perpendicular to {@code outward}. */
    static void place(GameTestHelper helper, BlockPos provider, Direction outward, Direction lateral, boolean local, Consumer<BlockPos> track) {
        if (lateral.getAxis() == outward.getAxis()) throw new IllegalArgumentException("Endpoint bus side must be beside the provider line");
        var endpoint = provider.relative(outward);
        var assembler = endpoint.relative(outward);
        track.accept(provider); track.accept(endpoint); track.accept(assembler);
        helper.setBlock(provider, local ? AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.fromDirection(outward))
                : ProcessingRegistration.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING, outward));
        helper.setBlock(endpoint, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, outward.getOpposite()));
        helper.setBlock(assembler, AEBlocks.MOLECULAR_ASSEMBLER.block());
        // Keep the bus beside, never on, the provider/Endpoint/assembler line, and on the side away from the hub,
        // where the member chest, its power cell and the arm cannot touch it.
        for (var pos : List.of(endpoint.relative(lateral), assembler.relative(lateral))) {
            track.accept(pos);
            PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        }
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(assembler.relative(lateral)), lateral.getOpposite(), null, AEParts.STORAGE_BUS.get());
        if (local) {
            track.accept(endpoint.relative(lateral, 2));
            helper.setBlock(endpoint.relative(lateral, 2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
    }

    /** Whether the provider's executing logic, its lane for a Federation Provider, holds exactly {@code pattern}. */
    static boolean ready(GameTestHelper helper, BlockPos provider, BlockPos endpoint, boolean local, ItemStack pattern) {
        var binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(endpoint));
        if (binding == null) return false;
        var expected = AEItemKey.of(pattern);
        if (local) return binding.runtime().mode().orElse(null) instanceof EndpointModeGeneration.Local
                && holds(((PatternProviderLogicHost) helper.getBlockEntity(provider)).getLogic().getAvailablePatterns(), expected);
        var owner = helper.<FederationPatternProviderBlockEntity>getBlockEntity(provider);
        if (owner.runtime().isEmpty()) return false;
        // Toggling an Endpoint that already has a lane would unmap it.
        if (owner.laneFor(binding.endpointIdentity()).isEmpty()) {
            owner.toggleEndpoint(owner.mappedProvider().mappingHandle(0), binding);
            return false;
        }
        var lane = owner.laneFor(binding.endpointIdentity()).get();
        return holds(owner.lane(lane).getAvailablePatterns(), expected);
    }

    private static boolean holds(List<IPatternDetails> patterns, AEItemKey expected) {
        return patterns.size() == 1 && patterns.getFirst().getDefinition().equals(expected);
    }

    /** Each Endpoint subnet is its own grid: no member network or other subnet merged into it. */
    static String isolation(GameTestHelper helper, Collection<BlockPos> endpoints, Collection<IGrid> members) {
        var seen = new HashSet<IGrid>();
        for (var endpoint : endpoints) {
            var binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(endpoint));
            var subnet = binding == null ? null : binding.subnetNode().getGrid();
            if (subnet == null) return "No Endpoint subnet at " + endpoint;
            if (members.contains(subnet)) return "Endpoint subnet at " + endpoint + " merged into a member network";
            if (!seen.add(subnet)) return "Endpoint subnet at " + endpoint + " merged into another Endpoint subnet";
        }
        return "";
    }

    static void program(GameTestHelper helper, BlockPos assembler, ItemStack pattern) {
        helper.<MolecularAssemblerBlockEntity>getBlockEntity(assembler).getInternalInventory().setItemDirect(10, pattern.copy());
    }
    private PlaythroughEndpoints() { }
}
