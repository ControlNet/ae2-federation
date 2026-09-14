package space.controlnet.ae2federation.test.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record PolicyRestartState(PolicyKey key, PolicyRevision revision, long prepareProcessId) {
    public static PolicyRestartState read() {
        var properties = new Properties();
        try (var input = Files.newInputStream(path())) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Policy restart state", exception);
        }
        return new PolicyRestartState(new PolicyKey(
                new NetworkId(java.util.UUID.fromString(properties.getProperty("consumer"))),
                new NetworkId(java.util.UUID.fromString(properties.getProperty("provider"))),
                PolicyCapability.valueOf(properties.getProperty("capability"))),
                new PolicyRevision(Long.parseLong(properties.getProperty("revision"))),
                Long.parseLong(properties.getProperty("prepareProcessId")));
    }

    public void write() {
        var properties = new Properties();
        properties.setProperty("consumer", key.consumerNetworkId().value().toString());
        properties.setProperty("provider", key.providerNetworkId().value().toString());
        properties.setProperty("capability", key.capability().name());
        properties.setProperty("revision", Long.toString(revision.value()));
        properties.setProperty("prepareProcessId", Long.toString(prepareProcessId));
        try {
            Files.createDirectories(path().getParent());
            try (var output = Files.newOutputStream(path())) {
                properties.store(output, "AE2 Federation Policy restart state");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Policy restart state", exception);
        }
    }

    private static Path path() {
        var configured = System.getProperty("ae2federation.policyStateFile", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("Missing ae2federation.policyStateFile");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
