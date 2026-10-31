package space.controlnet.ae2federation.automation;

import appeng.api.config.AccessRestriction;
import appeng.api.config.CondenserOutput;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.FullnessMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.RelativeDirection;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.ids.AEBlockIds;
import appeng.api.ids.AEPartIds;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.LocalizationEnum;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.script.Candidate;

/**
 * AE2's device settings as programs name them: AE2's own names ({@code fuzzy_mode}, {@code redstone_controlled}) and
 * each value's enum name in lower case ({@code ignore_all}, {@code high_signal}). The names completion offers come from
 * AE2's registry, the public {@link Setting} fields of {@link Settings}, without the terminals' display preferences;
 * at run time the device's own list decides, so an addon's settings work as well (user, 2026-10-31).
 *
 * <p>To help a player match a name to the button they know, each name carries the title of AE2's button for it and
 * the AE2 devices that have it, and each value the button's line for it, as AE2's {@code SettingToggleButton} shows
 * them; all in the client's language. Addon devices are not listed, as they cannot be known without placing them.
 */
public final class DeviceSettings {
    /** The terminals' display preferences, left out: terminals are not programmed (user, 2026-10-31). */
    private static final Set<Setting<?>> TERMINAL = Set.of(Settings.SORT_BY, Settings.SORT_DIRECTION,
            Settings.VIEW_MODE, Settings.SEARCH_TOOLTIPS, Settings.TERMINAL_STYLE,
            Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, Settings.POWER_UNITS);

    /** A setting's button: its title, the line shown for each value, and the AE2 devices whose screen has it. */
    private record Button(LocalizationEnum title, Map<Enum<?>, Component> lines, List<ResourceLocation> devices) {
    }

    private static final List<Setting<?>> KNOWN = known();
    private static final Map<String, Button> BUTTONS = buttons();
    /**
     * The settings completion offers: those some AE2 device has. io_direction and level_emitter_mode stay in AE2's
     * registry with no device, so they are left out; an addon device that has them still takes them at run time
     * (user, 2026-10-31).
     */
    private static final List<Setting<?>> OFFERED = KNOWN.stream()
            .filter(setting -> !devices(setting.getName()).isEmpty()).toList();

    private DeviceSettings() {
    }

