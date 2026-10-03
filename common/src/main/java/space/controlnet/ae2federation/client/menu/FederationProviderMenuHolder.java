package space.controlnet.ae2federation.client.menu;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;
import space.controlnet.ae2federation.client.policy.MappingFeedback;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/**
 * The ME Federation Pattern Provider's own screen: its nine pattern slots on the Provider card of a wires view that
 * edits only this Provider's mapping, the Endpoints of the domain its Federation face joins (another Provider's shown
 * read-only), each Lane's return buffer, AE2's Blocking, Lock crafting and Pattern Access Terminal settings, its
 * priority and the player inventory.
 *
 * <p>The slots are real menu slots on both sides, added in the same order: the server binds the Provider's own pattern
 * inventory, the client a mirror of the same size that vanilla slot sync fills. Mapping uses the workspace's sequenced
 * requests; settings and priority are LDLib2 bindings the server applies to the Provider while this menu is valid.
 */
final class FederationProviderMenuHolder implements PlayerUIMenuType.PlayerUIHolder, FederationMenuHolder {
    private static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath("ae2federation", "ui/provider.xml");
    /** The mapping requests this screen takes; rule edits and choosing another Provider belong to the workspace. */
    private static final Set<FederationDomainPolicyAction> ACTIONS = EnumSet.of(FederationDomainPolicyAction.SELECT_TARGET,
            FederationDomainPolicyAction.SET_MAPPING, FederationDomainPolicyAction.PREPARE_RELEASE,
            FederationDomainPolicyAction.RELEASE_ENDPOINT, FederationDomainPolicyAction.CANCEL_RELEASE);
    /** AE2's priority field range. */
    private static final int PRIORITY_LIMIT = 999_999_999;
    private static final int CHOICES_TICKS = 5;
    private static final YesNo[] BLOCKING = {YesNo.NO, YesNo.YES};
    private static final YesNo[] TERMINAL = {YesNo.YES, YesNo.NO};

    private final @Nullable FederationDomainPolicySession session;
    private final FederationMenuAuthority authority;
    private final PatternChoices patterns = new PatternChoices();
    private UI currentUi;
    private FederationProcessingGraph graph;
    private FederationReleaseDialog releaseDialog;
    private String serverStatus = "pending";
    private JsonObject choices = new JsonObject();
    private @Nullable String choicesCache;
    private int choicesAge;

    FederationProviderMenuHolder(@Nullable FederationDomainPolicySession session) {
        this.session = session;
        authority = new FederationMenuAuthority(session);
    }

    @Override
    public ModularUI createUI(Player player) {
        var document = XmlUtils.loadXml(XML);
        if (document == null) {
            try (var input = FederationProviderMenuHolder.class.getResourceAsStream("/assets/ae2federation/ui/provider.xml")) {
                document = input == null ? null : XmlUtils.loadXml(input);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot load Federation Pattern Provider UI " + XML, exception);
            }
        }
        var ui = UI.of(Objects.requireNonNull(document, "Missing Federation Pattern Provider UI " + XML));
        currentUi = ui;
        var slots = patternSlots(ui);
        graph = new FederationProcessingGraph(ui, target -> send(FederationDomainPolicyAction.SET_MAPPING, target),
                target -> send(FederationDomainPolicyAction.SELECT_TARGET, target), this::prepareRelease,
                patterns::name, patterns::outputStack, patterns::facts);
        graph.setSlotElements(index -> slots.get(index));
        var releaseDialog = new FederationReleaseDialog(ui, this::send);
        this.releaseDialog = releaseDialog;
        // As on the Federation screen, the footer takes room only while one of its messages has something to say.
        var footer = element(ui, "provider_footer", UIElement.class);
        ui.rootElement.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.TICK, event -> {
            boolean message = footer.getChildren().stream().anyMatch(child -> child.isDisplayed()
                    && child instanceof Label label && !label.getText().getString().isEmpty());
            if (footer.isDisplayed() != message) footer.setDisplay(message);
        });

