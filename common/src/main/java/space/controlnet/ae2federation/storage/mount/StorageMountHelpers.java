package space.controlnet.ae2federation.storage.mount;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.provenance.ExportSource;
import space.controlnet.ae2federation.storage.provenance.MountGeneration;

final class StorageMountHelpers {
    private StorageMountHelpers() {
    }

    static MEStorage aggregate(List<ExportSource> sources) {
        if (sources.size() == 1) {
            return sources.getFirst().storage();
        }
        var aggregate = new NetworkStorage();
        sources.forEach(source -> aggregate.mount(source.priority(), source.storage()));
        return aggregate;
    }

    static MountGeneration nextGeneration(Map<PolicyKey, MountGeneration> generations, PolicyKey key) {
        var current = generations.get(key);
        var next = current == null ? new MountGeneration(1) : current.next();
        generations.put(key, next);
        return next;
    }
}
