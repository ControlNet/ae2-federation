package space.controlnet.ae2federation.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;

final class DirectionalEnergyTransfer {
    private DirectionalEnergyTransfer() {
    }

    static double extract(IEnergySource source, double amount, Actionable mode) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Energy amount must be finite and nonnegative");
        }
        return Math.min(amount, source.extractAEPower(amount, mode, PowerMultiplier.ONE));
    }
}