        setting(ui, player, "setting_blocking", FederationIcons.BLOCKING, Settings.BLOCKING_MODE, BLOCKING, "blocking");
        setting(ui, player, "setting_lock", FederationIcons.LOCK, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.values(), "lock");
        setting(ui, player, "setting_terminal", FederationIcons.TERMINAL, Settings.PATTERN_ACCESS_TERMINAL, TERMINAL, "terminal");
        priority(ui, player);

        sync(ui, this::providerChoices, value -> {
            if (value.isEmpty()) return;
            patterns.reset();
            choices = JsonParser.parseString(value).getAsJsonObject();
            releaseDialog.acceptChoices(value);
            acceptChoices();
        });
        sync(ui, () -> session == null ? "pending" : session.providerStatusCode(), code -> {
            serverStatus = code;
            renderRequestProgress();
        });
        sync(ui, () -> session == null ? "pending" : session.mappingStatusCode(), code -> {
            var label = element(ui, "processing_status", Label.class);
            var feedback = MappingFeedback.fromCode(code);
            label.setText(feedback.silent() ? Component.empty()
                    : Component.translatable(feedback.translationKey(), feedback.arguments().toArray()));
            for (var tone : new String[] {"neutral", "waiting", "success", "error"}) label.removeClass("feedback-" + tone);
            label.addClass("feedback-" + feedback.tone());
            label.setDisplay(!feedback.tone().equals("neutral") && !feedback.silent());
        });
        var authoritySync = sync(ui, () -> authority.encode(this, player), value -> {
            authority.accept(value);
            releaseDialog.acceptAuthority(authority.clientSequence());
            renderRequestProgress();
        });
        authoritySync.removeClass("state-sync");
        authoritySync.addClass("authority-sync");

        final class ProviderModularUI extends ModularUI implements space.controlnet.ae2federation.client.FederationGuiScale.Fixed {
            ProviderModularUI(UI document, Player viewer) {
                super(document, viewer);
            }

