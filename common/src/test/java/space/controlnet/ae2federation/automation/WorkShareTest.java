package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The server's program work per tick: each run's share, the tick's cap, and the turn order when it runs out. */
final class WorkShareTest {
    private static final List<String> INSTALLATIONS = List.of("a", "b", "c", "d");

    /** One tick where every installation's run takes all it is granted; who got work, in order. */
    private static List<String> tick(WorkShare share) {
        share.begin();
        var served = new ArrayList<String>();
        for (var id : share.order(INSTALLATIONS, Function.identity())) {
            long granted = share.grant(id);
            if (granted > 0) {
                served.add(id);
            }
            share.spend(granted);
        }
        share.end();
        return served;
    }

    @Test
    void aRunGetsItsShareWhileTheTickHasWork() {
        var share = new WorkShare(100, 1000);
        share.begin();
        assertEquals(100, share.grant("a"));
        share.spend(950);
        assertEquals(50, share.grant("b"), "only what is left");
        share.spend(50);
        assertEquals(0, share.grant("c"));
    }

    @Test
    void aRunPastItsGrantStillCountsAgainstTheTick() {
        var share = new WorkShare(100, 250);
        share.begin();
        share.grant("a");
        share.spend(300);
        assertEquals(0, share.grant("b"));
    }

    @Test
    void theInstallationsLeftWithoutWorkGoFirstNextTick() {
        var share = new WorkShare(100, 200);
        assertEquals(List.of("a", "b"), tick(share));
        assertEquals(List.of("c", "d"), tick(share));
        assertEquals(List.of("a", "b"), tick(share));
    }

    @Test
    void enoughWorkForAllKeepsTheOrder() {
        var share = new WorkShare(100, 1000);
        assertEquals(INSTALLATIONS, tick(share));
        assertEquals(INSTALLATIONS, tick(share));
    }

    @Test
    void aRemovedInstallationStartsTheTurnFromTheTop() {
        var share = new WorkShare(100, 200);
        tick(share);
        share.begin();
        assertEquals(List.of("a", "b"), share.order(List.of("a", "b"), Function.identity()));
    }
}
