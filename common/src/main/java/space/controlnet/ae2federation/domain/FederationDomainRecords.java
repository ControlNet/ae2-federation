package space.controlnet.ae2federation.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a physical domain id is remembered by across restarts: the last sequence handed out and, per domain, the nodes
 * that anchor it. A domain is anchored by its devices, every node that is not a cable; a domain of cables only by its
 * {@link #CABLE_ANCHORS} smallest cable nodes. A node anchors at most one domain. Anchors leave only when their block is
 * broken or when a loaded node joins another domain, never when it unloads, so the record of a domain that is not
 * loaded waits for the first of its anchors to load again.
 */
public final class FederationDomainRecords {
    /** How many cable nodes anchor a domain that has no device. */
    public static final int CABLE_ANCHORS = 8;

    private long sequence;
    private final Map<Long, Anchors> records = new HashMap<>();
    private final Map<FederationDomainNodeId, Long> owners = new HashMap<>();
    private Runnable changeListener = () -> {
    };

    /** The nodes that anchor one domain; a domain with any device keeps no cable anchors. */
    public record Anchors(Set<FederationDomainNodeId> devices, Set<FederationDomainNodeId> cables) {
        public Anchors {
            devices = Collections.unmodifiableSet(new TreeSet<>(devices));
            cables = Collections.unmodifiableSet(new TreeSet<>(cables));
        }

        boolean isEmpty() {
            return devices.isEmpty() && cables.isEmpty();
        }

        boolean contains(FederationDomainNodeId node) {
            return devices.contains(node) || cables.contains(node);
        }

        Anchors without(FederationDomainNodeId node) {
            var keptDevices = new TreeSet<>(devices);
            var keptCables = new TreeSet<>(cables);
            keptDevices.remove(node);
            keptCables.remove(node);
            return new Anchors(keptDevices, keptCables);
        }
    }

    /** Runs after every change, so the owner can mark its saved copy dirty. */
    public void onChange(Runnable listener) {
        changeListener = Objects.requireNonNull(listener);
    }

    /** The last physical sequence handed out; a new domain takes the next one. */
    public long sequence() {
        return sequence;
    }

    public void advanceSequence(long handedOut) {
        if (handedOut > sequence) {
            sequence = handedOut;
            changeListener.run();
        }
    }

    public Optional<Anchors> anchors(long domainSequence) {
        return Optional.ofNullable(records.get(domainSequence));
    }

    /** The domain a node anchors, by its sequence. */
    public OptionalLong owner(FederationDomainNodeId node) {
        var owner = owners.get(node);
        return owner == null ? OptionalLong.empty() : OptionalLong.of(owner);
    }

    public Map<Long, Anchors> all() {
        return Collections.unmodifiableMap(records);
    }

    /**
     * Replaces a domain's anchors; a node anchoring another domain moves to this one, and a record left with no anchor
     * is dropped.
     */
    public void put(long domainSequence, Set<FederationDomainNodeId> devices, Set<FederationDomainNodeId> cables) {
        var replacement = new Anchors(devices, cables);
        var previous = records.get(domainSequence);
        if (replacement.equals(previous)) {
            return;
        }
        if (previous != null) {
            for (var set : java.util.List.of(previous.devices(), previous.cables())) {
                set.stream().filter(node -> !replacement.contains(node)).forEach(node -> owners.remove(node, domainSequence));
            }
        }
        for (var set : java.util.List.of(replacement.devices(), replacement.cables())) {
            for (var node : set) {
                var owner = owners.put(node, domainSequence);
                if (owner != null && owner != domainSequence) {
                    replace(owner, records.get(owner).without(node));
                }
            }
        }
        replace(domainSequence, replacement);
        changeListener.run();
    }

    /** Forgets a node whose block was broken. */
    public void drop(FederationDomainNodeId node) {
        var owner = owners.remove(node);
        if (owner != null) {
            replace(owner, records.get(owner).without(node));
            changeListener.run();
        }
    }

    /** Forgets a domain that merged into another; its anchors that still point to it go with it. */
    public void remove(long domainSequence) {
        var removed = records.remove(domainSequence);
        if (removed != null) {
            for (var set : java.util.List.of(removed.devices(), removed.cables())) {
                set.forEach(node -> owners.remove(node, domainSequence));
            }
            changeListener.run();
        }
    }

    private void replace(long domainSequence, Anchors anchors) {
        if (anchors.isEmpty()) {
            records.remove(domainSequence);
        } else {
            records.put(domainSequence, anchors);
        }
    }
}
