package space.controlnet.ae2federation.test.crafting;

import appeng.api.networking.security.IActionSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import space.controlnet.ae2federation.crafting.binding.CraftingSubmissionSnapshot;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalAdapter;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalSession;

public final class CraftingLifecycleFixture implements AutoCloseable {
    private static final BlockPos REQUESTER = new BlockPos(2, 3, 2);
    private final GameTestHelper helper;
    private final CraftingBindingFixture binding;
    private NativeCraftingRequester requester;
    private NativeTerminalSession terminalSession;
    private final List<NativeCraftingRequester> requesters = new ArrayList<>();
    private CraftingSubmissionSnapshot requestSnapshot;

    public CraftingLifecycleFixture(GameTestHelper helper) {
        this.helper = helper;
        binding = new CraftingBindingFixture(helper, true);
    }

    public CraftingBindingFixture binding() {
        return binding;
    }

    public NativeCraftingRequester createRequester() {
        requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(REQUESTER),
                binding.sourcePhysicalStorage(), binding.key().providerNetworkId(), null);
        requester.connect(binding.sourceChest().getMainNode().getNode());
        requesters.add(requester);
        return requester;
    }

    public NativeCraftingRequester replaceRequester(CompoundTag state) {
        requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(REQUESTER),
                binding.sourcePhysicalStorage(), binding.key().providerNetworkId(), state);
        requester.connect(binding.sourceChest().getMainNode().getNode());
        requesters.add(requester);
        return requester;
    }

    public NativeCraftingRequester createReplacementRequester() {
        var replacement = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(REQUESTER),
                binding.sourcePhysicalStorage(), binding.key().providerNetworkId(), null);
        replacement.connect(binding.sourceChest().getMainNode().getNode());
        requesters.add(replacement);
        return replacement;
    }

    public CompoundTag writeState() {
        return requester.writeState();
    }

    public void openTerminalSession() {
        terminalSession = NativeTerminalAdapter.discover(helper.getLevel(), binding.consumerGrid(),
                binding.key().providerNetworkId(), IActionSource.empty()).orElseThrow();
        requestSnapshot = terminalSession.snapshot();
    }

    public NativeTerminalSession terminalSession() {
        if (terminalSession == null) {
            throw new IllegalStateException("Native terminal session is not open");
        }
        return terminalSession;
    }

    public NativeTerminalRequest beginTerminalRequest(appeng.api.stacks.AEKey output, long amount) {
        return terminalSession().begin(output, amount).orElseThrow();
    }

    public java.util.UUID requesterNodeId(NativeCraftingRequester owner) {
        return binding.providerGrid().getService(space.controlnet.ae2federation.identity.NetworkIdentityService.class)
                .lineage(owner.getActionableNode()).nodeId();
    }

    public Object authorityProvider() {
        return requestSnapshot.providerSources().getFirst().provider();
    }

    public void closeTerminalSession() {
        if (terminalSession == null) {
            throw new IllegalStateException("Native terminal session is not open");
        }
        terminalSession.close();
    }

    public boolean terminalSessionClosed() {
        return terminalSession != null && terminalSession.isClosed();
    }

    public void assertTerminalSessionRejectsUse() {
        try {
            terminalSession().craftables();
            throw new IllegalStateException("Closed native terminal session accepted public API use");
        } catch (IllegalStateException expected) {
            if (!"Native terminal session is closed".equals(expected.getMessage())) {
                throw expected;
            }
        }
    }

    public void removeBridges() {
        binding.removeBridges();
    }

    public void restoreBridge() {
        binding.restoreBridge();
    }

    @Override
    public void close() {
        requesters.forEach(NativeCraftingRequester::close);
        binding.close();
    }
}
