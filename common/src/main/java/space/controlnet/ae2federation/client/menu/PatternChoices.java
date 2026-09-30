package space.controlnet.ae2federation.client.menu;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the patterns and resources of a synchronized choices payload: a pattern slot's name, its first output as an
 * item for its row, and its outputs and inputs with exact amounts. Decoded resources are cached per payload.
 */
final class PatternChoices {
    private final Map<JsonObject, List<GenericStack>> resourceCache = new java.util.IdentityHashMap<>();

    /** A new payload replaces every choice object, so the old cache entries are dropped with it. */
    void reset() {
        resourceCache.clear();
    }

    /** A pattern's outputs and inputs with exact amounts, for the wires view's pattern detail and search. */
    List<Component> facts(JsonObject value) {
        var lines = new ArrayList<Component>();
        for (var output : resources(value, "outputs")) lines.add(FederationWorkspace.tr("output_resource",
                output.what().getDisplayName(), amount(output)));
        for (var input : resources(value, "inputs")) lines.add(FederationWorkspace.tr("input_resource",
                input.what().getDisplayName(), amount(input)));
        return lines;
    }

    List<GenericStack> resources(JsonObject choice, String field) {
        var array = choice.getAsJsonArray(field);
        if (array == null || array.isEmpty()) return List.of();
        // Cache each immutable resource object for the lifetime of this synchronized snapshot.
        return array.asList().stream().map(value -> resourceCache.computeIfAbsent(value.getAsJsonObject(), resource -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return List.<GenericStack>of();
            var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            return GenericStack.CODEC.parse(ops, resource).result().map(List::of).orElseGet(List::of);
        })).flatMap(List::stream).toList();
    }

    static String amount(GenericStack stack) {
        return stack.what().formatAmount(stack.amount(), AmountFormat.FULL);
    }

    ItemStack outputStack(JsonObject choice) {
        var outputs = resources(choice, "outputs");
        if (outputs.isEmpty()) return ItemStack.EMPTY;
        var output = outputs.getFirst();
        return output.what() instanceof AEItemKey item ? item.toStack() : GenericStack.wrapInItemStack(output);
    }

    Component name(JsonObject choice) {
        if (choice.get("empty").getAsBoolean()) return FederationWorkspace.tr("empty_slot");
        var outputs = resources(choice, "outputs");
        return outputs.isEmpty() ? Component.literal(choice.get("label").getAsString()) : outputs.getFirst().what().getDisplayName();
    }
}
