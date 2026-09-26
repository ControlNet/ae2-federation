package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import java.util.List;
import java.util.Objects;

public record NativeSourceDomain(OriginNetworkId origin, SourceGeneration generation, IGrid runtimeGrid,
        List<ExportSource> sources, List<IGridNode> sourceNodes) {
    public NativeSourceDomain {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(runtimeGrid, "runtimeGrid");
        sources = List.copyOf(sources);
        sourceNodes = List.copyOf(sourceNodes);
    }
}
