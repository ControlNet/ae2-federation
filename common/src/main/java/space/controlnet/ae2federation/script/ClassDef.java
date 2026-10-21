package space.controlnet.ae2federation.script;

import java.util.List;

/**
 * A player record: {@code class Name:} with typed fields only, no methods and no base classes. It behaves as a Python
 * dataclass without the decorator: constructed with its fields in order or by keyword. The docstring is {@code null}
 * when there is none.
 */
public record ClassDef(String id, Notes notes, String name, String docstring, List<Field> fields)
        implements Definition {
    public ClassDef {
        fields = List.copyOf(fields);
    }
}
