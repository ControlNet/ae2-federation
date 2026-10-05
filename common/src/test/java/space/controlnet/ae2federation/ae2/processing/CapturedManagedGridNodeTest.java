package space.controlnet.ae2federation.ae2.processing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AEColor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CapturedManagedGridNodeTest {
    private final List<String> physicalCalls = new ArrayList<>();

    /** A physical node that records what reaches it; {@code created} decides whether it already has a node. */
    private IManagedGridNode physical(boolean created) {
        var node = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> method.getName().equals("hasFlag") ? args[0] == GridFlags.REQUIRE_CHANNEL
                        : method.getReturnType() == boolean.class ? false : null);
        return (IManagedGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {IManagedGridNode.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getNode")) return created ? node : null;
                    physicalCalls.add(method.getName());
                    return method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == IManagedGridNode.class ? proxy : null;
                });
    }

    /** Addons that mix into PatternProviderLogic may configure its node as AE2 allows; the owner's node stays as is. */
    @Test
    void addonNodeConfigurationLeavesThePhysicalNodeAlone() {
        var facade = new CapturedManagedGridNode(physical(true), new NativeProviderLaneServices());
        assertSame(facade, facade.setIdlePowerUsage(5));
        assertSame(facade, facade.setGridColor(AEColor.RED));
        assertSame(facade, facade.setInWorldNode(true));
        assertSame(facade, facade.setTagName("addon"));
        assertSame(facade, facade.setExposedOnSides(EnumSet.noneOf(net.minecraft.core.Direction.class)));
        assertSame(facade, facade.setVisualRepresentation((appeng.api.stacks.AEItemKey) null));
        assertDoesNotThrow(() -> facade.setOwningPlayerId(7));
        assertDoesNotThrow(() -> facade.loadFromNBT(new CompoundTag()));
        assertDoesNotThrow(() -> facade.saveToNBT(new CompoundTag()));
        assertDoesNotThrow(() -> facade.create(null, null));
        assertDoesNotThrow(facade::destroy);
        assertEquals(List.of(), physicalCalls);
    }

    /** A flag an addon asks for after the physical node exists cannot be added; asking does not fail. */
    @Test
    void aFlagMissingFromTheCreatedNodeIsIgnored() {
        var facade = new CapturedManagedGridNode(physical(true), new NativeProviderLaneServices());
        assertSame(facade, assertDoesNotThrow(() -> facade.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)));
        assertEquals(List.of(), physicalCalls);
    }

    @Test
    void flagsBeforeTheNodeExistsStillConfigureIt() {
        var facade = new CapturedManagedGridNode(physical(false), new NativeProviderLaneServices());
        facade.setFlags(GridFlags.REQUIRE_CHANNEL);
        assertEquals(List.of("setFlags"), physicalCalls);
    }
}
