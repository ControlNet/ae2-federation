package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import space.controlnet.ae2federation.script.ListValue;

/** Installations in NBT, with their argument values: None, bool, int, float, str and lists of them. */
public final class InstallationCodec {
    private InstallationCodec() {
    }

    public static CompoundTag save(Installation installation) {
        var tag = new CompoundTag();
        tag.putString("device", installation.device());
        tag.putString("file", installation.file());
        tag.putString("function", installation.function());
        tag.put("args", values(installation.args()));
        tag.put("trigger", trigger(installation.trigger()));
        tag.putInt("interval", installation.interval());
        tag.putString("overlap", installation.overlap().name().toLowerCase(Locale.ROOT));
        return tag;
    }

    /** The installation as saved; empty when the record is malformed, so one bad record does not fail the save. */
    public static Optional<Installation> load(String id, CompoundTag tag) {
        try {
            var trigger = trigger(tag.getCompound("trigger"));
            if (!ShortId.valid(Installation.PREFIX, id) || trigger.isEmpty() || tag.getString("file").isEmpty()
                    || tag.getString("function").isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Installation(id, tag.getString("device"), tag.getString("file"),
                    tag.getString("function"), values(tag.getList("args", Tag.TAG_COMPOUND)), trigger.get(),
                    tag.getInt("interval"),
                    Installation.Overlap.valueOf(tag.getString("overlap").toUpperCase(Locale.ROOT))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static CompoundTag trigger(Installation.Trigger trigger) {
        var tag = new CompoundTag();
        switch (trigger) {
            case Installation.Trigger.Manual manual -> tag.putString("kind", "manual");
            case Installation.Trigger.Redstone redstone -> tag.putString("kind", "redstone");
            case Installation.Trigger.Schedule schedule -> {
                tag.putString("kind", "schedule");
                tag.putInt("ticks", schedule.ticks());
            }
            case Installation.Trigger.Condition condition -> {
                tag.putString("kind", "condition");
                tag.putString("function", condition.function());
                tag.put("args", values(condition.args()));
            }
            case Installation.Trigger.Event event -> {
                tag.putString("kind", "event");
                tag.putString("name", event.name());
                tag.put("args", values(event.args()));
            }
        }
        return tag;
    }

    private static Optional<Installation.Trigger> trigger(CompoundTag tag) {
        return Optional.ofNullable(switch (tag.getString("kind")) {
            case "manual" -> new Installation.Trigger.Manual();
            case "redstone" -> new Installation.Trigger.Redstone();
            case "schedule" -> new Installation.Trigger.Schedule(tag.getInt("ticks"));
            case "condition" -> new Installation.Trigger.Condition(tag.getString("function"),
                    values(tag.getList("args", Tag.TAG_COMPOUND)));
            case "event" -> new Installation.Trigger.Event(tag.getString("name"),
                    values(tag.getList("args", Tag.TAG_COMPOUND)));
            default -> null;
        });
    }

    private static ListTag values(List<Object> values) {
        var list = new ListTag();
        values.forEach(value -> list.add(value(value)));
        return list;
    }

    private static List<Object> values(ListTag list) {
        var values = new ArrayList<Object>();
        for (int i = 0; i < list.size(); i++) {
            values.add(value(list.getCompound(i)));
        }
        return values;
    }

    private static CompoundTag value(Object value) {
        var tag = new CompoundTag();
        switch (value) {
            case null -> tag.putBoolean("none", true);
            case Boolean b -> tag.putBoolean("bool", b);
            case Long l -> tag.putLong("int", l);
            case Double d -> tag.putDouble("float", d);
            case String s -> tag.putString("str", s);
            case ListValue list -> tag.put("list", values(list.items()));
            default -> throw new IllegalArgumentException("an installation cannot keep " + value);
        }
        return tag;
    }

    private static Object value(CompoundTag tag) {
        if (tag.contains("bool")) {
            return tag.getBoolean("bool");
        }
        if (tag.contains("int")) {
            return tag.getLong("int");
        }
        if (tag.contains("float")) {
            return tag.getDouble("float");
        }
        if (tag.contains("str")) {
            return tag.getString("str");
        }
        if (tag.contains("list")) {
            return new ListValue(values(tag.getList("list", Tag.TAG_COMPOUND)));
        }
        return null;
    }
}
