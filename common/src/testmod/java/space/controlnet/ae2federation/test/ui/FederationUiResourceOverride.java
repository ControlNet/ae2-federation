package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FederationUiResourceOverride {
    private static final Path FILE = Platform.getGamePath().resolve(
            "ldlib2/assets/ae2federation_test/ui/federation_harness.xml");

    private FederationUiResourceOverride() {
    }

    static void write(String changedText) {
        var xml = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <ldlib2-ui>
                    <stylesheet location="ldlib2:lss/mc.lss"/>
                    <stylesheet location="ae2federation_test:lss/federation_harness.lss"/>
                    <root id="harness_root" class="panel_bg harness-panel">
                        <label id="shared_resource_text">%s</label>
                        <label id="ack_status">Server acknowledgment pending</label>
                        <button id="ack_control" text="Request server acknowledgment"/>
                    </root>
                </ldlib2-ui>
                """.formatted(changedText);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, xml, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write task-owned resource override " + FILE, exception);
        }
    }

    static void remove() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot remove task-owned resource override " + FILE, exception);
        }
    }
}
