package space.controlnet.ae2federation.test.energy;

import java.util.Map;

public final class DirectionalEnergyEvidence {
    private DirectionalEnergyEvidence() {
    }

    public static void write(String testId, int assertions, Map<String, String> facts) {
        var evidence = new java.util.TreeMap<>(facts);
        var extracted = switch (testId) {
            case "energydirectionalpolicy" -> new java.math.BigDecimal(facts.get("accepted"))
                    .add(new java.math.BigDecimal(facts.get("largeAccepted"))).toPlainString();
            case "energyringconservation" -> facts.get("accepted");
            case "energycoldstart" -> facts.get("providerDebit");
            case "energyrejectreverse" -> facts.get("reverseExtracted");
            case "energydisconnectnosource" -> Double.toString(
                    Double.parseDouble(facts.get("initialTransfer"))
                            + Double.parseDouble(facts.get("replacementExtracted")));
            default -> throw new IllegalArgumentException("Unknown directional energy evidence case: " + testId);
        };
        evidence.put("extracted", new java.math.BigDecimal(extracted).movePointRight(9).toBigIntegerExact().toString());
        NativeEnergyEvidence.write(testId, assertions, testId.equals("energydirectionalpolicy") ? 2 : 1, evidence);
    }
}
