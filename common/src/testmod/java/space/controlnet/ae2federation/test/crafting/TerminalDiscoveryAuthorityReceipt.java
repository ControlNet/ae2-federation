package space.controlnet.ae2federation.test.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.policy.PolicyService;

public final class TerminalDiscoveryAuthorityReceipt {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalDiscoveryAuthorityReceipt.class);

    private TerminalDiscoveryAuthorityReceipt() {
    }

    public static void captureCurrent(String testId, TerminalCraftingFixture fixture) {
        requireSelected(testId);
        var allowed = TerminalCraftingFixture.outputKey();
        var forbidden = TerminalCraftingFixture.forbiddenOutputKey();
        var binding = fixture.binding();
        var service = binding.sourceService();
        var provider = fixture.session().snapshot().providerSources().getFirst().provider();
        var sourceKeys = service.getCraftables(key -> true);
        var consumerKeys = binding.consumerGrid().getCraftingService().getCraftables(key -> true);
        var discoveredKeys = fixture.craftables();
        var providerPatterns = provider.getAvailablePatterns();
        var allowedPatterns = service.getCraftingFor(allowed);
        var forbiddenPatterns = service.getCraftingFor(forbidden);
        var filter = PolicyService.get(binding.level()).configured(binding.key()).orElseThrow().rule().filter();
        if (!sourceKeys.equals(Set.of(allowed, forbidden)) || !consumerKeys.isEmpty()
                || !discoveredKeys.equals(Set.of(allowed)) || providerPatterns.size() != 2
                || allowedPatterns.size() != 1 || forbiddenPatterns.size() != 1
                || !containsIdentity(providerPatterns, allowedPatterns.iterator().next())
                || !containsIdentity(providerPatterns, forbiddenPatterns.iterator().next())) {
            throw new IllegalStateException("Terminal discovery authority is not the exact two-pattern native source");
        }
        LOGGER.info("AE2F_TERMINAL_DISCOVERY_AUTHORITY testId={} selected={} phase=terminal-discovery "
                        + "sourceCount={} sourceKeys={} consumerCount={} consumerKeys={} discoveredCount={} "
                        + "discoveredKeys={} allowedKey={} forbiddenKey={} provider={} providerPatterns={} "
                        + "allowedPattern={} forbiddenPattern={} filterMode={} filterEntries={}",
                testId, selectedTest(), sourceKeys.size(), keyIds(sourceKeys), consumerKeys.size(), keyIds(consumerKeys),
                discoveredKeys.size(), keyIds(discoveredKeys), keyId(allowed), keyId(forbidden), identity(provider),
                identities(providerPatterns), identity(allowedPatterns.iterator().next()),
                identity(forbiddenPatterns.iterator().next()), filter.mode(), filter.entries().stream()
                        .map(entry -> entry.resourceType() + "|" + entry.resourceId()).sorted()
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    private static boolean containsIdentity(Collection<IPatternDetails> patterns, IPatternDetails expected) {
        return patterns.stream().anyMatch(pattern -> pattern == expected);
    }

    private static String keyIds(Collection<AEKey> keys) {
        var joined = keys.stream().map(TerminalDiscoveryAuthorityReceipt::keyId).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isEmpty() ? "none" : joined;
    }

    private static String keyId(AEKey key) {
        return key.getId().toString();
    }

    private static String identities(Collection<?> values) {
        return values.stream().map(TerminalDiscoveryAuthorityReceipt::identity).sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static void requireSelected(String testId) {
        if (!selectedTest().equals(testId)) {
            throw new IllegalArgumentException("Discovery authority test ID does not match the selected child");
        }
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
