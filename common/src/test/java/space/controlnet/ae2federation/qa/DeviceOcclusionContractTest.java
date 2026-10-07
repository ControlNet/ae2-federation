package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.router.DeviceOcclusion;

/**
 * A device may hide a neighbour's face only where its model closes that face completely; anywhere the model has a gap
 * the neighbour must stay drawn, or the player looks through the gap into the void. The models are drawn front SOUTH.
 */
final class DeviceOcclusionContractTest {
    private static final Path MODELS = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation/models/block");

    @Test
    void theRouterHidesNoNeighbourFace() throws IOException {
        assertEquals(closedFaces("router"), fullFaces(DeviceOcclusion.ROUTER));
        assertEquals(Set.of(), fullFaces(DeviceOcclusion.ROUTER));
    }

    @Test
    void providerAndEndpointHideOnlyBehindTheirBack() throws IOException {
        for (var model : new String[] {"pattern_provider", "processing_endpoint"}) {
            assertEquals(closedFaces(model), fullFaces(DeviceOcclusion.front(Direction.SOUTH)), model);
        }
        for (var facing : Direction.values()) {
            assertEquals(Set.of(facing.getOpposite()), fullFaces(DeviceOcclusion.front(facing)), facing.getName());
        }
    }

    /** Sides of the unit cube the model covers completely, from the elements lying on each side with a face there. */
    private static Set<Direction> closedFaces(String model) throws IOException {
        var json = JsonParser.parseString(Files.readString(MODELS.resolve(model + ".json"))).getAsJsonObject();
        var closed = EnumSet.noneOf(Direction.class);
        for (var side : Direction.values()) {
            var covered = new boolean[16][16];
            for (var element : json.getAsJsonArray("elements")) {
                var object = element.getAsJsonObject();
                if (!object.getAsJsonObject("faces").has(side.getName())) continue;
                var from = corner(object, "from");
                var to = corner(object, "to");
                int axis = side.getAxis().ordinal();
                if ((side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? to[axis] : from[axis])
                        != (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 : 0)) continue;
                int u = (axis + 1) % 3;
                int v = (axis + 2) % 3;
                for (int i = from[u]; i < to[u]; i++) {
                    for (int j = from[v]; j < to[v]; j++) covered[i][j] = true;
                }
            }
            boolean full = true;
            for (var row : covered) for (var cell : row) full &= cell;
            if (full) closed.add(side);
        }
        return closed;
    }

    private static int[] corner(JsonObject element, String key) {
        var values = element.getAsJsonArray(key);
        return new int[] {values.get(0).getAsInt(), values.get(1).getAsInt(), values.get(2).getAsInt()};
    }

    /** As {@code Block.isFaceFull}: the sides on which the shape hides a neighbour's whole face. */
    private static Set<Direction> fullFaces(VoxelShape shape) {
        var full = EnumSet.noneOf(Direction.class);
        for (var side : Direction.values()) {
            if (!Shapes.joinIsNotEmpty(Shapes.block(), shape.getFaceShape(side), BooleanOp.ONLY_FIRST)) full.add(side);
        }
        return full;
    }
}
