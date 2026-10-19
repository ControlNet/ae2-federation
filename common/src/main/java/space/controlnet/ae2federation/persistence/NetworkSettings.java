package space.controlnet.ae2federation.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;

/**
 * What the Federation keeps that a player set or is owed under a network's id: a name, rules on either side, crafting
 * returns. An id with none of these has no history to lose, so a merge may fold it into another id; so may an id whose
 * settings repeat another's exactly. Anything else saved by NetworkId that a player would miss belongs here too.
 */
public record NetworkSettings(Optional<String> name, Map<Side, PolicyRule> rules, boolean craftingReturns) {
    /** One rule as seen from the id: its role, the network on the other end and the capability. */
    public record Side(boolean consumer, NetworkId other, PolicyCapability capability) {
    }

    public static NetworkSettings of(ServerLevel level, NetworkId network) {
        var rules = new HashMap<Side, PolicyRule>();
        for (var record : PolicySavedData.get(level).snapshot().entries().values()) {
            if (!(record instanceof PolicyRecord.Configured configured)) continue;
            var key = configured.key();
            if (key.consumerNetworkId().equals(network)) {
                rules.put(new Side(true, key.providerNetworkId(), key.capability()), configured.rule());
            } else if (key.providerNetworkId().equals(network)) {
                rules.put(new Side(false, key.consumerNetworkId(), key.capability()), configured.rule());
            }
        }
        return new NetworkSettings(NetworkNames.get(level).name(network), Map.copyOf(rules),
                CraftingReturnLedger.get(level).references(network));
    }

    public boolean configured() {
        return name.isPresent() || !rules.isEmpty() || craftingReturns;
    }

    /**
     * Whether ids holding these settings can become one without losing anything: the same name and the same rules to
     * the same other networks, no rule between two of {@code merging} (it would end on itself) and no crafting
     * returns, which cannot be told apart per id.
     */
    public static boolean interchangeable(Collection<NetworkSettings> settings, Collection<NetworkId> merging) {
        var first = settings.iterator().next();
        return settings.stream().allMatch(each -> each.name.equals(first.name) && each.rules.equals(first.rules)
                && !each.craftingReturns && each.rules.keySet().stream().noneMatch(side -> merging.contains(side.other())));
    }

    /** Drops a folded id's name and rules; they repeat the survivor's, so nothing is lost. */
    public static void forget(ServerLevel level, NetworkId network) {
        NetworkNames.get(level).rename(network, "");
        var policies = PolicyService.get(level);
        for (var record : PolicySavedData.get(level).snapshot().entries().values()) {
            var key = record.key();
            if (record instanceof PolicyRecord.Configured
                    && (key.consumerNetworkId().equals(network) || key.providerNetworkId().equals(network))) {
                policies.delete(new PolicyDelete(key, record.revision()));
            }
        }
    }
}
