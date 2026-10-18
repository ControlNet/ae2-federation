package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Program text sent in chunks under the payload limit, and put back together whatever the order. */
final class ChunkAssemblerTest {
    @Test
    void aLongTextIsSplitAndJoinedBackInAnyOrder() {
        var text = "# " + "x".repeat(ChunkAssembler.CHUNK * 2) + "\ndef f() -> int:\n    return 1\n";
        var chunks = ChunkAssembler.split(text);
        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= ChunkAssembler.CHUNK));
        var assembler = new ChunkAssembler<String>();
        assertEquals(Optional.empty(), assembler.accept("a", 7, 2, 3, chunks.get(2), 0));
        assertEquals(Optional.empty(), assembler.accept("a", 7, 0, 3, chunks.get(0), 0));
        assertEquals(Optional.of(text), assembler.accept("a", 7, 1, 3, chunks.get(1), 0));
        assertEquals(0, assembler.pending());
    }

    @Test
    void anEmptyTextIsOneChunkAndSurrogatePairsAreNeverCut() {
        assertEquals(List.of(""), ChunkAssembler.split(""));
        var text = "a".repeat(ChunkAssembler.CHUNK - 1) + "😀" + "b";
        var chunks = ChunkAssembler.split(text);
        assertEquals(ChunkAssembler.CHUNK - 1, chunks.get(0).length(), "the pair moves to the next chunk whole");
        assertEquals(text, String.join("", chunks));
    }

    @Test
    void aNewerUploadReplacesTheOlderAndStaleOnesAreForgotten() {
        var assembler = new ChunkAssembler<String>();
        assertEquals(Optional.empty(), assembler.accept("a", 1, 0, 2, "old ", 0));
        assertEquals(Optional.empty(), assembler.accept("a", 2, 0, 2, "new ", 10));
        assertEquals(Optional.of("new text"), assembler.accept("a", 2, 1, 2, "text", 10));
        assertEquals(Optional.empty(), assembler.accept("b", 1, 0, 2, "half", 0));
        assembler.expire(ChunkAssembler.STALE_TICKS + 1);
        assertEquals(0, assembler.pending());
    }

    @Test
    void malformedOrOversizedUploadsAreRefused() {
        var assembler = new ChunkAssembler<String>();
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("a", 1, 2, 2, "", 0));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("a", 1, 0, 100, "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("a", 1, 0, 1, "x".repeat(ChunkAssembler.CHUNK + 1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkAssembler.split("x".repeat(ChunkAssembler.MAX_TEXT + 1)));
    }
}
