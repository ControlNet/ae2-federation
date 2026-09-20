package space.controlnet.ae2federation.test.mixed;

import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class MixedMachineBlockEntity extends BlockEntity {
    private static final int SLOTS = 16;
    private final ItemStackHandler inputs = new ItemStackHandler(SLOTS);
    private final ItemStackHandler outputs = new ItemStackHandler(SLOTS);
    private final IItemHandler inputHandler = new MachineInputHandler();
    private final List<ConfiguredRecipe> recipes = new ArrayList<>();
    private final Map<Object, IItemHandler> returnHandlers = new IdentityHashMap<>();
    private boolean blockedReleased;

    public MixedMachineBlockEntity(BlockPos position, BlockState state) {
        super(MixedMachineRegistration.BLOCK_ENTITY.get(), position, state);
    }

    public IItemHandler inputHandler() {
        return inputHandler;
    }

    public IItemHandler inputInventory() {
        return inputs;
    }

    public IItemHandler outputInventory() {
        return outputs;
    }

    public long inputCount(Item item) {
        long total = 0;
        for (var slot = 0; slot < inputs.getSlots(); slot++) {
            if (inputs.getStackInSlot(slot).is(item)) {
                total += inputs.getStackInSlot(slot).getCount();
            }
        }
        return total;
    }

    public void configure(List<MachineRecipe> mappings) {
        if (!recipes.isEmpty()) {
            throw new IllegalStateException("Mixed machine recipes are already configured");
        }
        for (var mapping : mappings) {
            recipes.add(new ConfiguredRecipe(mapping, mapping.returnHandler()));
            returnHandlers.put(mapping.returnOwner(), mapping.returnHandler());
        }
    }

    public void releaseBlockedRecipes() {
        blockedReleased = true;
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, MixedMachineBlockEntity machine) {
        machine.processOne();
    }

    private void processOne() {
        for (var slot = 0; slot < inputs.getSlots(); slot++) {
            var input = inputs.getStackInSlot(slot);
            if (input.isEmpty()) {
                continue;
            }
            var configured = recipes.stream().filter(candidate -> candidate.recipe.input() == input.getItem()
                    && (!candidate.recipe.blocked() || blockedReleased)).findFirst().orElse(null);
            if (configured == null) {
                continue;
            }
            var amount = input.getCount();
            var consumed = inputs.extractItem(slot, amount, false);
            var produced = new ItemStack(configured.recipe.output(), consumed.getCount());
            var outputSlot = firstOutputSlot(produced);
            var outputRemainder = outputs.insertItem(outputSlot, produced, false);
            if (!outputRemainder.isEmpty()) {
                throw new IllegalStateException("Mixed machine output inventory rejected configured recipe result");
            }
            var physicalOutput = outputs.extractItem(outputSlot, produced.getCount(), false);
            var returnRemainder = insert(configured.returnHandler, physicalOutput);
            if (!returnRemainder.isEmpty()) {
                throw new IllegalStateException("Native provider return handler rejected mixed machine output");
            }
            MixedFactoryObservation.machineTransition(this, inputHandler, outputs, configured.recipe.returnOwner(),
                    AEItemKey.of(configured.recipe.input()), AEItemKey.of(configured.recipe.output()),
                    consumed.getCount());
            setChanged();
            return;
        }
    }

    private int firstOutputSlot(ItemStack stack) {
        for (var slot = 0; slot < outputs.getSlots(); slot++) {
            if (outputs.insertItem(slot, stack, true).isEmpty()) {
                return slot;
            }
        }
        throw new IllegalStateException("Mixed machine has no output slot for configured recipe result");
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        var remainder = stack;
        for (var slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    public record MachineRecipe(Item input, Item output, boolean blocked, Object returnOwner,
            IItemHandler returnHandler) {
        public MachineRecipe {
            java.util.Objects.requireNonNull(input);
            java.util.Objects.requireNonNull(output);
            java.util.Objects.requireNonNull(returnOwner);
            java.util.Objects.requireNonNull(returnHandler);
        }
    }

    private record ConfiguredRecipe(MachineRecipe recipe, IItemHandler returnHandler) {
    }

    private final class MachineInputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return inputs.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inputs.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (recipes.stream().noneMatch(recipe -> recipe.recipe.input() == stack.getItem())) {
                return stack;
            }
            var remainder = inputs.insertItem(slot, stack, simulate);
            if (!simulate && remainder.getCount() != stack.getCount()) {
                MixedFactoryObservation.machineAccepted(MixedMachineBlockEntity.this, this,
                        AEItemKey.of(stack.getItem()), stack.getCount() - remainder.getCount());
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inputs.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return recipes.stream().anyMatch(recipe -> recipe.recipe.input() == stack.getItem());
        }
    }
}
