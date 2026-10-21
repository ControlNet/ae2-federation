package space.controlnet.ae2federation.neoforge.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.automation.ComputingHost;
import space.controlnet.ae2federation.automation.DeviceBudget;
import space.controlnet.ae2federation.automation.InstallationCost;
import space.controlnet.ae2federation.automation.ProgramSync;

/** The library index survives the trip to the client. */
final class ProgramPayloadsTest {
    @Test
    void theIndexComesBackAsSent() {
        var index = new ProgramSync.Index(
                List.of(new ProgramSync.FileInfo("iron_tools", "0123456789abcdef", true, true, 0,
                        List.of(new ProgramSync.FunctionInfo("keep", List.of("item: Item", "min: int"), "None",
                                true, new InstallationCost(12, 0, 1)),
                                new ProgramSync.FunctionInfo("check", List.of(), "bool", false, null))),
                        new ProgramSync.FileInfo("draft", "fedcba9876543210", false, false, 2, List.of())),
                List.of(new ProgramSync.DeviceInfo("device-k7m2qx", "core lab", "minecraft:overworld",
                        new BlockPos(1, -60, 3), "physical:4", "ae2federation:eco_computation_system_l9",
                        new ProgramSync.Budget(new ComputingHost.Capacity(128, 4_000_000, 4),
                                new DeviceBudget.Reserved(96, 1_200_000, 2, true))),
                        new ProgramSync.DeviceInfo("device-m3pz7c", "", "minecraft:the_nether",
                                new BlockPos(-8, 70, 2), "", "", new ProgramSync.Budget(
                                ComputingHost.Capacity.UNLIMITED, DeviceBudget.Reserved.NOTHING))),
                List.of(new ProgramSync.DeviceInfo("interface-q2w8rt", "north_gate", "minecraft:overworld",
                        new BlockPos(2, -60, 3), "physical:4",
                        "ae2federation:automation_interface", null)),
                List.of(new ProgramSync.NetworkInfo("network-0b6f1e", "Main base", "physical:4",
                                "0b6f1e2a-5c3d-4e7f-8a9b-1c2d3e4f5a6b"),
                        new ProgramSync.NetworkInfo("network-4f1c2b", "", "physical:9",
                                "4f1c2b3a-9d8e-4f7a-b6c5-d4e3f2a1b0c9")),
                List.of(new ProgramSync.InstallationInfo("install-abcdef", "device-k7m2qx", "iron_tools", "keep",
                        List.of("minecraft:iron_ingot", "64"), "every 40", 20, "ignore", 1,
                        new InstallationCost(12, 0, 1), 2_800_000),
                        new ProgramSync.InstallationInfo("install-ghijkl", "device-m3pz7c", "iron_tools", "keep",
                                List.of(), "manual", 20, "parallel", 0, new InstallationCost(48, 640, 0),
                                Long.MAX_VALUE)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        ProgramPayloads.write(buffer, index);
        assertEquals(index, ProgramPayloads.readIndex(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
