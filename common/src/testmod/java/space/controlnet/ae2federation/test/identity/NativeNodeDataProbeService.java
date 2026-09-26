package space.controlnet.ae2federation.test.identity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridServiceProvider;
import appeng.me.GridNode;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeNodeDataProbeService implements NativeNodeDataProbe, IGridServiceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeNodeDataProbeService.class);
    private static final String DATA_KEY = "ae2federation_test_native_node_probe";
    private static final String MARKER_KEY = "marker";

    private final IGrid grid;
    private final Map<IGridNode, String> markers = new IdentityHashMap<>();

    public NativeNodeDataProbeService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        var restored = savedData != null && savedData.get(DATA_KEY) instanceof CompoundTag data
                ? data.getString(MARKER_KEY)
                : "";
        var marker = restored.isEmpty()
                ? System.getProperty("ae2federation.identityMarker", "baseline-native-marker")
                : restored;
        markers.put(gridNode, marker);
        if (restored.isEmpty()) {
            ((GridNode) gridNode).callListener(IGridNodeListener::onSaveChanges);
        }
        LOGGER.info(
                "AE2F_NATIVE_IDENTITY_BASELINE operation=add-node grid={} service={} node={} restored={} marker={}",
                Integer.toHexString(System.identityHashCode(grid)),
                Integer.toHexString(System.identityHashCode(this)),
                Integer.toHexString(System.identityHashCode(gridNode)),
                !restored.isEmpty(), marker);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        markers.remove(gridNode);
    }

    @Override
    public void saveNodeData(IGridNode gridNode, CompoundTag savedData) {
        var marker = markers.get(gridNode);
        if (marker == null) {
            return;
        }
        var data = new CompoundTag();
        data.putString(MARKER_KEY, marker);
        savedData.put(DATA_KEY, data);
        LOGGER.info(
                "AE2F_NATIVE_IDENTITY_BASELINE operation=save-node-data grid={} service={} node={} marker={}",
                Integer.toHexString(System.identityHashCode(grid)),
                Integer.toHexString(System.identityHashCode(this)),
                Integer.toHexString(System.identityHashCode(gridNode)), marker);
    }

    @Override
    public Optional<String> marker(IGridNode node) {
        return Optional.ofNullable(markers.get(node));
    }
}
