package space.controlnet.ae2federation.test.energy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** Native evidence of the shared energy GameTests: the energy each case drew through a shared pool, in nano AE. */
public final class SharedEnergyEvidence {
    private SharedEnergyEvidence() {
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        var evidence = new TreeMap<>(facts);
        var extracted = switch (testId) {
            case "energysharedmutual" -> decimal(facts, "accepted").add(decimal(facts, "largeAccepted"));
            case "energycoldstart" -> decimal(facts, "providerDebit");
            case "energysharedtransitive" -> decimal(facts, "accepted");
            case "energyruleoffsplits" -> decimal(facts, "initialTransfer").add(decimal(facts, "reenabledExtracted"));
            case "energydisconnectsplits", "energyadjacentrouters" -> decimal(facts, "initialTransfer");
            default -> throw new IllegalArgumentException("Unknown shared energy evidence case: " + testId);
        };
        evidence.put("extracted", extracted.movePointRight(9).toBigIntegerExact().toString());
        NativeEnergyEvidence.write(testId, assertions, operations(testId), evidence);
    }

    /** The native energy operations a case's evidence accounts for. */
    public static int operations(String testId) {
        return testId.equals("energysharedmutual") || testId.equals("energyruleoffsplits") ? 2 : 1;
    }

    private static BigDecimal decimal(Map<String, String> facts, String name) {
        return new BigDecimal(facts.get(name));
    }
}
