package me.xssmusashi.farsight.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.ingest.ChunkIngestor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** {@code /farsight <stats|rebuild>} — lightweight client-side diagnostics. */
public final class FarsightCommand {
    private FarsightCommand() {}

    public static void register(Supplier<ChunkIngestor> ingestorSupplier) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            FarsightCommand.registerRoot(dispatcher, ingestorSupplier));
    }

    private static void registerRoot(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            Supplier<ChunkIngestor> ingestorSupplier) {
        dispatcher.register(
            ClientCommands.literal("farsight")
                .then(ClientCommands.literal("stats").executes(ctx -> runStats(ctx, ingestorSupplier.get())))
                .then(ClientCommands.literal("rebuild").executes(ctx -> runRebuild(ctx, ingestorSupplier.get())))
        );
    }

    private static int runStats(CommandContext<FabricClientCommandSource> ctx, ChunkIngestor ingestor) {
        String summary = ingestor == null ? "<no active session>" : ingestor.stats().summary();
        ctx.getSource().sendFeedback(Component.literal("[Farsight] " + summary));
        return 1;
    }

    private static int runRebuild(CommandContext<FabricClientCommandSource> ctx, ChunkIngestor ingestor) {
        ctx.getSource().sendFeedback(Component.literal("[Farsight] rebuild not implemented yet (Phase 4 stub)"));
        FarsightClient.LOGGER.info("rebuild requested — no-op in alpha");
        return 1;
    }
}
