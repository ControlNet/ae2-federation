package space.controlnet.ae2federation.test.identity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import java.util.Optional;

public interface NativeNodeDataProbe extends IGridService {
    Optional<String> marker(IGridNode node);
}
