package space.controlnet.ae2federation.compat.jech;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Pinyin search through JustEnoughCharacters, when it is installed. JECh only rewrites the searches on its own list of
 * other mods' methods, so our searches ask its matcher directly: {@code Match.contains(text, query)}, the call JECh puts
 * in place of {@code text.contains(query)}. It is found at run time, so nothing is compiled against JECh, and its
 * PinIn library, nested in the JECh jar, need not be on the build path.
 */
public final class JechPinyin {
    private static final String MOD_ID = "jecharacters";
    private static final String MATCH = "me.towdium.jecharacters.utils.Match";

    private JechPinyin() {
    }

    /** The matcher, or {@code null} while JECh is not installed or its matcher is not the expected one. */
    public static @Nullable BiPredicate<String, String> find() {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded(MOD_ID)) return null;
        MethodHandle contains;
        try {
            var match = Class.forName(MATCH, true, JechPinyin.class.getClassLoader());
            contains = MethodHandles.publicLookup().findStatic(match, "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
        return (text, query) -> {
            try {
                return (boolean) contains.invokeExact(text, (CharSequence) query);
            } catch (Throwable e) {
                return false;
            }
        };
    }
}
