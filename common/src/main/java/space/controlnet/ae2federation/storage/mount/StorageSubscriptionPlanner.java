package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.storage.IStorageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.storage.provenance.ExportSource;
import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.subscription.SourceSubscriptionKey;
import space.controlnet.ae2federation.storage.subscription.SourceSubscriptionPlan;
import space.controlnet.ae2federation.storage.subscription.StorageSubscriptionService;
import space.controlnet.ae2federation.storage.subscription.SubscriptionTarget;

final class StorageSubscriptionPlanner {
    private final StorageDependencyIndex dependencies;
    private final StorageSubscriptionService subscriptions;

    StorageSubscriptionPlanner(StorageDependencyIndex dependencies, StorageSubscriptionService subscriptions) {
        this.dependencies = dependencies;
        this.subscriptions = subscriptions;
    }

    void reconcile(Map<PolicyKey, MountedStorageRelationship> mounts,
            Map<PolicyKey, MountGeneration> mountGenerations) {
        var plans = new HashMap<SourceSubscriptionKey, PlanBuilder>();
        for (var mounted : mounts.values()) {
            var effective = dependencies.relationship(mounted.effectiveKey());
            if (effective == null) continue;
            var domain = mounted.domain();
            var consumerStorage = mounted.relationship().consumerGrid().getService(IStorageService.class);
            for (var source : domain.sources()) {
                var key = new SourceSubscriptionKey(source.id(), source.generation());
                var builder = plans.computeIfAbsent(key, ignored -> new PlanBuilder(key, source, domain));
                builder.targets.add(new SubscriptionTarget(effective.key(), consumerStorage,
                        () -> mounts.get(mounted.relationship().key()) == mounted
                                && mounted.generation().equals(mountGenerations.get(mounted.relationship().key()))
                                && dependencies.current(effective, domain)));
            }
        }
        subscriptions.reconcile(plans.values().stream().map(PlanBuilder::build).toList());
    }

    private final class PlanBuilder {
        private final SourceSubscriptionKey key;
        private final ExportSource source;
        private final NativeSourceDomain domain;
        private final List<SubscriptionTarget> targets = new ArrayList<>();

        private PlanBuilder(SourceSubscriptionKey key, ExportSource source, NativeSourceDomain domain) {
            this.key = key;
            this.source = source;
            this.domain = domain;
        }

        private SourceSubscriptionPlan build() {
            return new SourceSubscriptionPlan(key, source.storage(),
                    domain.runtimeGrid().getService(IStorageService.class),
                    () -> dependencies.sourceCurrent(domain), targets);
        }
    }
}
