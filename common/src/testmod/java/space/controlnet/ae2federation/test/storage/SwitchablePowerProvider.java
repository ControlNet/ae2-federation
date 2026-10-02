package space.controlnet.ae2federation.test.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

/**
 * A third-party-style node that mounts one storage and is its Grid's only power source, which can be switched off. Like
 * many mods' providers it never asks AE2 to remount when its Grid loses power or reboots, so its Grid's power and
 * boot state reach Federation only through AE2's own Grid events.
 */
public final class SwitchablePowerProvider implements IStorageProvider, IAEPowerStorage {
    private static final double CAPACITY = 1_000_000;

    private final MEStorage storage;
    private boolean powered = true;

    public SwitchablePowerProvider(MEStorage storage) {
        this.storage = storage;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(storage, 0);
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        return amount;
    }

    @Override
    public double getAEMaxPower() {
        return CAPACITY;
    }

    @Override
    public double getAECurrentPower() {
        return powered ? CAPACITY : 0;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ;
    }

    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        return powered ? amount : 0;
    }
}
