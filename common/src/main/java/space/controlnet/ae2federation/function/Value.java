package space.controlnet.ae2federation.function;

import java.util.Set;

/** A runtime value. {@link #UNAVAILABLE} is what an observation that could not be made gives: not false, not 0. */
public sealed interface Value {
    Value UNAVAILABLE = new Unavailable();
    Value NOTHING = new Nothing();

    record Bool(boolean value) implements Value {
    }

    record Num(double value) implements Value {
    }

    record Text(String value) implements Value {
    }

    record Resource(ResourceRef value) implements Value {
    }

    /** Which resources a storage rule exposes: every one, only {@code entries}, or every one but {@code entries}. */
    record Filter(Mode mode, Set<ResourceRef> entries) implements Value {
        public enum Mode { ALL, ALLOW_LIST, DENY_LIST }

        public Filter {
            entries = Set.copyOf(entries);
        }
    }

    record Unavailable() implements Value {
    }

    record Nothing() implements Value {
    }
}
