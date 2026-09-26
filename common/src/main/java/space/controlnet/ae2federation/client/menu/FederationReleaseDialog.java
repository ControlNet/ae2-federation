package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import java.util.function.Consumer;

/** Presents the server's prepared release; confirmation still uses the normal sequenced authority request. */
final class FederationReleaseDialog {
    private final UI ui;
    private final Consumer<FederationDomainPolicyAction> send;
    private long requestedSequence = -1;
    private long authoritySequence = -1;
    private JsonObject prepared;
    private Dialog dialog;
    private boolean suppressCancel;

    FederationReleaseDialog(UI ui, Consumer<FederationDomainPolicyAction> send) {
        this.ui = ui;
        this.send = send;
    }

    void prepare(long sequence) {
        closeSilently();
        prepared = null;
        requestedSequence = sequence;
    }

    void acceptAuthority(long sequence) {
        authoritySequence = sequence;
        if (sequence < 0) {
            closeSilently();
            prepared = null;
            requestedSequence = -1;
        }
        else showWhenReady();
    }

    void acceptChoices(String encoded) {
        if (encoded.isEmpty()) return;
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        var replacement = root.has("release") ? root.getAsJsonObject("release") : null;
        if (dialog != null && (replacement == null || !replacement.equals(prepared))) closeSilently();
        prepared = replacement;
        if (prepared != null) showWhenReady();
    }

    private void showWhenReady() {
        if (dialog != null || prepared == null || requestedSequence < 0 || authoritySequence <= requestedSequence) return;
        dialog = new Dialog().setTitle("ae2federation.ui.workspace.release_title").darkenBackground();
        dialog.setId("release_dialog");
        dialog.overlay.layout(style -> style.width(Math.min(300, ui.rootElement.getContentWidth() - 12)));
        dialog.overlay.style(style -> style.backgroundTexture(FederationTheme.FRAME));
        dialog.titleBar.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.INSET)));
        dialog.contentContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        dialog.buttonContainer.style(style -> style.backgroundTexture(new ColorRectTexture(FederationTheme.PANEL)));
        var body = new Label();
        body.setId("release_consequence");
        body.setText(FederationWorkspace.tr("release_consequence", prepared.get("position").getAsString(),
                prepared.get("endpoint").getAsString(), prepared.get("epoch").getAsLong()));
        body.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        body.layout(style -> style.widthPercent(100));
        dialog.addContent(body);
        var cancel = new Button();
        cancel.setId("release_cancel");
        cancel.setText(FederationWorkspace.tr("cancel"));
        cancel.setOnClick(event -> dialog.close());
        var confirm = new Button();
        confirm.setId("release_confirm");
        confirm.addClass("danger-action");
        confirm.setText(FederationWorkspace.tr("release_confirm"));
        confirm.setOnClick(event -> {
            closeSilently();
            prepared = null;
            requestedSequence = authoritySequence;
            send.accept(FederationDomainPolicyAction.RELEASE_ENDPOINT);
        });
        cancel.layout(style -> style.flex(1).height(20));
        confirm.layout(style -> style.flex(1).height(20));
        dialog.addButton(cancel).addButton(confirm);
        dialog.setOnClose(() -> {
            dialog = null;
            if (!suppressCancel) {
                requestedSequence = -1;
                send.accept(FederationDomainPolicyAction.CANCEL_RELEASE);
            }
        });
        dialog.show(ui.rootElement);
    }

    private void closeSilently() {
        if (dialog == null) return;
        suppressCancel = true;
        dialog.close();
        dialog = null;
        suppressCancel = false;
    }
}
