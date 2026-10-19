package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds the strings in a script's line that name a resource, such as {@code "minecraft:iron_ingot"}: the editor shows
 * the resource's icon before each. The line comes as the editor colours it, in segments, where a string is one
 * segment from its opening quote to its closing one, and a comment is one segment too.
 */
public final class ResourceStrings {
    /** A string naming a resource: the column (from 0) of its opening quote, and the resource's ID. */
    public record At(int column, String id) {
    }

    private ResourceStrings() {
    }

    /** The strings among {@code segments} whose text is an ID that {@code known} accepts, in line order. */
    public static List<At> find(List<String> segments, Predicate<String> known) {
        var found = new ArrayList<At>();
        int column = 0;
        for (var segment : segments) {
            if (segment.length() > 2) {
                char quote = segment.charAt(0);
                var id = segment.substring(1, segment.length() - 1);
                if ((quote == '"' || quote == '\'') && segment.charAt(segment.length() - 1) == quote
                        && id.indexOf(':') > 0 && known.test(id)) {
                    found.add(new At(column, id));
                }
            }
            column += segment.length();
        }
        return found;
    }
}
