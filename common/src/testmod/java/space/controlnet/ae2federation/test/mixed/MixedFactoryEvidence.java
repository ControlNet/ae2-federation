package space.controlnet.ae2federation.test.mixed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class MixedFactoryEvidence {
    private static final Map<String, String> RESOURCES = resources();

    private MixedFactoryEvidence() {
    }

    public static void writeBenchmark(MixedFactoryProfile profile, List<MixedFactoryIteration> runs) {
        var measured = runs.stream().filter(run -> !run.warmup()).toList();
        var facts = base("benchmark", "mixedbenchmarksmall",
                Math.multiplyExact(profile.measuredIterations(), 16));
        facts.put("profileVersion", profile.profileVersion());
        facts.put("profileSha256", profile.profileSha256());
        facts.put("seed", Long.toString(profile.seed()));
        facts.put("warmupCount", Integer.toString(profile.warmupIterations()));
        facts.put("measuredCount", Integer.toString(profile.measuredIterations()));
        facts.put("resourceKeyCount", Integer.toString(profile.resourceKeyCount()));
        facts.put("operations", Integer.toString(Math.multiplyExact(profile.measuredIterations(),
                profile.largeBatchCalls() + profile.smallBatchCalls())));
        var batchVolume = Math.multiplyExact(profile.measuredIterations(),
                Math.addExact(Math.multiplyExact(profile.largeBatchCalls(), profile.largeBatchUnits()),
                        Math.multiplyExact(profile.smallBatchCalls(), profile.smallBatchUnits())));
        facts.put("inserted", Long.toString(batchVolume));
        facts.put("extracted", Long.toString(batchVolume));
        facts.put("elapsedNanos", Long.toString(measured.stream().mapToLong(MixedFactoryIteration::elapsedNanos).sum()));
        facts.put("reconciliation.currentRun", "true");
        for (var run : runs) writeRun(facts, run.warmup() ? "warmup.0" : "iteration." + (run.index()
                - profile.warmupIterations()), run, profile);
        write(facts);
    }

    public static void writeNegative(String testId, int assertions, Map<String, String> details) {
        var facts = base("verify", testId, assertions);
        facts.putAll(details);
        write(facts);
    }

    private static void writeRun(Map<String, String> facts, String prefix, MixedFactoryIteration run,
            MixedFactoryProfile profile) {
        var nativeFacts = run.nativeObservation();
        facts.put(prefix + ".index", Integer.toString(run.index()));
        facts.put(prefix + ".phase", run.warmup() ? "warmup" : "measured");
        facts.put(prefix + ".runtimeIdentity", run.runtimeIdentity());
        facts.put(prefix + ".selectedAlternative", Integer.toString(profile.selectedAlternative(run.index())));
        facts.put(prefix + ".planning", Long.toString(nativeFacts.planning()));
        facts.put(prefix + ".submitted", Long.toString(nativeFacts.submitted()));
        facts.put(prefix + ".jobIds", String.join(",", nativeFacts.craftingIds().stream().sorted().toList()));
        facts.put(prefix + ".waiting", Integer.toString(nativeFacts.waitingJobs().size()));
        facts.put(prefix + ".executing", Long.toString(nativeFacts.executing()));
        facts.put(prefix + ".returnInjections", Long.toString(nativeFacts.returnInjections()));
        facts.put(prefix + ".providerOwners", joinOwners(nativeFacts, "push-return"));
        facts.put(prefix + ".returnOwners", joinOwners(nativeFacts, "return-inject"));
        facts.put(prefix + ".projectionReceipts", projectionReceipts(nativeFacts));
        facts.put(prefix + ".busReceipt", busReceipt(nativeFacts));
        facts.put(prefix + ".handlerCalls", Integer.toString(nativeFacts.handlers().size()));
        facts.put(prefix + ".handlerQuantity", Long.toString(nativeFacts.handlers().stream()
                .mapToLong(MixedFactoryObservation.HandlerReceipt::amount).sum()));
        facts.put(prefix + ".stockingReceipts", Integer.toString(nativeFacts.stocking().size()));
        facts.put(prefix + ".peakInFlight", Long.toString(nativeFacts.peakInFlight()));
        facts.put(prefix + ".elapsedNanos", Long.toString(run.elapsedNanos()));
        for (var index = 0; index < nativeFacts.handlers().size(); index++) {
            var receipt = nativeFacts.handlers().get(index);
            facts.put(prefix + ".handler." + index, String.join("|", receipt.machineOwner(), receipt.inputOwner(),
                    receipt.outputOwner(), receipt.returnOwner(), receipt.input(), receipt.output(),
                    Long.toString(receipt.accepted()), Long.toString(receipt.consumed()),
                    Long.toString(receipt.produced())));
        }
        for (var index = 0; index < nativeFacts.stocking().size(); index++) {
            var receipt = nativeFacts.stocking().get(index);
            facts.put(prefix + ".stocking." + index, String.join("|", receipt.interfaceOwner(),
                    Long.toString(receipt.before()), Long.toString(receipt.moved()), Long.toString(receipt.after())));
        }
        writeBatch(facts, prefix + ".batch.large", nativeFacts, "minecraft:lapis_lazuli");
        writeBatch(facts, prefix + ".batch.small", nativeFacts, "minecraft:quartz");
        for (var resource : RESOURCES.entrySet()) writeResource(facts, prefix, resource.getKey(), resource.getValue(), run);
    }

    private static String joinOwners(MixedFactoryObservation.Snapshot observation, String operation) {
        return String.join(",", observation.processingReceipts().stream()
                .filter(receipt -> receipt.operation().equals(operation)).map(
                        space.controlnet.ae2federation.test.processing.ProcessingNativeObservation.Receipt::owner)
                .distinct().sorted().toList());
    }

    private static String projectionReceipts(MixedFactoryObservation.Snapshot observation) {
        return String.join(";", observation.automation().operations().stream().map(receipt -> String.join("|",
                receipt.owner(), receipt.operation(), receipt.key(), Long.toString(receipt.requested()),
                Long.toString(receipt.accepted()))).sorted().toList());
    }

    private static String busReceipt(MixedFactoryObservation.Snapshot observation) {
        var automation = observation.automation();
        return String.join("|", automation.importBus(), Integer.toString(automation.importWork()),
                automation.exportBus(), Integer.toString(automation.exportWork()));
    }

    private static void writeBatch(Map<String, String> facts, String prefix,
            MixedFactoryObservation.Snapshot observation, String id) {
        var extracts = observation.automation().operations().stream().filter(receipt ->
                receipt.operation().equals("extract") && receipt.key().endsWith("|" + id)).toList();
        facts.put(prefix + ".calls", Integer.toString(extracts.size()));
        facts.put(prefix + ".quantity", Long.toString(extracts.stream().mapToLong(
                space.controlnet.ae2federation.test.automation.AutomationNativeObservation.ProjectionReceipt::accepted)
                .sum()));
    }

    private static void writeResource(Map<String, String> facts, String prefix, String alias, String id,
            MixedFactoryIteration run) {
        var nativeFacts = run.nativeObservation();
        var initial = run.initialSource().getOrDefault(id, 0L);
        var inserted = projection(nativeFacts, id, "insert");
        var extracted = projection(nativeFacts, id, "extract");
        var consumed = nativeFacts.handlers().stream().filter(receipt -> receipt.input().equals(id))
                .mapToLong(MixedFactoryObservation.HandlerReceipt::amount).sum();
        var produced = nativeFacts.handlers().stream().filter(receipt -> receipt.output().equals(id))
                .mapToLong(MixedFactoryObservation.HandlerReceipt::amount).sum();
        var callback = run.callbackResults().getOrDefault(id, 0L);
        var finalSource = run.finalSource().getOrDefault(id, 0L);
        var stem = prefix + ".resource." + alias + ".";
        facts.put(stem + "initial", Long.toString(initial));
        facts.put(stem + "projectionInserted", Long.toString(inserted));
        facts.put(stem + "projectionExtracted", Long.toString(extracted));
        facts.put(stem + "handlerConsumed", Long.toString(consumed));
        facts.put(stem + "handlerProduced", Long.toString(produced));
        facts.put(stem + "callbackInserted", Long.toString(callback));
        facts.put(stem + "finalSource", Long.toString(finalSource));
        facts.put(stem + "finalStocked", Long.toString(id.equals("minecraft:diamond") ? run.finalStocked() : 0));
        facts.put(stem + "finalExported", Long.toString(id.equals("minecraft:diamond") ? run.finalExported() : 0));
        var stockConsumed = id.equals("minecraft:diamond") ? nativeFacts.stocking().stream()
                .filter(receipt -> receipt.moved() < 0).mapToLong(receipt -> -receipt.moved()).sum() : 0;
        facts.put(stem + "stockingConsumed", Long.toString(stockConsumed));
        facts.put(stem + "equation", Boolean.toString(Math.addExact(Math.addExact(initial, inserted), produced)
                == Math.addExact(Math.addExact(extracted, consumed), finalSource)));
    }

    private static long projection(MixedFactoryObservation.Snapshot observation, String id, String operation) {
        return observation.automation().operations().stream().filter(receipt -> receipt.operation().equals(operation)
                && receipt.key().endsWith("|" + id)).mapToLong(
                        space.controlnet.ae2federation.test.automation.AutomationNativeObservation.ProjectionReceipt::accepted)
                .sum();
    }

    private static TreeMap<String, String> base(String kind, String testId, int assertions) {
        var facts = new TreeMap<String, String>();
        facts.put("schemaVersion", "1");
        facts.put("status", "passed");
        facts.put("kind", kind);
        facts.put("testId", testId);
        facts.put("structure", "ae2federation_test:harness_native_smoke");
        facts.put("assertions", Integer.toString(assertions));
        return facts;
    }

    private static Map<String, String> resources() {
        var values = new LinkedHashMap<String, String>();
        values.put("diamond", "minecraft:diamond");
        values.put("cobblestone", "minecraft:cobblestone");
        values.put("dirt", "minecraft:dirt");
        values.put("stone", "minecraft:stone");
        values.put("iron_ingot", "minecraft:iron_ingot");
        values.put("gold_ingot", "minecraft:gold_ingot");
        values.put("emerald", "minecraft:emerald");
        values.put("redstone", "minecraft:redstone");
        values.put("glass", "minecraft:glass");
        values.put("lapis_lazuli", "minecraft:lapis_lazuli");
        values.put("quartz", "minecraft:quartz");
        return Map.copyOf(values);
    }

    private static void write(Map<String, String> facts) {
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) return;
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var lines = new ArrayList<String>(facts.size());
        facts.forEach((key, value) -> lines.add(key + "=" + value));
        try {
            Files.createDirectories(path.getParent());
            Files.write(temporary, lines);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write mixed factory evidence " + path, exception);
        }
    }
}
