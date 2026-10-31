package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The settings programs name: AE2's registry without the terminals' display preferences, spelled in lower case. */
final class DeviceSettingsTest {
    @Test
    void theNamesAreAe2sDeviceSettingsInOrder() {
        var names = DeviceSettings.names();
        assertTrue(names.containsAll(List.of("fuzzy_mode", "redstone_controlled", "redstone_emitter", "blocking_mode",
                "lock_crafting_mode", "access", "storage_filter", "filter_on_extract", "crafting_scheduling_mode",
                "scheduling_mode", "craft_only", "place_block", "auto_export")), names.toString());
        assertEquals(names.stream().sorted().toList(), names, "in the order of their names");
    }

    @Test
    void theTerminalsDisplayPreferencesAndDeprecatedSettingsAreLeftOut() {
        var names = DeviceSettings.names();
        for (var left : List.of("sort_by", "sort_direction", "view_mode", "search_tooltips", "terminal_style",
                "show_pattern_providers", "power_units", "inscriber_buffer_size")) {
            assertFalse(names.contains(left), left);
        }
        assertFalse(DeviceSettings.programmable(Settings.SORT_BY));
        assertTrue(DeviceSettings.programmable(Settings.FUZZY_MODE));
    }

    @Test
    void valuesAreTheSettingsOwnInLowerCase() {
        assertEquals(List.of("ignore", "low_signal", "high_signal", "signal_pulse"),
                DeviceSettings.values(Settings.REDSTONE_CONTROLLED));
        assertEquals(List.of("low_signal", "high_signal"), DeviceSettings.values(Settings.REDSTONE_EMITTER),
                "a setting that allows only some of its enum's values");
        assertEquals(List.of("ignore_all", "percent_99", "percent_75", "percent_50", "percent_25"),
                DeviceSettings.values(Settings.FUZZY_MODE), "in the enum's order");
        assertEquals("percent_25", DeviceSettings.spelled(FuzzyMode.PERCENT_25));
        assertEquals(RedstoneMode.SIGNAL_PULSE, DeviceSettings.parse(Settings.REDSTONE_CONTROLLED, "signal_pulse")
                .orElseThrow());
        assertTrue(DeviceSettings.parse(Settings.REDSTONE_EMITTER, "signal_pulse").isEmpty(),
                "a value of the enum that the setting does not allow");
        assertTrue(DeviceSettings.parse(Settings.REDSTONE_CONTROLLED, "SIGNAL_PULSE").isEmpty(),
                "only the lower-case spelling");
    }

    @Test
    void everyNameCarriesItsButtonsTitleAndTheDevicesThatHaveIt() {
        assertEquals("gui.tooltips.ae2.FuzzyMode", DeviceSettings.titleKey("fuzzy_mode"));
        assertEquals("gui.ae2.CopyMode", DeviceSettings.titleKey("copy_mode"));
        assertEquals(List.of("ae2:import_bus", "ae2:export_bus", "ae2:io_port"),
                DeviceSettings.devices("redstone_controlled").stream().map(Object::toString).toList());
        for (var name : DeviceSettings.names()) {
            assertFalse(DeviceSettings.titleKey(name).isEmpty(), name);
            assertFalse(DeviceSettings.devices(name).isEmpty(), name);
        }
    }

    /** io_direction and level_emitter_mode stay in AE2's registry, but no AE2 device has them (user, 2026-10-31). */
    @Test
    void completionLeavesOutSettingsNoAe2DeviceHas() {
        assertFalse(DeviceSettings.names().contains("io_direction"));
        assertFalse(DeviceSettings.names().contains("level_emitter_mode"));
        assertTrue(DeviceSettings.known("io_direction").isEmpty());
    }
}
