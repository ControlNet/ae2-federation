package space.controlnet.ae2federation.compat;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Replaces complete checkpoints atomically; a failed write leaves the preceding checkpoint intact. */
final class PlaythroughCheckpoint {
    static void write(Path path, String text) throws java.io.IOException {
        var temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private PlaythroughCheckpoint() { }
}
