package space.controlnet.ae2federation.script;

import java.util.Map;

/** Where a file is edited: the server's other program files by name, and the catalogue. */
public record Workspace(Map<String, Program> files, Catalog catalog) {
    public Workspace {
        files = Map.copyOf(files);
    }
}
