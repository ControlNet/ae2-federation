package space.controlnet.ae2federation.test.storage;

import java.util.stream.Collectors;
import appeng.api.storage.IStorageProvider;
import java.util.ArrayList;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;

public final class ProvenanceEvidence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvenanceEvidence.class);

    private ProvenanceEvidence() {
    }

    public static void domain(String testId, String phase, NativeSourceDomain domain) {
        for (var source : domain.sources()) {
            var aliases = source.aliases().stream().map(alias -> alias.id().toString())
                    .collect(Collectors.joining(","));
            LOGGER.info("AE2F_PROVENANCE_NATIVE testId={} phase={} origin={} generation={} grid={} source={} "
                            + "storage={} aliases={} priority={}",
                    testId, phase, domain.origin().value(), domain.generation().value(),
                    Integer.toUnsignedString(System.identityHashCode(domain.runtimeGrid())), source.id(),
                    Integer.toUnsignedString(System.identityHashCode(source.storage())), aliases, source.priority());
        }
    }

    public static void rejection(String testId, NativeSourceDomain previous, ProvenanceDiagnostic diagnostic) {
        LOGGER.info("AE2F_PROVENANCE_REJECTION testId={} origin={} generation={} diagnostic={} current=false",
                testId, previous.origin().value(), previous.generation().value(), diagnostic);
    }

    public static CallbackSnapshot callback(String testId, String phase, IStorageProvider provider) {
        var entries = new ArrayList<CallbackEntry>();
        provider.mountInventories((storage, priority) -> entries.add(
                new CallbackEntry(entries.size(), storage instanceof FederationManagedStorage)));
        entries.forEach(entry -> LOGGER.info(
                "AE2F_PROVENANCE_CALLBACK testId={} phase={} slot={} managed={}",
                testId, phase, entry.slot(), entry.managed()));
        var nativeSlots = entries.stream().filter(entry -> !entry.managed()).map(CallbackEntry::slot).toList();
        return new CallbackSnapshot(nativeSlots.getFirst(), (int) entries.stream().filter(CallbackEntry::managed).count());
    }

    public static void mountIsolation(String testId, String projectionBefore, String projectionAfter,
            long sourceGenerationBefore, long sourceGenerationAfter, long mountGenerationBefore,
            long mountGenerationAfter, int removalsBefore, int removalsAfterRemount, int removalsAfterStale,
            long oldVisible, long oldInserted, long oldExtracted, long newInserted, long newVisible,
            long newExtracted, long newRemaining) {
        LOGGER.info("AE2F_PROVENANCE_MOUNT testId={} projectionBefore={} projectionAfter={} "
                        + "sourceGenerationBefore={} sourceGenerationAfter={} mountGenerationBefore={} "
                        + "mountGenerationAfter={} removalsBefore={} removalsAfterRemount={} removalsAfterStale={} "
                        + "oldVisible={} oldInserted={} oldExtracted={} newInserted={} newVisible={} "
                        + "newExtracted={} newRemaining={}",
                testId, projectionBefore, projectionAfter, sourceGenerationBefore, sourceGenerationAfter,
                mountGenerationBefore, mountGenerationAfter, removalsBefore, removalsAfterRemount,
                removalsAfterStale, oldVisible, oldInserted, oldExtracted, newInserted, newVisible,
                newExtracted, newRemaining);
    }

    public record CallbackSnapshot(int nativeSlot, int managedEntries) {
    }

    private record CallbackEntry(int slot, boolean managed) {
    }
}
