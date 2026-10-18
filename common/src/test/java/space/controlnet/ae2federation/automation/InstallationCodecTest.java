package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.function.ListValue;

/** Installations survive a save with their arguments and trigger. */
final class InstallationCodecTest {
    @Test
    void anInstallationComesBackAsSaved() {
        var installation = new Installation("install-abcdef", "device-k7m2qx", "iron_tools", "keep",
                Arrays.asList("minecraft:iron_ingot", 64L, 0.5, true, null,
                        new ListValue(List.of("interface-k7m2qx", 2L))),
                new Installation.Trigger.Condition("low", List.of("minecraft:iron_ingot")), 40,
                Installation.Overlap.PARALLEL);
        var loaded = InstallationCodec.load("install-abcdef", InstallationCodec.save(installation)).orElseThrow();
        assertEquals(installation.device(), loaded.device());
        assertEquals(installation.trigger(), loaded.trigger());
        assertEquals(40, loaded.interval());
        assertEquals(Installation.Overlap.PARALLEL, loaded.overlap());
        assertEquals("['minecraft:iron_ingot', 64, 0.5, True, None, ['interface-k7m2qx', 2]]",
                new ListValue(loaded.args()).toString());
    }

    @Test
    void everyTriggerKindIsKept() {
        for (var trigger : List.of(new Installation.Trigger.Manual(), new Installation.Trigger.Redstone(),
                new Installation.Trigger.Schedule(100),
                new Installation.Trigger.Event("stock_changed", List.of("minecraft:iron_ingot")))) {
            var installation = new Installation("install-abcdef", "device-k7m2qx", "f", "g", List.of(), trigger, 20,
                    Installation.Overlap.IGNORE);
            assertEquals(Optional.of(installation),
                    InstallationCodec.load("install-abcdef", InstallationCodec.save(installation)));
        }
    }

    @Test
    void aMalformedRecordIsDroppedAndTheIntervalHasAFloor() {
        assertEquals(Optional.empty(), InstallationCodec.load("install-abcdef", new CompoundTag()));
        var fast = new Installation("install-abcdef", "device-k7m2qx", "f", "g", List.of(),
                new Installation.Trigger.Manual(), 1, Installation.Overlap.IGNORE);
        assertEquals(Installation.MIN_INTERVAL, fast.interval());
        assertEquals(Optional.empty(), InstallationCodec.load("not-an-id", InstallationCodec.save(fast)));
    }
}
