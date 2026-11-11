package space.controlnet.ae2federation.compat.jech;

import java.util.function.BiPredicate;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Pinyin search through JustEnoughCharacters, when it is installed. JECh only rewrites the searches on its own list of
 * other mods' methods, so our searches ask its matcher directly: {@code Match.contains(text, query)}, the call JECh puts
 * in place of {@code text.contains(query)}. This class only checks for JECh; {@link JechPinyinDirect}, which calls it,
 * is loaded only when JECh is installed.
 */
public final class JechPinyin {
    private static final String MOD_ID = "jecharacters";

    private JechPinyin() {
    }

    /** The matcher, or {@code null} while JECh is not installed or its matcher is not the expected one. */
    public static @Nullable BiPredicate<String, String> find() {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded(MOD_ID)) return null;
        try {
            return JechPinyinDirect.matcher();
        } catch (LinkageError otherApi) {
            return null;
        }
    }
}
