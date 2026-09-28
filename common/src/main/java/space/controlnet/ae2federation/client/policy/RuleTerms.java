package space.controlnet.ae2federation.client.policy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;

/**
 * The terms of a rule beyond on/off, as the pair editor lists them: allowed operations in a fixed order, the
 * resource filter and whether the consumer may pass the capability on. Keys only; the client translates them.
 */
public record RuleTerms(List<String> operations, String filter, int filterEntries, boolean reexport) {
    public static RuleTerms of(PolicyRule rule) {
        var operations = Arrays.stream(PolicyOperation.values()).filter(rule.operations()::contains)
                .map(operation -> operation.name().toLowerCase(Locale.ROOT)).toList();
        return new RuleTerms(operations, rule.filter().mode().name().toLowerCase(Locale.ROOT),
                rule.filter().entries().size(), rule.allowReexport());
    }
}
