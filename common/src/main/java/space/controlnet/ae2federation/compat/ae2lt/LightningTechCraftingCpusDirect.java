package space.controlnet.ae2federation.compat.ae2lt;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/** {@link LightningTechCraftingCpus} through Lightning Tech's own types; loaded only after one of its classes was seen. */
final class LightningTechCraftingCpusDirect {
    private LightningTechCraftingCpusDirect() {
    }

    static AddonCraftingCpus.Cpu cpu() {
        return new AddonCraftingCpus.Cpu() {
            @Override
            public Object owner(ICraftingCPU cpu) {
                return ((TimeWheelCraftingCPU) cpu).getHost();
            }

            @Override
            public Object lastLink(ICraftingCPU cpu) {
                return ((TimeWheelCraftingCPU) cpu).getCraftingLogic().getLastLink();
            }

            @Override
            public long waitingFor(ICraftingCPU cpu, AEKey key) {
                return ((TimeWheelCraftingCPU) cpu).getCraftingLogic().getWaitingFor(key);
            }
        };
    }

    static AddonCraftingCpus.Anchor anchor() {
        // The full pool map includes pending-loaded CPUs and removes a CPU only after all of its persistent state is
        // drained. No state in an active loaded owner proves old jobs ended.
        return (anchor, craftingId) -> {
            var unit = (PigmeeMentalmathUnitBlockEntity) anchor;
            return unit.isCpuActive() && !unit.getTimeWheelCraftingCpuPool().hasPersistentState();
        };
    }
}
