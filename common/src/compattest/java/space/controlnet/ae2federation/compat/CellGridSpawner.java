package space.controlnet.ae2federation.compat;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;

/**
 * Places each test's structure in a cell of its own, in rows of {@code perRow}. Most scenes build well past their
 * template (a 3x3x3 template may carry a scene 20 blocks long), and vanilla's {@code StructureGridSpawner} leaves only
 * five blocks between templates, so tests running side by side would build into each other. A cell is whole chunks:
 * one chunk before the template on each axis, and its size rounded up to chunks plus one more chunk after it.
 */
final class CellGridSpawner implements GameTestRunner.StructureSpawner {
    private static final int CHUNK = 16;

    private final BlockPos origin;
    private final int perRow;
    private int inRow;
    private int x;
    private int z;
    private int rowDepth;

    CellGridSpawner(BlockPos origin, int perRow) {
        this.origin = origin;
        this.perRow = perRow;
    }

    @Override
    public Optional<GameTestInfo> spawnStructure(GameTestInfo info) {
        info.setNorthWestCorner(origin.offset(x + CHUNK, 0, z + CHUNK));
        info.prepareTestStructure();
        var bounds = StructureUtils.getStructureBoundingBox(info.getStructureBlockEntity());
        x += cell(bounds.getXSpan());
        rowDepth = Math.max(rowDepth, cell(bounds.getZSpan()));
        if (++inRow == perRow) {
            inRow = 0;
            x = 0;
            z += rowDepth;
            rowDepth = 0;
        }
        return Optional.of(info);
    }

    private static int cell(int span) {
        return CHUNK + Math.ceilDiv(span, CHUNK) * CHUNK + CHUNK;
    }
}