            @Override
            public void init(int screenWidth, int screenHeight) {
                ui.rootElement.removeClass("compact");
                if (screenHeight < 280) ui.rootElement.addClass("compact");
                var size = space.controlnet.ae2federation.client.policy.WorkspaceSize.fit(screenWidth, screenHeight);
                ui.rootElement.layout(style -> style.width(size.width()).height(size.height()));
                super.init(screenWidth, screenHeight);
            }
        }
        return new ProviderModularUI(ui, player);
    }

    /**
     * The nine pattern slots, in slot order, parked out of sight until the first choices put each one on its pattern
     * row. The server binds the Provider's pattern inventory; the client binds a mirror that vanilla slot sync fills.
     * Both accept only encoded patterns, as AE2's own Provider menu does.
     */
    private List<ItemSlot> patternSlots(UI ui) {
        var store = element(ui, "pattern_slots", UIElement.class);
        var provider = session == null ? Optional.<FederationPatternProviderBlockEntity>empty() : session.providerEntity();
        IItemHandlerModifiable handler = provider.isPresent()
                ? (IItemHandlerModifiable) provider.get().getLogic().getPatternInv().toItemHandler()
                : new ItemStackHandler(FederationPatternProviderBlockEntity.PATTERN_SLOTS) {
                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) {
                        return PatternDetailsHelper.isEncodedPattern(stack);
                    }
                };
        var slots = new ArrayList<ItemSlot>();
        for (int index = 0; index < FederationPatternProviderBlockEntity.PATTERN_SLOTS; index++) {
            var slot = new FederationPatternSlot();
            slot.setId("pattern_slot_" + index);
            slot.bind(new ItemHandlerSlot(handler, index).setCanPlace(PatternDetailsHelper::isEncodedPattern)
                    .setCanTake(viewer -> session == null || session.isStillValid(viewer)));
            store.addChild(slot);
            slots.add(slot);
        }
        return slots;
    }

    /**
     * One AE2 setting as an icon button that steps through {@code values}. The server reads and writes the Provider's
     * config manager, which also sets every Lane; the client shows what the server last sent and sends its next choice.
     */
    private <T extends Enum<T>> void setting(UI ui, Player player, String id, IGuiTexture icon, Setting<T> setting,
            T[] values, String key) {
        var button = element(ui, id, Button.class);
        FederationIcons.apply(button, icon);
        var shown = new int[] {-1};
        Runnable paint = () -> {
            boolean known = shown[0] >= 0 && shown[0] < values.length;
            var name = known ? values[shown[0]].name().toLowerCase(java.util.Locale.ROOT) : "unknown";
            button.removeClass("selected");
            if (known && settingOn(values[shown[0]])) button.addClass("selected");
            button.style(style -> style.tooltips(tr("setting." + key), tr("setting." + key + "." + name)));
            button.setActive(known);
        };
        paint.run();
        showServerValue(ui, () -> provider().map(entity -> indexOf(values, entity.getConfigManager().getSetting(setting)))
                .orElse(-1), index -> {
                    shown[0] = index;
                    paint.run();
                });
        var ask = request(ui, index -> {
            var entity = provider().orElse(null);
            if (entity == null || index < 0 || index >= values.length || !session.isStillValid(player)) return;
            entity.getConfigManager().putSetting(setting, values[index]);
            entity.saveChanges();
        });
        button.setOnClick(event -> {
            if (shown[0] < 0) return;
            shown[0] = (shown[0] + 1) % values.length;
            paint.run();
            ask.accept(shown[0]);
        });
    }

    /** The server's current value, shown on the client; it never flows back. */
    private static void showServerValue(UI ui, java.util.function.Supplier<Integer> server,
            java.util.function.IntConsumer client) {
        var value = new BindableValue<Integer>(Integer.MIN_VALUE);
        value.bind(DataBindingBuilder.intValS2C(server).initialValue(Integer.MIN_VALUE)
                .remoteSetter(next -> {
                    if (next != null) client.accept(next);
                }).build());
        value.addClass("state-sync");
        ui.rootElement.addChild(value);
    }

    /**
     * What the player asks for, as {@code "<number>:<value>"}, numbered per click or keystroke. The server applies a
     * number once and each side keeps offering its own latest request, so a request echoed back settles instead of
     * bouncing between values in flight. A C2S-only binding cannot be used: LDLib2 writes every binding into a menu's
     * initial data, and a client binding that refuses S2C data aborts reading the rest of it.
     */
    private static java.util.function.IntConsumer request(UI ui, java.util.function.IntConsumer server) {
        var asked = new String[] {""};
        var count = new int[1];
        var applied = new String[] {""};
        var appliedNumber = new long[1];
        var value = new BindableValue<String>("");
        value.bind(DataBindingBuilder.string(() -> applied[0], text -> {
                    var separator = text == null ? -1 : text.indexOf(':');
                    if (separator <= 0) return;
                    try {
                        long number = Long.parseLong(text.substring(0, separator));
                        if (number <= appliedNumber[0]) return;
                        appliedNumber[0] = number;
                        applied[0] = text;
                        server.accept(Integer.parseInt(text.substring(separator + 1)));
                    } catch (NumberFormatException ignored) {
                        // Not a request this screen sends.
                    }
                })
                .initialValue("")
                .remoteGetter(() -> asked[0])
                .remoteSetter(ignored -> {
                })
                .build());
        value.addClass("state-sync");
        ui.rootElement.addChild(value);
        return next -> asked[0] = ++count[0] + ":" + next;
    }

    /** Blocking and Lock crafting are on when they hold crafts back; the terminal setting when it lists the Provider. */
    private static boolean settingOn(Enum<?> value) {
        return value == YesNo.YES || value instanceof LockCraftingMode mode && mode != LockCraftingMode.NONE;
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int index = 0; index < values.length; index++) if (values[index] == value) return index;
        return -1;
    }

    /** The priority field: what the player types is sent once it parses; the server's value shows while not editing. */
    private void priority(UI ui, Player player) {
        var field = element(ui, "provider_priority", TextField.class);
        field.setNumbersOnlyInt(-PRIORITY_LIMIT, PRIORITY_LIMIT);
        field.style(style -> style.tooltips(tr("priority_help")));
        var ask = request(ui, next -> {
            var entity = provider().orElse(null);
            if (entity == null || next < -PRIORITY_LIMIT || next > PRIORITY_LIMIT || !session.isStillValid(player)) return;
            if (entity.getPriority() != next) entity.setPriority(next);
        });
        field.setTextResponder(text -> {
            try {
                ask.accept(Math.max(-PRIORITY_LIMIT, Math.min(PRIORITY_LIMIT, Integer.parseInt(text.strip()))));
            } catch (NumberFormatException ignored) {
                // A half-typed number such as "-" waits for the next key.
            }
        });
        showServerValue(ui, () -> provider().map(FederationPatternProviderBlockEntity::getPriority).orElse(Integer.MIN_VALUE),
                next -> {
                    if (!field.isFocused() && next != Integer.MIN_VALUE) field.setText(Integer.toString(next), false);
                });
    }

    /**
     * The screen's choices, rebuilt at most every {@link #CHOICES_TICKS} ticks: building them decodes every pattern
     * and walks the domain's Endpoints. An accepted action rebuilds them at once.
     */
    private String providerChoices() {
        if (session == null) return "";
        if (choicesCache == null || ++choicesAge >= CHOICES_TICKS) {
            choicesAge = 0;
            choicesCache = session.providerChoices();
        }
        return choicesCache;
    }

    private Optional<FederationPatternProviderBlockEntity> provider() {
        return session == null ? Optional.empty() : session.providerEntity();
    }

    private BindableValue<String> sync(UI ui, java.util.function.Supplier<String> server,
            java.util.function.Consumer<String> client) {
        var value = new BindableValue<String>("");
        value.bind(DataBindingBuilder.stringS2C(server).initialValue("").remoteSetter(client).build());
        value.addClass("state-sync");
        ui.rootElement.addChild(value);
        return value;
    }

    /** The header, the no-face notice, the return buffers and the wires, from the latest choices. */
    private void acceptChoices() {
        var ui = currentUi;
        var names = objects(choices, "networks").stream().map(network -> FederationTopologyView.displayName(
                network.get("id").getAsString(), network.has("name") ? network.get("name").getAsString() : "").getString()).toList();
        var card = objects(choices, "processingProviders").stream().findFirst().orElse(null);
        int networkIndex = card == null || !card.has("networkIndex") ? -1 : card.get("networkIndex").getAsInt();
        var position = choices.has("position") ? choices.get("position").getAsString() : "";
        var where = Component.empty();
        if (networkIndex >= 0 && networkIndex < names.size()) where.append(tr("where_network", names.get(networkIndex)));
        if (!position.isEmpty()) where.append(tr("where_position", position));
        element(ui, "provider_where", Label.class).setText(where);
        boolean face = choices.has("face") && choices.get("face").getAsBoolean();
        boolean stale = choices.has("stale") && choices.get("stale").getAsBoolean();
        var slotChoices = objects(choices, "slot");
        var targets = objects(choices, "target");
        long used = slotChoices.stream().filter(slot -> !slot.get("empty").getAsBoolean()).count();
        int mappings = choices.has("mappings") ? choices.get("mappings").getAsInt() : 0;
        // Without a domain on its Federation face the Provider still has its patterns and keeps its mappings.
        element(ui, "provider_summary", Label.class).setText(face ? tr("summary", used, slotChoices.size(), mappings, targets.size())
                : tr(stale ? "summary_stale" : "summary_noface", used, slotChoices.size(), mappings));
        var notice = element(ui, "provider_noface", UIElement.class);
        notice.setDisplay(!face && !stale);
        element(ui, "provider_noface_text", Label.class).setText(tr("noface.text"));
        element(ui, "provider_noface_kept", Label.class).setText(tr(mappings > 0 ? "noface.kept" : "noface.none", mappings));
        renderReturns(ui, targets);
        var selected = choices.getAsJsonObject("selected");
        graph.accept(slotChoices, targets, selected.has("target") ? selected.get("target").getAsString() : "", position,
                objects(choices, "processingProviders"), names);
    }

    /** One line per bound Lane: its Endpoint, then whether results wait in its buffer or a send is still going. */
    private void renderReturns(UI ui, List<JsonObject> targets) {
        var list = element(ui, "provider_return_list", ScrollerView.class);
        list.clearAllScrollViewChildren();
        var lanes = objects(choices, "lanes");
        if (lanes.isEmpty()) {
            var none = new Label();
            none.addClass("provider-return-where");
            none.setText(tr("returns_none"));
            none.textStyle(style -> style.textColor(FederationTheme.DARK_MUTED));
            none.layout(style -> style.widthPercent(100).height(9));
            list.addScrollViewChild(none);
            return;
        }
        for (var lane : lanes) {
            var row = new UIElement();
            row.addClass("provider-return-row");
            row.setId("provider_return_" + lane.get("lane").getAsInt());
            var target = targets.stream().filter(value -> value.get("id").getAsString().equals(lane.get("endpoint").getAsString()))
                    .findFirst().orElse(null);
            int index = target == null || !target.has("networkIndex") ? -1 : target.get("networkIndex").getAsInt();
            var swatch = new UIElement();
            swatch.addClass("provider-return-swatch");
            swatch.style(style -> style.backgroundTexture(FederationTheme.solid(index < 0 ? FederationTheme.EDGE
                    : FederationTheme.networkAccent(index))));
            var where = new Label();
            where.addClass("provider-return-where");
            where.setText(lane.has("position") ? FederationWorkspace.tr("endpoint_at", lane.get("position").getAsString())
                    : Component.literal(lane.get("endpoint").getAsString().substring(0, 8)));
            var state = new Label();
            state.addClass("provider-return-state");
            state.setId("provider_return_state_" + lane.get("lane").getAsInt());
            var returns = patterns.resources(lane, "returns");
            boolean sending = lane.get("pendingSend").getAsBoolean();
            if (!returns.isEmpty()) {
                var names = returns.stream().map(stack -> stack.what().getDisplayName().getString() + " × "
                        + PatternChoices.amount(stack)).toList();
                state.setText(tr("returns_waiting", String.join(", ", names))
                        .withStyle(Style.EMPTY.withColor(FederationTheme.WARN & 0xffffff)));
            } else {
                state.setText(tr(sending ? "returns_sending" : "returns_empty").withStyle(Style.EMPTY.withColor(
                        (sending ? FederationTheme.TEAL : FederationTheme.DARK_MUTED) & 0xffffff)));
            }
            row.addChildren(swatch, where, state);
            list.addScrollViewChild(row);
        }
    }

    private void prepareRelease() {
        if (authority.authorized()) {
            releaseDialog.prepare(authority.clientSequence());
            send(FederationDomainPolicyAction.PREPARE_RELEASE);
        }
    }

    @Override
    public boolean isStillValid(Player player) {
        return session == null || session.isStillValid(player);
    }

    @Override
    public FederationDomainPolicyActionResult dispatch(ServerPlayer player, ModularUIContainerMenu menu,
            FederationDomainPolicyActionRequest request) {
        var refused = authority.reject(this, menu, request);
        if (refused != null) return refused;
        if (!ACTIONS.contains(request.action())) return FederationDomainPolicyActionResult.WRONG_MENU;
        // Only this Provider's slots and the Endpoints it may map can be chosen here, never another Provider.
        if (request.action() == FederationDomainPolicyAction.SELECT_TARGET && !request.target().startsWith("slot:")
                && !request.target().startsWith("target:")) {
            return FederationDomainPolicyActionResult.INVALID_TARGET;
        }
        if (!session.matchesMappingContext(player, request.context())) {
            return session.rejectStaleContext(player) ? FederationDomainPolicyActionResult.STALE_CONTEXT
                    : FederationDomainPolicyActionResult.WRONG_MENU;
        }
        if (!session.editsOpenedProvider()) return FederationDomainPolicyActionResult.STALE_CONTEXT;
        if (request.action() != FederationDomainPolicyAction.RELEASE_ENDPOINT) session.clearPendingRelease();
        switch (request.action()) {
            case PREPARE_RELEASE, RELEASE_ENDPOINT -> session.releaseEndpoint();
            case CANCEL_RELEASE -> session.cancelRelease();
            case SELECT_TARGET -> {
                if (!session.selectTarget(request.target())) return FederationDomainPolicyActionResult.INVALID_TARGET;
            }
            case SET_MAPPING -> {
                if (!session.setMapping(request.target())) return FederationDomainPolicyActionResult.INVALID_TARGET;
            }
            default -> throw new IllegalStateException("Action outside the Provider screen: " + request.action());
        }
        choicesCache = null;
        authority.advance();
        return FederationDomainPolicyActionResult.ACCEPTED;
    }

    @Override
    public Optional<FederationDomainPolicyActionRequest> currentRequest(ModularUIContainerMenu menu,
            FederationDomainPolicyAction action) {
        return authority.currentRequest(this, menu, action);
    }

    @Override
    public long currentSequence() {
        return authority.sequence();
    }

    @Override
    public String currentMappingStatus() {
        return session == null ? "pending" : session.mappingStatusCode();
    }

    @Override
    public void acceptReply(UUID nonce, UUID requestId, long sequence, FederationDomainPolicyActionResult result) {
        if (authority.acceptReply(nonce, requestId, sequence, result)) renderRequestProgress();
    }

    private void send(FederationDomainPolicyAction action) {
        send(action, "");
    }

    private void send(FederationDomainPolicyAction action, String target) {
        if (authority.send(action, target)) renderRequestProgress();
    }

    /** The lamp and footer: editable while the face's domain is current and no request is in flight. */
    private void renderRequestProgress() {
        if (currentUi == null) return;
        var ui = currentUi;
        boolean pending = authority.pending();
        boolean ready = serverStatus.equals("ready");
        boolean active = ready && authority.authorized();
        graph.setEditable(active && !pending);
        int tone = active ? FederationTheme.OK : serverStatus.equals("pending") || serverStatus.equals("noface")
                ? FederationTheme.WARN : FederationTheme.ERROR;
        element(ui, "sync_lamp", UIElement.class).style(style -> style.backgroundTexture(FederationTheme.solid(tone)));
        var status = element(ui, "ack_status", Label.class);
        var message = element(ui, "request_status", Label.class);
        var rejection = authority.rejection();
        boolean visible = pending || rejection != null;
        message.setDisplay(visible);
        status.setDisplay(!visible);
        status.setText(tr("status." + (active ? "ready" : serverStatus.equals("noface") ? "noface"
                : serverStatus.equals("pending") ? "pending" : "stale")));
        message.removeClass("request-error");
        if (pending) message.setText(Component.translatable("ae2federation.ui.request.pending"));
        else if (rejection != null) {
            message.addClass("request-error");
            message.setText(Component.translatable("ae2federation.ui.request." + rejection.name().toLowerCase(java.util.Locale.ROOT)));
        }
        for (var id : new String[] {"release_confirm", "release_cancel"}) {
            ui.selectId(id, Button.class).forEach(button -> button.setActive(!pending));
        }
    }

    private static List<JsonObject> objects(JsonObject root, String field) {
        if (!root.has(field)) return List.of();
        var values = new ArrayList<JsonObject>();
        root.getAsJsonArray(field).forEach(value -> values.add(value.getAsJsonObject()));
        return values;
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.provider." + key, arguments);
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
