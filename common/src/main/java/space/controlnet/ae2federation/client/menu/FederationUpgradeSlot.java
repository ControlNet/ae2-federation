package space.controlnet.ae2federation.client.menu;

import appeng.api.upgrades.Upgrades;
import appeng.core.localization.GuiText;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.ArrayList;
import space.controlnet.ae2federation.processing.ProcessingRegistration;

/**
 * One of the Provider's upgrade slots, drawn as AE2 draws its own: an empty slot shows AE2's upgrade icon, and hovering
 * it lists the cards the Provider takes, as AE2's upgrade panel does ({@code UpgradesPanel.getTooltip}).
 */
public final class FederationUpgradeSlot extends ItemSlot {
    /** Keeps the {@code item-slot} type name, so style sheets style it as any other slot. */
    @Override
    public String name() {
        return "item-slot";
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (getValue().isEmpty()) {
            FederationTheme.UPGRADE_SLOT.draw(context, getPositionX() + (getSizeWidth() - 16) / 2,
                    getPositionY() + (getSizeHeight() - 16) / 2, 16, 16);
        }
    }

    @Override
    protected void onHoverTooltips(UIEvent event) {
        if (!getValue().isEmpty()) {
            super.onHoverTooltips(event);
            return;
        }
        var lines = new ArrayList<net.minecraft.network.chat.Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        lines.addAll(Upgrades.getTooltipLinesForMachine(ProcessingRegistration.PROVIDER_ITEM.get()));
        event.hoverTooltips = new HoverTooltips(lines, null, null, null);
    }
}
