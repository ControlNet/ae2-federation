package space.controlnet.ae2federation.compat.jech;

import java.util.function.BiPredicate;
import me.towdium.jecharacters.utils.Match;

/** {@link JechPinyin} through JECh's own matcher; loaded only when JECh is installed. */
final class JechPinyinDirect {
    private JechPinyinDirect() {
    }

    /** JECh's matcher; throws a {@link LinkageError} here, not in a search, when JECh's matcher is another one. */
    static BiPredicate<String, String> matcher() {
        Match.contains("", "");
        return (text, query) -> Match.contains(text, (CharSequence) query);
    }
}
