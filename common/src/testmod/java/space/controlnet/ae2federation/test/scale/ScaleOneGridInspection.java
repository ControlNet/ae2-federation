package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;

public final class ScaleOneGridInspection {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleOneGridInspection.class);

    private ScaleOneGridInspection() {
    }

    public static void assertScene(GameTestHelper helper, IGridNode provider, ProcessingCraftingGrid crafting,
            IGridNode requester, int completedJobs, boolean record) {
        var grids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        var nodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        var source = provider.getGrid();
        var details = new StringBuilder();
        inspect(helper, "provider", provider, source, grids, nodes, details);
        inspect(helper, "storage", crafting.node(), source, grids, nodes, details);
        inspect(helper, "cpu", crafting.cpuNode(), source, grids, nodes, details);
        inspect(helper, "requester", requester, source, grids, nodes, details);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var position = new BlockPos(x, y, z);
                    var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
                    if (entity instanceof AENetworkedBlockEntity ae2) {
                        inspect(helper, position.toShortString(), ae2.getMainNode().getNode(), source,
                                grids, nodes, details);
                    }
                }
            }
        }
        for (var grid : List.copyOf(grids)) {
            for (var node : grid.getNodes()) {
                inspect(helper, "grid-member", node, source, grids, nodes, details);
            }
        }
        var receipt = "AE2F_SCALE_ONE_GRID completedJobs=" + completedJobs + " nodes=" + nodes.size()
                + " distinctGrids=" + grids.size() + " sourceGrid=" + id(source)
                + " settled=" + FabricRegistryAccess.confirmedNetworkId(source).isPresent()
                + " details=" + details;
        if (record) {
            LOGGER.info("{}", receipt);
            var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
            if (!configured.isBlank()) {
                var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-one-grid.log");
                try {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, receipt + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot persist one-Grid receipt", exception);
                }
            }
        }
        helper.assertTrue(FabricRegistryAccess.confirmedNetworkId(source).isPresent(),
                "One-Grid source identity must be settled");
        helper.assertValueEqual(grids.size(), 1, "Scene-owned active native Grids must be exactly one; " + receipt);
    }

    private static void inspect(GameTestHelper helper, String name, IGridNode node, IGrid source,
            java.util.Set<IGrid> grids, java.util.Set<IGridNode> nodes, StringBuilder details) {
        helper.assertTrue(node != null && node.hasGridBooted() && node.isActive(),
                "Scene-owned AE2 node must be booted and active: " + name);
        if (nodes.add(node)) {
            var grid = node.getGrid();
            grids.add(grid);
            details.append(name).append(':').append(id(node)).append('@').append(id(grid))
                    .append(grid == source ? "=source" : "=extra").append(',');
        }
    }

    private static String id(Object object) {
        return Integer.toUnsignedString(System.identityHashCode(object));
    }
}
