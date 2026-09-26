package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MappingFeedbackTest {
    @Test
    void preservesSlotWithoutNarrowingGenerationOrDisplayingItAsAnAmount() {
        var feedback = MappingFeedback.fromCode("accepted-8-9223372036854775807");
        assertEquals("accepted", feedback.key());
        assertEquals(List.of("8"), feedback.arguments());
        assertEquals("success", feedback.tone());
    }

    @Test
    void malformedAndUnknownResultsCannotMasqueradeAsSuccess() {
        for (var code : List.of("accepted-8", "accepted-8-x", "released-x", "cleared-stale-x", "rejected-future-reason", "")) {
            assertEquals("unknown", MappingFeedback.fromCode(code).key(), code);
            assertEquals("error", MappingFeedback.fromCode(code).tone(), code);
        }
    }

    @Test
    void releaseAndStaleCleanupRemainDifferentOutcomes() {
        assertEquals("released", MappingFeedback.fromCode("released-0").key());
        assertEquals("cleared", MappingFeedback.fromCode("cleared-stale-0").key());
        assertEquals("waiting", MappingFeedback.fromCode("confirm-release").tone());
    }

    @Test
    void refusalReasonsRetainTheirSpecificMeaning() {
        for (var code : List.of("rejected-pending-send", "rejected-pending-return", "rejected-owner_conflict",
                "rejected-stale-slot", "rejected-session", "rejected-claim-changed")) {
            assertEquals(code, MappingFeedback.fromCode(code).key());
            assertEquals("error", MappingFeedback.fromCode(code).tone());
        }
    }
}
