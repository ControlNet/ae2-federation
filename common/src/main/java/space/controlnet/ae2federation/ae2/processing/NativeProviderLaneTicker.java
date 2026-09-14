package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import java.util.List;

final class NativeProviderLaneTicker implements IGridTickable {
    private final List<NativeProviderLaneServices> delegates;

    NativeProviderLaneTicker(List<NativeProviderLaneServices> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        var requests = delegates.stream().map(delegate -> delegate.ticker().getTickingRequest(node)).toList();
        var min = requests.stream().mapToInt(TickingRequest::minTickRate).min().orElseThrow();
        var max = requests.stream().mapToInt(TickingRequest::maxTickRate).min().orElseThrow();
        var sleeping = requests.stream().allMatch(TickingRequest::isSleeping);
        return new TickingRequest(min, max, sleeping);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        var result = TickRateModulation.SLEEP;
        for (var delegate : delegates) {
            var modulation = delegate.tick(node, ticksSinceLastCall);
            if (modulation.ordinal() > result.ordinal()) {
                result = modulation;
            }
        }
        return result;
    }
}
