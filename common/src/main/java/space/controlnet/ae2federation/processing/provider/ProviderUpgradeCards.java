package space.controlnet.ae2federation.processing.provider;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ItemLike;

/**
 * The upgrade cards the Federation Pattern Provider accepts: every card AE2 lets its own Pattern Provider install, as
 * many of each, whichever mod registered it. Addons that add an upgrade inventory to AE2's provider logic register
 * their cards for AE2's Pattern Provider only, such as Applied Flux's Induction Card; the Federation Pattern Provider's
 * owner logic carries that same inventory, keyed to its own item, so it inherits those cards here.
 */
public final class ProviderUpgradeCards {
    private ProviderUpgradeCards() {
    }

    /** Registers the inherited cards for {@code provider}; run once every mod has registered its own cards. */
    public static void inherit(ItemLike provider) {
        for (var item : BuiltInRegistries.ITEM) {
            if (!Upgrades.isUpgradeCardItem(item)) continue;
            int max = Upgrades.getMaxInstallable(item, AEBlocks.PATTERN_PROVIDER);
            if (max > 0 && Upgrades.getMaxInstallable(item, provider) == 0) {
                Upgrades.add(item, provider, max);
            }
        }
    }
}
