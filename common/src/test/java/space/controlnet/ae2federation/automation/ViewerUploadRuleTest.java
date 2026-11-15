package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Who the server takes recipe viewer recipes from: everyone by default, or operators and the world's owner. */
final class ViewerUploadRuleTest {
    @AfterEach
    void reset() {
        ViewerUploadRule.set(ViewerUploadRule.Who.EVERYONE);
    }

    @Test
    void everyoneMaySendByDefault() {
        assertEquals(ViewerUploadRule.Who.EVERYONE, ViewerUploadRule.who());
        assertTrue(ViewerUploadRule.allows(false, false));
    }

    @Test
    void operatorsOnlyLeavesOtherPlayersOut() {
        ViewerUploadRule.set(ViewerUploadRule.Who.OPERATORS);
        assertFalse(ViewerUploadRule.allows(false, false));
        assertTrue(ViewerUploadRule.allows(true, false));
        assertTrue(ViewerUploadRule.allows(false, true), "the singleplayer world's owner always may");
    }
}
