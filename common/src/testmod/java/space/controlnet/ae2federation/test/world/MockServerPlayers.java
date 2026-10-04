package space.controlnet.ae2federation.test.world;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * A connected creative ServerPlayer in the test's level, for menus, policy sessions and packets. Minecraft 1.21.1 marks
 * GameTestHelper's mock player for removal without offering another connected player, and NeoForge's FakePlayer has no
 * connection and is not in the player list. This is the one call to replace when the target version provides one.
 */
public final class MockServerPlayers {
    private MockServerPlayers() {}

    @SuppressWarnings("removal")
    public static ServerPlayer inLevel(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }
}
