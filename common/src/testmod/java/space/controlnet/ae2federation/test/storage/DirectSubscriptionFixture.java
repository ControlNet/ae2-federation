package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

public final class DirectSubscriptionFixture implements AutoCloseable {
    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final SubscriptionHookOwner hooks = new SubscriptionHookOwner();
    private final boolean multipleContributors;
    private boolean bridgePlaced;
    private boolean bridgeClosed;

    public DirectSubscriptionFixture(GameTestHelper helper) {
        this(helper, false);
    }

    public DirectSubscriptionFixture(GameTestHelper helper, boolean multipleContributors) {
        this.helper = helper;
        this.multipleContributors = multipleContributors;
        bridge = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        bridge.installStorageCells();
        if (multipleContributors) {
            bridge.replaceCallbackProbeWithSecondChest();
        }
    }

    public boolean ready() {
        if (!bridgePlaced && bridge.networksSettled()) {
            bridge.placeFirstBridge();
            bridgePlaced = true;
            return false;
        }
        return bridgePlaced && bridge.firstBridgeReady();
    }

    public PolicyRevision configure(PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key(), policies.revision(key()), rule));
        return ((PolicyMutationResult.Accepted) result).revision();
    }

    public PolicyKey key() {
        return new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.STORAGE);
    }

    public StorageMountService mounts() {
        return StorageMountService.get(helper.getLevel());
    }

    public MEStorage source() {
        return nativeSource(bridge.providerChest());
    }

    public MEStorage secondSource() {
        if (!multipleContributors) {
            throw new IllegalStateException("Fixture has one contributor");
        }
        return nativeSource(bridge.secondProviderChest());
    }

    public int nativeSourceCount() {
        return nativeSources(bridge.outerGrid()).size();
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public appeng.blockentity.storage.MEChestBlockEntity providerChest() {
        return bridge.providerChest();
    }

    public SubscriptionHookOwner hooks() {
        return hooks;
    }

    public boolean bridgeClosed() {
        return bridgeClosed;
    }

    @Override
    public void close() {
        IllegalStateException unconsumed = null;
        try {
            hooks.assertConsumedAndClose();
        } catch (IllegalStateException exception) {
            unconsumed = exception;
        } finally {
            hooks.close();
            try {
                bridge.close();
                bridgeClosed = true;
            } catch (RuntimeException exception) {
                if (unconsumed == null) {
                    throw exception;
                }
                unconsumed.addSuppressed(exception);
            }
        }
        if (unconsumed != null) {
            throw unconsumed;
        }
    }

    private static java.util.List<MEStorage> nativeSources(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).stream().map(source -> source.storage()).toList();
    }

    private static MEStorage nativeSource(appeng.blockentity.storage.MEChestBlockEntity chest) {
        var node = java.util.Objects.requireNonNull(chest.getMainNode().getNode());
        return new NativeStorageProvenance().qualify(node).getFirst().storage();
    }
}
