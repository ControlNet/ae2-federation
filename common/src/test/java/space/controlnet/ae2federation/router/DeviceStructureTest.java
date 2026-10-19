package space.controlnet.ae2federation.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.router.DeviceStructure.Cell;
import space.controlnet.ae2federation.router.DeviceStructure.Invalid;
import space.controlnet.ae2federation.router.DeviceStructure.Reason;
import space.controlnet.ae2federation.router.DeviceStructure.Result;
import space.controlnet.ae2federation.router.DeviceStructure.Unknown;
import space.controlnet.ae2federation.router.DeviceStructure.Valid;

final class DeviceStructureTest {
    private static Set<BlockPos> box(int sx, int sy, int sz) {
        var cells = new HashSet<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells.add(new BlockPos(x, y, z));
        return cells;
    }

    private static Result evaluate(Set<BlockPos> members, BlockPos start) {
        return evaluate(members, Set.of(), start);
    }

    private static Result evaluate(Set<BlockPos> members, Set<BlockPos> unloaded, BlockPos start) {
        return DeviceStructure.evaluate(start, (x, y, z) -> {
            var position = new BlockPos(x, y, z);
            if (unloaded.contains(position)) return Cell.UNLOADED;
            return members.contains(position) ? Cell.MEMBER : Cell.OTHER;
        }, DeviceStructure.MAX_EDGE);
    }

    private static void assertFormed(Set<BlockPos> members, BlockPos min, BlockPos max) {
        // Every member finds the same body, wherever the check starts.
        for (var start : members) assertEquals(new Valid(min, max), evaluate(members, start), "from " + start);
    }

    @Test
    void solidCuboidsOfAnyProportionForm() {
        assertFormed(box(1, 1, 1), BlockPos.ZERO, BlockPos.ZERO);
        assertFormed(box(2, 2, 2), BlockPos.ZERO, new BlockPos(1, 1, 1));
        assertFormed(box(1, 1, 4), BlockPos.ZERO, new BlockPos(0, 0, 3));
        assertFormed(box(1, 3, 5), BlockPos.ZERO, new BlockPos(0, 2, 4));
        assertFormed(box(4, 1, 1), BlockPos.ZERO, new BlockPos(3, 0, 0));
        assertFormed(box(2, 3, 4), BlockPos.ZERO, new BlockPos(1, 2, 3));
    }

    @Test
    void incompleteHollowAndBranchedCandidatesStayUnformed() {
        var missingCorner = box(2, 2, 2);
        missingCorner.remove(new BlockPos(1, 1, 1));
        for (var start : missingCorner) assertEquals(Invalid.class, evaluate(missingCorner, start).getClass());

        var hollow = box(3, 3, 3);
        hollow.remove(new BlockPos(1, 1, 1));
        for (var start : hollow) assertEquals(Invalid.class, evaluate(hollow, start).getClass(), "from " + start);
        assertEquals(new Invalid(Reason.NOT_SOLID), evaluate(hollow, BlockPos.ZERO));

        var l = new HashSet<>(Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 1, 0)));
        for (var start : l) assertEquals(Invalid.class, evaluate(l, start).getClass());

        // One extra block against the middle of a finished body leaves the whole candidate unformed.
        var protrusion = box(3, 1, 3);
        protrusion.add(new BlockPos(1, 1, 1));
        for (var start : protrusion) assertEquals(Invalid.class, evaluate(protrusion, start).getClass(), "from " + start);
    }

    @Test
    void cuboidsTouchingFaceToFaceAreOneCandidate() {
        var union = box(2, 1, 1);
        for (int x = 2; x < 4; x++) for (int y = 0; y < 2; y++) union.add(new BlockPos(x, y, 0));
        for (var start : union) assertEquals(Invalid.class, evaluate(union, start).getClass());

        var aligned = box(2, 2, 1);
        for (int x = 2; x < 5; x++) for (int y = 0; y < 2; y++) aligned.add(new BlockPos(x, y, 0));
        assertFormed(aligned, BlockPos.ZERO, new BlockPos(4, 1, 0));
    }

    @Test
    void blocksTouchingOnlyAtAnEdgeOrCornerFormSeparately() {
        var members = new HashSet<>(Set.of(BlockPos.ZERO, new BlockPos(1, 1, 0), new BlockPos(2, 2, 2)));
        for (var start : members) assertEquals(new Valid(start, start), evaluate(members, start));
    }

    @Test
    void anUnloadedPositionTheAnswerNeedsLeavesItUnknown() {
        var members = box(1, 1, 3);
        assertEquals(Unknown.INSTANCE, evaluate(members, Set.of(new BlockPos(0, 0, 3)), BlockPos.ZERO));
        assertEquals(Unknown.INSTANCE, evaluate(members, Set.of(new BlockPos(0, 0, 1)), BlockPos.ZERO));
        // A position that was already found to break the cuboid decides it, whatever lies in the unloaded chunk.
        var hollow = box(3, 3, 3);
        hollow.remove(new BlockPos(1, 1, 1));
        assertEquals(new Invalid(Reason.NOT_SOLID), evaluate(hollow, Set.of(new BlockPos(5, 5, 5)), BlockPos.ZERO));
    }

    @Test
    void bodiesLongerThanEightBlocksStayUnformed() {
        var edge = DeviceStructure.MAX_EDGE;
        assertEquals(8, edge);
        assertFormed(box(edge, 1, 1), BlockPos.ZERO, new BlockPos(edge - 1, 0, 0));
        var tooLong = box(edge + 1, 1, 1);
        for (var start : tooLong) assertEquals(new Invalid(Reason.TOO_LARGE), evaluate(tooLong, start), "from " + start);
    }
}
