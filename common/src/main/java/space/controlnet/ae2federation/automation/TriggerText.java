package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import space.controlnet.ae2federation.function.Ops;

/**
 * A trigger as one line of text, as the commands take it and the Automation tab sends it: {@code manual},
 * {@code redstone}, {@code every 40}, {@code when is_low "minecraft:iron_ingot"} or
 * {@code on block_changed interface-k7m2qx 2}. Words are split at spaces; a word in double quotes may hold spaces.
 */
public final class TriggerText {
    private TriggerText() {
    }

    public static Installation.Trigger parse(String text) {
        var words = words(text);
        if (words.isEmpty()) {
            throw new IllegalArgumentException("no trigger given");
        }
        var rest = List.<Object>copyOf(words.subList(Math.min(words.size(), 2), words.size()));
        return switch (words.get(0)) {
            case "manual" -> only(words, new Installation.Trigger.Manual());
            case "redstone" -> only(words, new Installation.Trigger.Redstone());
            case "every" -> {
                if (words.size() != 2) {
                    throw new IllegalArgumentException("every takes a number of ticks");
                }
                try {
                    var ticks = Integer.parseInt(words.get(1));
                    if (ticks < 1) {
                        throw new NumberFormatException();
                    }
                    yield new Installation.Trigger.Schedule(ticks);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("every takes a number of ticks, not " + words.get(1));
                }
            }
            case "when" -> new Installation.Trigger.Condition(name(words, "when takes a function"), rest);
            case "on" -> new Installation.Trigger.Event(name(words, "on takes an event"), rest);
            default -> throw new IllegalArgumentException("a trigger is manual, redstone, every, when or on, not "
                    + words.get(0));
        };
    }

    public static String format(Installation.Trigger trigger) {
        return switch (trigger) {
            case Installation.Trigger.Manual manual -> "manual";
            case Installation.Trigger.Redstone redstone -> "redstone";
            case Installation.Trigger.Schedule schedule -> "every " + schedule.ticks();
            case Installation.Trigger.Condition condition ->
                    "when " + condition.function() + arguments(condition.args());
            case Installation.Trigger.Event event -> "on " + event.name() + arguments(event.args());
        };
    }

    /** Words split at spaces; a word in double quotes may hold spaces, as an alias can. */
    public static List<String> words(String text) {
        var words = new ArrayList<String>();
        var word = new StringBuilder();
        var quoted = false;
        var any = false;
        for (var c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (c == ' ' && !quoted) {
                if (any) {
                    words.add(word.toString());
                }
                word.setLength(0);
                any = false;
            } else {
                word.append(c);
                any = true;
            }
        }
        if (any) {
            words.add(word.toString());
        }
        return words;
    }

    private static Installation.Trigger only(List<String> words, Installation.Trigger trigger) {
        if (words.size() != 1) {
            throw new IllegalArgumentException(words.get(0) + " takes no arguments");
        }
        return trigger;
    }

    private static String name(List<String> words, String missing) {
        if (words.size() < 2) {
            throw new IllegalArgumentException(missing);
        }
        return words.get(1);
    }

    /** Arguments as words: text in quotes when it holds a space or is empty, other values as Python prints them. */
    private static String arguments(List<Object> args) {
        var text = new StringBuilder();
        for (var arg : args) {
            text.append(' ');
            if (arg instanceof String s) {
                text.append(s.isEmpty() || s.contains(" ") ? '"' + s + '"' : s);
            } else {
                text.append(Ops.repr(arg));
            }
        }
        return text.toString();
    }
}
