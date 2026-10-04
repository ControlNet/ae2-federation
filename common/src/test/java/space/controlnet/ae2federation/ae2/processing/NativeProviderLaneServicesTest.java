package space.controlnet.ae2federation.ae2.processing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import org.junit.jupiter.api.Test;

class NativeProviderLaneServicesTest {
    /** A service an addon adds to every PatternProviderLogic, as Applied Flux adds its energy distributor. */
    private interface AddonService extends IGridNodeService {
    }

    private static final IGridTickable TICKER = new IGridTickable() {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 1, false);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            return TickRateModulation.SAME;
        }
    };

    @Test
    void addonServicesAreLeftOutOfTheLane() {
        var services = new NativeProviderLaneServices();
        assertDoesNotThrow(() -> services.capture(AddonService.class, new AddonService() {
        }));
        services.capture(IGridTickable.class, TICKER);
        assertSame(TICKER, services.ticker());
    }

    /** AE2 lets a later registration replace a service; a Lane keeps AE2's own, which registers first. */
    @Test
    void aLaterTickerOrProviderDoesNotReplaceAE2s() {
        var services = new NativeProviderLaneServices();
        services.capture(IGridTickable.class, TICKER);
        var wrapper = new IGridTickable() {
            @Override
            public TickingRequest getTickingRequest(IGridNode node) {
                return new TickingRequest(1, 1, false);
            }

            @Override
            public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
                return TickRateModulation.URGENT;
            }
        };
        assertDoesNotThrow(() -> services.capture(IGridTickable.class, wrapper));
        assertSame(TICKER, services.ticker());
    }

    @Test
    void aLaneWithoutAE2sTickerIsStillRejected() {
        assertThrows(IllegalStateException.class, () -> new NativeProviderLaneServices().ticker());
    }
}
