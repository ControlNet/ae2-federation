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
import java.util.ArrayList;
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

    static void place(GameTestHelper helper, BlockPos provider, Direction outward, boolean local, Consumer<BlockPos> track) {
        var endpoint = provider.relative(outward);
        var assembler = endpoint.relative(outward);
        track.accept(provider); track.accept(endpoint); track.accept(assembler);
        helper.setBlock(provider, local ? AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.fromDirection(outward))
                : ProcessingRegistration.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING, outward));
        helper.setBlock(endpoint, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, outward.getOpposite()));
        helper.setBlock(assembler, AEBlocks.MOLECULAR_ASSEMBLER.block());
        // Keep the bus beside, never on, the provider/Endpoint/assembler line.
        var lateral = outward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
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

    static boolean ready(GameTestHelper helper, BlockPos provider, BlockPos endpoint, boolean local) {
        var binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(endpoint));
        if (binding == null) return false;
        if (local) return binding.runtime().mode().orElse(null) instanceof EndpointModeGeneration.Local;
        var owner = helper.<FederationPatternProviderBlockEntity>getBlockEntity(provider);
        if (owner.runtime().isEmpty()) return false;
        if (owner.laneCount() == 0) {
            String status = owner.toggleEndpoint(owner.mappedProvider().mappingHandle(0), binding);
            return status.startsWith("accepted-");
        }
        return owner.lane(0).getAvailablePatterns().size() == 1;
    }

    static void program(GameTestHelper helper, BlockPos assembler, ItemStack pattern) {
        helper.<MolecularAssemblerBlockEntity>getBlockEntity(assembler).getInternalInventory().setItemDirect(10, pattern.copy());
    }
    private PlaythroughEndpoints() { }
}
