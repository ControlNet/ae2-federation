package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import space.controlnet.ae2federation.identity.NetworkId;

/** Explicit operator recovery for real transit whose original network no longer exists. No identities/policies move. */
public final class ReturnRecoveryCommands {
    private ReturnRecoveryCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2federation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("returns")
                        .then(Commands.literal("list").executes(context -> {
                            var ledger = CraftingReturnLedger.get(context.getSource().getLevel());
                            for (var network : ledger.transitNetworks()) {
                                for (var entry : ledger.transit(network)) {
                                    var message = network.value() + " transit " + entry.getKey() + " " + entry.getLongValue();
                                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                                }
                            }
                            for (var debt : ledger.entries()) {
                                var message = debt.executing().value() + " -> " + debt.consumer().value() + " "
                                        + debt.key() + " owed=" + debt.amount() + " held=" + debt.held()
                                        + " unknownCpu=" + ledger.hasUnknownJobs(debt.consumer());
                                context.getSource().sendSuccess(() -> Component.literal(message), false);
                            }
                            return ledger.entries().size() + ledger.transitNetworks().size();
                        }))
                        .then(Commands.literal("recover-transit")
                                .then(Commands.argument("network", StringArgumentType.word())
                                        .then(Commands.argument("destination", BlockPosArgument.blockPos())
                                                .executes(context -> {
                                                    var source = context.getSource();
                                                    var level = source.getLevel();
                                                    var position = BlockPosArgument.getLoadedBlockPos(context, "destination");
                                                    NetworkId network;
                                                    try {
                                                        network = new NetworkId(UUID.fromString(
                                                                StringArgumentType.getString(context, "network")));
                                                    } catch (IllegalArgumentException invalid) {
                                                        source.sendFailure(Component.literal("Invalid executing network UUID"));
                                                        return 0;
                                                    }
                                                    if (!(level.getBlockEntity(position) instanceof IActionHost host)
                                                            || host.getActionableNode() == null) {
                                                        source.sendFailure(Component.literal("Destination must be a loaded ME block"));
                                                        return 0;
                                                    }
                                                    var grid = host.getActionableNode().getGrid();
                                                    var ledger = CraftingReturnLedger.get(level);
                                                    var target = HeldReturnTransfer.ownStorage(grid);
                                                    long moved = 0;
                                                    for (var entry : ledger.transit(network)) {
                                                        var key = entry.getKey();
                                                        // Reserve the actual saved stock before calling a potentially reentrant target.
                                                        long reserved = ledger.takeTransit(network, key, entry.getLongValue(), Actionable.MODULATE);
                                                        long accepted = 0;
                                                        try {
                                                            accepted = target.insert(key, reserved, Actionable.MODULATE, IActionSource.empty());
                                                            moved += accepted;
                                                        } finally {
                                                            ledger.addTransit(network, key, reserved - accepted);
                                                        }
                                                    }
                                                    long delivered = moved;
                                                    source.sendSuccess(() -> Component.literal("Recovered " + delivered
                                                            + " transit units from " + network.value() + "; rejected stock remains saved"), true);
                                                    return moved > 0 ? 1 : 0;
                                                }))))));
    }
}
