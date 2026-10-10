package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/**
 * Test double for fault injection: an ME storage whose simulated and real inserts take at most set amounts, so a
 * simulated insert can promise more than the real one then takes, or a storage lets stock out but never takes any in,
 * as an extract-only storage bus does. Stock put in with {@link #put} bypasses the insert limits.
 */
public final class FaultStorage implements MEStorage {
    private final KeyCounter stock = new KeyCounter();
    private final long simulated;
    private long real;

    /**
     * @param simulated the most any simulated insert takes
     * @param real the most all real inserts together take
     */
    public FaultStorage(long simulated, long real) {
        this.simulated = simulated;
        this.real = real;
    }

    /** A storage that takes no stock in at all, only lets it out. */
    public static FaultStorage extractOnly() {
        return new FaultStorage(0, 0);
    }

    public void put(AEKey what, long amount) {
        stock.add(what, amount);
    }

    public long held(AEKey what) {
        return stock.get(what);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        long taken = Math.min(amount, mode == Actionable.SIMULATE ? simulated : real);
        if (taken <= 0) return 0;
        if (mode == Actionable.MODULATE) {
            stock.add(what, taken);
            real -= taken;
        }
        return taken;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        long taken = Math.min(amount, stock.get(what));
        if (taken > 0 && mode == Actionable.MODULATE) stock.remove(what, taken);
        return taken;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        out.addAll(stock);
    }

    @Override
    public Component getDescription() {
        return Component.literal("Fault-injection test storage");
    }
}
