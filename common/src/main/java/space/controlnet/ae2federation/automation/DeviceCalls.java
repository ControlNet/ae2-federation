package space.controlnet.ae2federation.automation;

import appeng.api.config.Setting;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.IConfigInvHost;
import appeng.helpers.IPriorityHost;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;

/**
 * The library calls that read and change a device at a selected block: its settings, its priority and its config or
 * filter slots, as the device's own screen changes them.
 *
 * <p>The device is found through AE2's interfaces only, so addon devices work as AE2's do: on a cable, the part on the
 * selected side (by default the one facing the interface); otherwise the block entity itself. The device's own
 * {@link IConfigManager} lists its settings, AE2's and an addon's, without the terminals' display preferences.
 *
 * <p>Assistant choices, as for the other block calls: a block that cannot be reached now (unloaded, its interface gone,
 * or the interface's own block) reads as empty or {@code None}, and a change to it gives False. A reachable device
 * without the setting, priority or slot asked for stops the run with a {@code ValueError} that lists what it has. A
 * change is saved with its block, and a dry run checks it without making it.
 */
final class DeviceCalls {
    private final boolean simulate;
    /** A program's resource string as an AE2 key; an unknown one stops the run. */
    private final Function<Object, AEKey> key;

    DeviceCalls(boolean simulate, Function<Object, AEKey> key) {
        this.simulate = simulate;
        this.key = key;
    }

    /** {@code ae2.settings}, {@code ae2.priority}, {@code ae2.config} and the calls that change them. */
    Object call(ProgramHost host, String name, List<Object> args) {
        var place = host.place(args.get(0)).filter(found -> !ProgramHost.own(found));
        return switch (name) {
            case "settings" -> new ListValue(place.flatMap(found -> device(found, IConfigurableObject.class))
                    .map(device -> settings(device.getConfigManager())).orElse(List.of()));
            case "setting" -> place.flatMap(found -> device(found, IConfigurableObject.class))
                    .map(IConfigurableObject::getConfigManager).flatMap(manager -> setting(manager,
                            (String) args.get(1)).map(setting -> (Object) current(manager, setting))).orElse(null);
            case "set_setting" -> place.map(found -> setSetting(found, (String) args.get(1), (String) args.get(2)))
                    .orElse(false);
            case "priority" -> place.flatMap(found -> device(found, IPriorityHost.class))
                    .map(device -> (Object) (long) device.getPriority()).orElse(null);
            case "set_priority" -> place.map(found -> setPriority(found, (Long) args.get(1))).orElse(false);
            case "config" -> new ListValue(place.flatMap(found -> device(found, IConfigInvHost.class))
                    .map(device -> config(device)).orElse(List.of()));
            case "set_config" -> place.map(found -> setConfig(found, (Long) args.get(1), args.get(2),
                    (Long) args.get(3))).orElse(false);
            default -> throw new RunError("NotImplementedError", "ae2." + name + " is not available yet");
        };
    }

    /**
     * The device of the given kind at the place: the part on the place's side of a cable, or else the block entity
     * itself.
     */
    private static <T> Optional<T> device(ProgramHost.Place place, Class<T> kind) {
        var entity = place.level().getBlockEntity(place.position());
        if (entity instanceof IPartHost parts && kind.isInstance(parts.getPart(place.side()))) {
            return Optional.of(kind.cast(parts.getPart(place.side())));
        }
        return kind.isInstance(entity) ? Optional.of(kind.cast(entity)) : Optional.empty();
    }

    /** Marks the changed block to be saved; AE2's config slots of buses do not do it themselves. */
    private static void changed(ProgramHost.Place place) {
        var entity = place.level().getBlockEntity(place.position());
        if (entity != null) {
            entity.setChanged();
        }
    }

    // ---- settings ----

    /** The settings a program may use, by name. */
    private static List<Setting<?>> programmable(IConfigManager manager) {
        return manager.getSettings().stream().filter(DeviceSettings::programmable)
                .sorted(Comparator.comparing(Setting::getName)).toList();
    }

    private static Optional<Setting<?>> setting(IConfigManager manager, String name) {
        return programmable(manager).stream().filter(setting -> setting.getName().equals(name)).findFirst();
    }

    private static List<Object> settings(IConfigManager manager) {
        var out = new ArrayList<Object>();
        for (var setting : programmable(manager)) {
            var fields = new LinkedHashMap<String, Object>();
            fields.put("name", setting.getName());
            fields.put("value", current(manager, setting));
            fields.put("values", new ListValue(new ArrayList<>(DeviceSettings.values(setting))));
            out.add(new RecordValue("Setting", fields, true));
        }
        return out;
    }

    private static <T extends Enum<T>> String current(IConfigManager manager, Setting<T> setting) {
        return DeviceSettings.spelled(manager.getSetting(setting));
    }

    private boolean setSetting(ProgramHost.Place place, String name, String value) {
        var manager = device(place, IConfigurableObject.class).map(IConfigurableObject::getConfigManager)
                .orElse(null);
        var all = manager == null ? List.<Setting<?>>of() : programmable(manager);
        var setting = all.stream().filter(each -> each.getName().equals(name)).findFirst()
                .orElseThrow(() -> new RunError("ValueError", all.isEmpty() ? "the block has no settings"
                        : "the block has no setting " + Ops.repr(name) + "; it has "
                                + String.join(", ", all.stream().map(Setting::getName).toList())));
        if (!put(manager, setting, value)) {
            throw new RunError("ValueError", name + " takes " + String.join(", ", DeviceSettings.values(setting))
                    + ", not " + Ops.repr(value));
        }
        if (!simulate) {
            changed(place);
        }
        return true;
    }

    /** Puts the value unless simulating; false when the setting does not take it. */
    private <T extends Enum<T>> boolean put(IConfigManager manager, Setting<T> setting, String value) {
        var parsed = DeviceSettings.parse(setting, value);
        if (parsed.isPresent() && !simulate) {
            manager.putSetting(setting, parsed.get());
        }
        return parsed.isPresent();
    }

    // ---- priority ----

    private boolean setPriority(ProgramHost.Place place, long priority) {
        var device = device(place, IPriorityHost.class)
                .orElseThrow(() -> new RunError("ValueError", "the block has no priority"));
        if (!simulate) {
            device.setPriority((int) priority);
            changed(place);
        }
        return true;
    }

    // ---- config and filter slots ----

    private static List<Object> config(IConfigInvHost device) {
        var slots = device.getConfig();
        var out = new ArrayList<Object>();
        for (int slot = 0; slot < slots.size(); slot++) {
            var stack = slots.getStack(slot);
            out.add(stack == null ? null : DomainCalls.stack(stack.what(), stack.amount()));
        }
        return out;
    }

    /** Fills or empties one slot; False when the slot does not take the resource, as a filter of another kind. */
    private boolean setConfig(ProgramHost.Place place, long slot, @Nullable Object resource, long amount) {
        var slots = device(place, IConfigInvHost.class)
                .orElseThrow(() -> new RunError("ValueError", "the block has no config slots")).getConfig();
        if (slot >= slots.size()) {
            throw new RunError("ValueError", "the block has " + slots.size() + " config slots, 0 to "
                    + (slots.size() - 1) + "; there is no slot " + slot);
        }
        var what = resource == null ? null : key.apply(resource);
        if (what != null && !slots.isAllowedIn((int) slot, what)) {
            return false;
        }
        if (!simulate) {
            slots.setStack((int) slot, what == null ? null : new GenericStack(what, amount));
            changed(place);
        }
        return true;
    }
}
