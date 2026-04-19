package me.xssmusashi.farsight.world;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.file.Path;

/**
 * Derives a stable cache directory path for the currently-connected world.
 * Single-player uses the save's level name; multiplayer derives a
 * filesystem-safe key from the server address. Kept free of direct
 * {@code IntegratedServer} imports (its package has shifted across MC
 * releases) — relies only on {@code Minecraft} and {@code MinecraftServer}
 * public surface via {@code var}.
 */
public final class WorldIdentifier {
    private WorldIdentifier() {}

    public static String resolveWorldId(Minecraft mc, ClientPacketListener handler) {
        try {
            var sp = mc.getSingleplayerServer();
            if (sp != null) {
                String name = sp.getWorldData().getLevelName();
                if (name != null && !name.isBlank()) {
                    return "singleplayer/" + sanitise(name);
                }
            }
        } catch (Throwable ignored) {
            // fall through to multiplayer path
        }
        ServerData sd = mc.getCurrentServer();
        String addr = (sd != null && sd.ip != null)
            ? sd.ip
            : String.valueOf(handler.getConnection().getRemoteAddress());
        return "server/" + sanitise(addr);
    }

    public static Path cacheDirFor(String worldId) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve("farsight-cache").resolve(worldId);
    }

    private static String sanitise(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
