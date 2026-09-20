package space.controlnet.ae2federation.storage.subscription;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StorageSubscriptionService implements AutoCloseable {
    private static final int SNAPSHOT_RACE_LIMIT = 256;
    private final Map<SourceSubscriptionKey, SourceBinding> bindings = new HashMap<>();
    private long sourceEvents;
    private long consumerDeliveries;
    private final Map<space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey, Long>
            deliveriesByRelationship = new HashMap<>();
    private int listenerRegistrations;
    private int listenerRemovals;

    public void reconcile(List<SourceSubscriptionPlan> plans) {
        var desired = new LinkedHashMap<SourceSubscriptionKey, SourceSubscriptionPlan>();
        for (var plan : plans) {
            if (desired.put(plan.key(), plan) != null) {
                throw new IllegalArgumentException("Duplicate true-source subscription plan: " + plan.key());
            }
        }
        List.copyOf(bindings.entrySet()).stream()
                .filter(entry -> !matches(entry.getValue(), desired.get(entry.getKey())))
                .forEach(entry -> remove(entry.getKey()));
        desired.forEach((key, plan) -> {
            var binding = bindings.get(key);
            if (binding == null) {
                binding = new SourceBinding(plan);
                bindings.put(key, binding);
                binding.start();
            } else {
                binding.updateTargets(plan.targets());
            }
        });
    }

    public int activeListenerCount() {
        return bindings.size();
    }

    public int listenerRegistrationCount() {
        return listenerRegistrations;
    }

    public int listenerRemovalCount() {
        return listenerRemovals;
    }

    public long sourceEventCount() {
        return sourceEvents;
    }

    public long consumerDeliveryCount() {
        return consumerDeliveries;
    }

    public long consumerDeliveryCount(
            space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey relationship) {
        return deliveriesByRelationship.getOrDefault(relationship, 0L);
    }

    public long eventVersion(SourceSubscriptionKey key) {
        var binding = bindings.get(key);
        return binding == null ? 0 : binding.ledger.eventVersion();
    }

    public long snapshotVersion(SourceSubscriptionKey key) {
        var binding = bindings.get(key);
        return binding == null ? 0 : binding.ledger.snapshotVersion();
    }

    public long registrationId(SourceSubscriptionKey key) {
        var binding = bindings.get(key);
        return binding == null || binding.registration == null ? 0 : binding.registration.id();
    }

    public long registrationId(appeng.api.storage.MEStorage source) {
        return bindings.values().stream()
                .filter(binding -> binding.source == source && binding.registration != null)
                .mapToLong(binding -> binding.registration.id())
                .findFirst()
                .orElse(0);
    }

    public void reset(SourceSubscriptionKey key) {
        var binding = bindings.get(key);
        if (binding != null) {
            binding.reset(true);
        }
    }

    @Override
    public void close() {
        List.copyOf(bindings.keySet()).forEach(this::remove);
    }

    private boolean matches(SourceBinding binding, SourceSubscriptionPlan plan) {
        return plan != null && binding.source == plan.source()
                && binding.nativeStorageService == plan.nativeStorageService() && binding.usable();
    }

    private void remove(SourceSubscriptionKey key) {
        var binding = bindings.remove(key);
        if (binding != null) {
            binding.close();
        }
    }

    private void retire(SourceBinding binding) {
        if (bindings.remove(binding.key, binding)) {
            binding.close();
        }
    }

    private final class SourceBinding implements AutoCloseable, NativeStorageListener {
        private final SourceSubscriptionKey key;
        private final appeng.api.storage.MEStorage source;
        private final appeng.api.networking.storage.IStorageService nativeStorageService;
        private final java.util.function.BooleanSupplier sourceCurrent;
        private final SourceSnapshotLedger<AEKey> ledger;
        private final BoundedKeyCursor<AEKey> reconciliationKeys =
                new BoundedKeyCursor<>(NativeStorageNotificationHub.MAX_RETAINED_KEYS);
        private Map<space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey,
                SubscriptionTarget> targets = Map.of();
        private NativeStorageRegistration registration;

        private SourceBinding(SourceSubscriptionPlan plan) {
            key = plan.key();
            source = plan.source();
            nativeStorageService = plan.nativeStorageService();
            sourceCurrent = plan.sourceCurrent();
            ledger = new SourceSnapshotLedger<>(key.generation().value(), SNAPSHOT_RACE_LIMIT, this::forward);
            updateTargets(plan.targets());
        }

        private void start() {
            registration = NativeStorageNotificationHub.register(nativeStorageService, this);
            listenerRegistrations++;
            reset(true);
        }

        private void reset(boolean invalidateUnchanged) {
            if (!current() || !sourceCurrent.getAsBoolean() || !ledger.beginSnapshot(key.generation().value())) {
                return;
            }
            var before = ledger.eventVersion();
            var sourceSnapshot = snapshot();
            if (!NativeStorageNotificationHub.discover(nativeStorageService, sourceSnapshot.keySet())) {
                return;
            }
            if (!current() || !sourceCurrent.getAsBoolean()
                    || !ledger.completeSnapshot(key.generation().value(), sourceSnapshot, true)) {
                return;
            }
            if (invalidateUnchanged && ledger.eventVersion() == before) {
                invalidateCurrentTargets();
            }
        }

        @Override
        public void onAmountChanged(NativeStorageAmount nativeAmount) {
            if (!current() || !sourceCurrent.getAsBoolean()) {
                return;
            }
            try {
                ledger.acceptAbsolute(key.generation().value(), nativeAmount.key(), nativeAmount.absolute());
            } catch (SnapshotQueueOverflowException exception) {
                retire(this);
            }
        }

        @Override
        public long sourceAmount(AEKey discoveredKey) {
            return current() && sourceCurrent.getAsBoolean() ? amount(discoveredKey) : 0;
        }

        @Override
        public void discoverKeys(Iterable<? extends AEKey> discoveredKeys) {
            try {
                reconciliationKeys.addAll(discoveredKeys);
            } catch (KeyRetentionOverflowException exception) {
                retire(this);
            }
        }

        @Override
        public void onDiscoveryOverflow() {
            retire(this);
        }

        @Override
        public int reconcileSource(int keyBudget) {
            if (!current() || !sourceCurrent.getAsBoolean()) {
                return 0;
            }
            var keys = reconciliationKeys.next(keyBudget);
            var probes = 0;
            for (var key : keys) {
                if (!current() || !sourceCurrent.getAsBoolean()) {
                    break;
                }
                ledger.acceptAbsolute(this.key.generation().value(), key, amount(key));
                probes++;
            }
            return probes;
        }

        private Map<AEKey, Long> snapshot() {
            var result = new LinkedHashMap<AEKey, Long>();
            for (var entry : source.getAvailableStacks()) {
                result.put(entry.getKey(), entry.getLongValue());
            }
            return result;
        }

        private long amount(AEKey key) {
            return source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }

        private void forward(SourceQuantityUpdate<AEKey> update) {
            sourceEvents++;
            invalidateCurrentTargets();
        }

        private void invalidateCurrentTargets() {
            var delivered = java.util.Collections.newSetFromMap(
                    new IdentityHashMap<appeng.api.networking.storage.IStorageService, Boolean>());
            for (var target : targets.values()) {
                if (target.current().getAsBoolean() && delivered.add(target.consumer())) {
                    target.consumer().invalidateCache();
                    consumerDeliveries++;
                    deliveriesByRelationship.merge(target.relationship(), 1L, Math::addExact);
                }
            }
        }

        private void updateTargets(List<SubscriptionTarget> replacement) {
            var next = new LinkedHashMap<space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey,
                    SubscriptionTarget>();
            replacement.forEach(target -> next.put(target.relationship(), target));
            targets = Map.copyOf(next);
            invalidateCurrentTargets();
        }

        private boolean usable() {
            return !ledger.closed() && registration != null && registration.active();
        }

        private boolean current() {
            return bindings.get(key) == this && usable();
        }

        @Override
        public void close() {
            ledger.close();
            if (registration != null) {
                registration.close();
                registration = null;
                listenerRemovals++;
            }
            targets = Map.of();
        }
    }
}
