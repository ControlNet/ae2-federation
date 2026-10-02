package space.controlnet.ae2federation.storage.provenance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import java.util.List;
import java.util.Objects;

/** {@code skipped} are the mounted handles left out of {@code sources}, ordered by alias. */
public record NativeSourceDomain(OriginNetworkId origin, SourceGeneration generation, IGrid runtimeGrid,
        List<ExportSource> sources, List<IGridNode> sourceNodes, List<SkippedSource> skipped) {
    public NativeSourceDomain {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(runtimeGrid, "runtimeGrid");
        sources = List.copyOf(sources);
        sourceNodes = List.copyOf(sourceNodes);
        skipped = List.copyOf(skipped);
    }
}
