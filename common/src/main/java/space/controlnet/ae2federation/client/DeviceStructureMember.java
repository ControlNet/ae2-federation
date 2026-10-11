package space.controlnet.ae2federation.client;

import java.util.List;
import java.util.Set;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Quad;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;

/** The geometry of a formed Router or Switch member, built per set of neighbours that belong to the same body. */
@FunctionalInterface
public interface DeviceStructureMember {
    List<Quad> build(Set<Side> connections);
}