    /** The settings of AE2's registry, by name, without deprecated ones and the terminals'. */
    private static List<Setting<?>> known() {
        var found = new ArrayList<Setting<?>>();
        for (var field : Settings.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Setting.class
                    && !field.isAnnotationPresent(Deprecated.class)) {
                try {
                    var setting = (Setting<?>) field.get(null);
                    if (programmable(setting)) {
                        found.add(setting);
                    }
                } catch (IllegalAccessException unreadable) {
                    throw new IllegalStateException("AE2's setting " + field.getName() + " is not readable",
                            unreadable);
                }
            }
        }
        found.sort(Comparator.comparing(Setting::getName));
        return List.copyOf(found);
    }

    /** Whether a program may read and change the setting: all but the terminals' display preferences. */
    public static boolean programmable(Setting<?> setting) {
        return !TERMINAL.contains(setting);
    }

    /** The names completion offers, in order. */
    public static List<String> names() {
        return OFFERED.stream().map(Setting::getName).toList();
    }

    /** A setting of AE2's registry by its name, if completion offers it. */
    public static Optional<Setting<?>> known(String name) {
        return OFFERED.stream().filter(setting -> setting.getName().equals(name)).findFirst();
    }

    /** A value as programs write it: its enum name in lower case. */
    public static String spelled(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** The values the setting allows, spelled for programs, in the enum's order. */
    public static List<String> values(Setting<?> setting) {
        return setting.getValues().stream().map(DeviceSettings::spelled).toList();
    }

    /** The allowed value a program's spelling names; empty for any other text. */
    public static <T extends Enum<T>> Optional<T> parse(Setting<T> setting, String value) {
        return setting.getValues().stream().filter(allowed -> spelled(allowed).equals(value)).findFirst();
    }

    /** The language key of the title of AE2's button for the setting; empty when AE2 shows none. */
    static String titleKey(String name) {
        var button = BUTTONS.get(name);
        return button == null ? "" : button.title().getTranslationKey();
    }

    /** The IDs of the AE2 devices that have the setting, in the order their screens are listed. */
    static List<ResourceLocation> devices(String name) {
        var button = BUTTONS.get(name);
        return button == null ? List.of() : button.devices();
    }

    /**
     * The setting names that {@code prefix} finds: those whose name or button title starts with it (a title also by
     * pinyin, as {@link NameMatch#startsWith} reads it), then those containing it. Each shows its button's title, and
     * below the list the devices that have it.
     */
    public static List<Candidate> names(String prefix) {
        var typed = prefix.toLowerCase(Locale.ROOT);
        var starting = new ArrayList<Candidate>();
        var containing = new ArrayList<Candidate>();
        for (var setting : OFFERED) {
            var name = setting.getName();
            var title = title(name);
            var candidate = new Candidate(name, Candidate.Kind.VALUE, title, devicesNote(name), false, "", false);
            if (typed.isEmpty() || name.startsWith(typed) || NameMatch.startsWith(title, prefix)) {
                starting.add(candidate);
            } else if (name.contains(typed) || title.toLowerCase(Locale.ROOT).contains(typed)) {
                containing.add(candidate);
            }
        }
        starting.addAll(containing);
        return starting;
    }

    /**
     * The values of the setting {@code name} that {@code prefix} starts, in the enum's order, each with its button's
     * line; empty for a setting completion does not offer.
     */
    public static List<Candidate> values(String name, String prefix) {
        var setting = known(name);
        if (setting.isEmpty()) {
            return List.of();
        }
        var typed = prefix.toLowerCase(Locale.ROOT);
        var button = BUTTONS.get(name);
        var out = new ArrayList<Candidate>();
        for (var value : setting.get().getValues()) {
            var spelled = spelled(value);
            if (spelled.startsWith(typed)) {
                var line = button == null ? null : button.lines().get(value);
                out.add(new Candidate(spelled, Candidate.Kind.VALUE,
                        line == null ? "" : line.getString().lines().findFirst().orElse("")));
            }
        }
        return out;
    }

    private static String title(String name) {
        var button = BUTTONS.get(name);
        return button == null ? "" : Component.translatableWithFallback(button.title().getTranslationKey(),
                button.title().getEnglishText()).getString();
    }

    /** "On: ME Import Bus, ME Export Bus", in the client's language; empty without a known device. */
    private static String devicesNote(String name) {
        var names = devices(name).stream().map(id -> BuiltInRegistries.ITEM.get(id).getDescription().getString())
                .toList();
        return names.isEmpty() ? "" : Component.translatable("ae2federation.automation.setting_devices",
                String.join(", ", names)).getString();
    }

    // ---- AE2's buttons, as its SettingToggleButton and screens register them ----

    private static Map<String, Button> buttons() {
        var parts = new HashMap<String, Button>();
        button(parts, Settings.REDSTONE_CONTROLLED, ButtonToolTips.RedstoneMode,
                List.of(AEPartIds.IMPORT_BUS, AEPartIds.EXPORT_BUS, AEBlockIds.IO_PORT),
                RedstoneMode.IGNORE, ButtonToolTips.AlwaysActive, RedstoneMode.LOW_SIGNAL,
                ButtonToolTips.ActiveWithoutSignal, RedstoneMode.HIGH_SIGNAL, ButtonToolTips.ActiveWithSignal,
                RedstoneMode.SIGNAL_PULSE, ButtonToolTips.ActiveOnPulse);
        button(parts, Settings.REDSTONE_EMITTER, ButtonToolTips.RedstoneMode,
                List.of(AEPartIds.LEVEL_EMITTER, AEPartIds.ENERGY_LEVEL_EMITTER),
                RedstoneMode.LOW_SIGNAL, ButtonToolTips.EmitLevelsBelow, RedstoneMode.HIGH_SIGNAL,
                ButtonToolTips.EmitLevelAbove);
        button(parts, Settings.FUZZY_MODE, ButtonToolTips.FuzzyMode,
                List.of(AEPartIds.IMPORT_BUS, AEPartIds.EXPORT_BUS, AEPartIds.STORAGE_BUS, AEPartIds.FORMATION_PLANE,
                        AEPartIds.LEVEL_EMITTER, AEBlockIds.INTERFACE),
                FuzzyMode.PERCENT_25, ButtonToolTips.FZPercent_25, FuzzyMode.PERCENT_50, ButtonToolTips.FZPercent_50,
                FuzzyMode.PERCENT_75, ButtonToolTips.FZPercent_75, FuzzyMode.PERCENT_99, ButtonToolTips.FZPercent_99,
                FuzzyMode.IGNORE_ALL, ButtonToolTips.FZIgnoreAll);
        button(parts, Settings.CONDENSER_OUTPUT, ButtonToolTips.CondenserOutput, List.of(AEBlockIds.CONDENSER),
                CondenserOutput.TRASH, ButtonToolTips.Trash,
                CondenserOutput.MATTER_BALLS, ButtonToolTips.MatterBalls.text(CondenserOutput.MATTER_BALLS.requiredPower),
                CondenserOutput.SINGULARITY, ButtonToolTips.Singularity.text(CondenserOutput.SINGULARITY.requiredPower));
        button(parts, Settings.ACCESS, ButtonToolTips.IOMode, List.of(AEPartIds.STORAGE_BUS),
                AccessRestriction.READ, ButtonToolTips.Read, AccessRestriction.WRITE, ButtonToolTips.Write,
                AccessRestriction.READ_WRITE, ButtonToolTips.ReadWrite);
        button(parts, Settings.OPERATION_MODE, ButtonToolTips.TransferDirection, List.of(AEBlockIds.IO_PORT),
                OperationMode.FILL, ButtonToolTips.TransferToStorageCell, OperationMode.EMPTY,
                ButtonToolTips.TransferToNetwork);
        button(parts, Settings.IO_DIRECTION, ButtonToolTips.TransferDirection, List.of(),
                RelativeDirection.LEFT, ButtonToolTips.TransferToStorageCell, RelativeDirection.RIGHT,
                ButtonToolTips.TransferToNetwork);
        button(parts, Settings.FULLNESS_MODE, ButtonToolTips.OperationMode, List.of(AEBlockIds.IO_PORT),
                FullnessMode.EMPTY, ButtonToolTips.MoveWhenEmpty, FullnessMode.HALF, ButtonToolTips.MoveWhenWorkIsDone,
                FullnessMode.FULL, ButtonToolTips.MoveWhenFull);
        button(parts, Settings.BLOCKING_MODE, ButtonToolTips.InterfaceBlockingMode,
                List.of(AEBlockIds.PATTERN_PROVIDER),
                YesNo.YES, ButtonToolTips.Blocking, YesNo.NO, ButtonToolTips.NonBlocking);
        button(parts, Settings.CRAFT_ONLY, ButtonToolTips.Craft, List.of(AEPartIds.EXPORT_BUS),
                YesNo.YES, ButtonToolTips.CraftOnly, YesNo.NO, ButtonToolTips.CraftEither);
        button(parts, Settings.CRAFT_VIA_REDSTONE, ButtonToolTips.EmitterMode, List.of(AEPartIds.LEVEL_EMITTER),
                YesNo.YES, ButtonToolTips.CraftViaRedstone, YesNo.NO, ButtonToolTips.EmitWhenCrafting);
        button(parts, Settings.STORAGE_FILTER, ButtonToolTips.ReportInaccessibleItems, List.of(AEPartIds.STORAGE_BUS),
                StorageFilter.EXTRACTABLE_ONLY, ButtonToolTips.ReportInaccessibleItemsNo, StorageFilter.NONE,
                ButtonToolTips.ReportInaccessibleItemsYes);
        button(parts, Settings.PLACE_BLOCK, ButtonToolTips.BlockPlacement, List.of(AEPartIds.FORMATION_PLANE),
                YesNo.YES, ButtonToolTips.BlockPlacementYes, YesNo.NO, ButtonToolTips.BlockPlacementNo);
        button(parts, Settings.SCHEDULING_MODE, ButtonToolTips.SchedulingMode, List.of(AEPartIds.EXPORT_BUS),
                SchedulingMode.DEFAULT, ButtonToolTips.SchedulingModeDefault, SchedulingMode.ROUNDROBIN,
                ButtonToolTips.SchedulingModeRoundRobin, SchedulingMode.RANDOM, ButtonToolTips.SchedulingModeRandom);
        button(parts, Settings.OVERLAY_MODE, ButtonToolTips.OverlayMode, List.of(AEBlockIds.SPATIAL_ANCHOR),
                YesNo.NO, ButtonToolTips.OverlayModeNo, YesNo.YES, ButtonToolTips.OverlayModeYes);
        button(parts, Settings.FILTER_ON_EXTRACT, ButtonToolTips.FilterOnExtract, List.of(AEPartIds.STORAGE_BUS),
                YesNo.YES, ButtonToolTips.FilterOnExtractEnabled, YesNo.NO, ButtonToolTips.FilterOnExtractDisabled);
        button(parts, Settings.CPU_SELECTION_MODE, ButtonToolTips.CpuSelectionMode, List.of(AEBlockIds.CRAFTING_UNIT),
                CpuSelectionMode.ANY, ButtonToolTips.CpuSelectionModeAny, CpuSelectionMode.PLAYER_ONLY,
                ButtonToolTips.CpuSelectionModePlayersOnly, CpuSelectionMode.MACHINE_ONLY,
                ButtonToolTips.CpuSelectionModeAutomationOnly);
        button(parts, Settings.LOCK_CRAFTING_MODE, ButtonToolTips.LockCraftingMode,
                List.of(AEBlockIds.PATTERN_PROVIDER),
                LockCraftingMode.NONE, ButtonToolTips.LockCraftingModeNone, LockCraftingMode.LOCK_WHILE_HIGH,
                ButtonToolTips.LockCraftingWhileRedstoneHigh, LockCraftingMode.LOCK_WHILE_LOW,
                ButtonToolTips.LockCraftingWhileRedstoneLow, LockCraftingMode.LOCK_UNTIL_PULSE,
                ButtonToolTips.LockCraftingUntilRedstonePulse, LockCraftingMode.LOCK_UNTIL_RESULT,
                ButtonToolTips.LockCraftingUntilResultReturned);
        button(parts, Settings.INSCRIBER_SEPARATE_SIDES, ButtonToolTips.InscriberSideness,
                List.of(AEBlockIds.INSCRIBER),
                YesNo.YES, ButtonToolTips.InscriberSidenessSeparate, YesNo.NO, ButtonToolTips.InscriberSidenessCombined);
        button(parts, Settings.AUTO_EXPORT, ButtonToolTips.AutoExport, List.of(AEBlockIds.INSCRIBER),
                YesNo.YES, ButtonToolTips.AutoExportOn, YesNo.NO, ButtonToolTips.AutoExportOff);
        button(parts, Settings.INSCRIBER_INPUT_CAPACITY, ButtonToolTips.InscriberBufferSize,
                List.of(AEBlockIds.INSCRIBER),
                InscriberInputCapacity.SIXTY_FOUR, ButtonToolTips.InscriberBufferHigh, InscriberInputCapacity.FOUR,
                ButtonToolTips.InscriberBufferLow, InscriberInputCapacity.ONE, ButtonToolTips.InscriberBufferVeryLow);
        // Toggles of their own screens, without a line per value.
        button(parts, Settings.COPY_MODE, GuiText.CopyMode, List.of(AEBlockIds.CELL_WORKBENCH));
        button(parts, Settings.PATTERN_ACCESS_TERMINAL, GuiText.PatternAccessTerminal,
                List.of(AEBlockIds.PATTERN_PROVIDER));
        return Map.copyOf(parts);
    }

    /** Registers a button; {@code lines} pairs each value with its line, a {@link LocalizationEnum} or a text. */
    private static void button(Map<String, Button> buttons, Setting<?> setting, LocalizationEnum title,
            List<ResourceLocation> devices, Object... lines) {
        var byValue = new HashMap<Enum<?>, Component>();
        for (int i = 0; i < lines.length; i += 2) {
            var line = lines[i + 1] instanceof LocalizationEnum text
                    ? Component.translatableWithFallback(text.getTranslationKey(), text.getEnglishText())
                    : (Component) lines[i + 1];
            byValue.put((Enum<?>) lines[i], line);
        }
        buttons.put(setting.getName(), new Button(title, Map.copyOf(byValue), List.copyOf(devices)));
    }
}
