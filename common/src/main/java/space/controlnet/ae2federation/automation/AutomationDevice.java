package space.controlnet.ae2federation.automation;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** A block entity with an automation ID and an alias, which the ID and alias screen shows. */
public interface AutomationDevice {
    /** The ID once the server has claimed it, such as {@code interface-k7m2qx}. */
    @Nullable String id();

    /** The alias a player gave the block in its screen; empty when it has none. */
    String alias(ServerLevel level);

    /** Sets or, with an empty text, clears the alias. */
    AutomationInterfaceBook.AliasResult setAlias(ServerLevel level, String alias);
}
