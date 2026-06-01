package io.canvasmc.canvas.world;

import com.google.common.base.Preconditions;
import com.mojang.logging.LogUtils;
import io.canvasmc.canvas.util.ServerLocation;
import io.canvasmc.canvas.util.SilentLogger;
import io.papermc.paper.threadedregions.RegionShutdownThread;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.event.player.PlayerKickEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

public class WorldShutdownThread extends RegionShutdownThread {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final String debugName;
    private final ServerLevel level;
    private final Supplier<List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>>> theWaiter;

    private static void teleportPlayer(
        final @NonNull ServerPlayer entityPlayer,
        final @NonNull ServerLocation target
    ) {
        Preconditions.checkNotNull(target.pos(), "Target position not defined");
        entityPlayer.teleportAsync(
            target.level(),
            target.pos(),
            // note: yaw and pitch CAN be null in the ServerLocation instance, this is ok, this is handled
            //       by the teleportAsync function
            target.yaw(), target.pitch(),
            null, // don't care about velocity
            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN,
            net.minecraft.world.entity.Entity.TELEPORT_FLAG_LOAD_CHUNK,
            null, // we don't need a callback
            false // do not call events for this, too unsafe
        );
    }

    public WorldShutdownThread(
        final @NonNull ServerLevel level,
        final Supplier<List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>>> theWaiter
    ) {
        final String worldName = level.dimension().identifier().toDebugFileName();

        super(worldName + " shutdown thread");
        this.setUncaughtExceptionHandler((_, thrown) -> {
            LOGGER.error("Error shutting down world {}", worldName, thrown);
            MinecraftServer.getServer().stopServer();
        });

        this.debugName = worldName;
        this.level = level;
        this.theWaiter = theWaiter;

        // we set this because frankly, we don't want to log everything
        // super.LOGGER = new SilentLogger(); // TODO - we just want errors only
    }

    private String getWorldName() {
        return debugName;
    }

    @Override
    public void run() {
        LOGGER.info("Awaiting termination of {} regions", getWorldName());
        final List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>>
            regions = theWaiter.get();
        LOGGER.info("Beginning shutdown of {}", getWorldName());

        // we have to do the following in the following order:

        // - halt world chunk system

        // we do teleports and save players swapped from shutdown thread
        // because we have to complete all teleport requests first

        // - finish pending teleports
        // - process players

        // - save chunks
        // - save pearls
        // - save level data
        // - close saved data storage
        // - remove world from maps
        // - complete ticket callback

        // halting the chunk system comes before pending teleports in
        // the region shutdown thread, so we mimic that here

        LOGGER.info("Halting chunk system for {}", getWorldName());

        try {
            // we don't need to try and soft halt the chunk system first,
            // this does the exact same thing, but waits 60s
            haltChunkSystem(this.level);
        } catch (final Throwable thrown) {
            LOGGER.error("Failed to halt chunk system for {}", getWorldName(), thrown);
            throw thrown;
        }

        boolean processedAnyTeleports = false;

        for (final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region : regions) {
            processedAnyTeleports |= finishTeleportations(region, this.level);
        }

        if (processedAnyTeleports) {
            LOGGER.info("Finished pending teleports in {}", getWorldName());
        }

        // cover a race condition with portaling and teleporting
        // that way we don't get players stuck in limbo ever

        // first we check world-local players, then all players

        int fixedPlayers = 0;

        for (ServerPlayer localPlayer : this.level.players()) {
            io.canvasmc.canvas.util.ServerLocation lastTeleportPos = localPlayer.canvas$lastTeleportOrigin;

            // inc fixed players, we are fixing any players that are in this list
            fixedPlayers += 1;

            if (lastTeleportPos == null) {
                LOGGER.warn("Player doesn't contain teleport origin, kicking");
                localPlayer.connection.disconnect(Component.literal("Destination world is unloading"), PlayerKickEvent.Cause.UNKNOWN);
                continue;
            }

            // the teleport origin methods should never have null return values
            teleportPlayer(localPlayer, lastTeleportPos);
        }

        for (final ServerPlayer entityPlayer : MinecraftServer.getServer().getPlayerList().players) {
            ServerLocation teleportingTo = entityPlayer.canvas$teleportingTo;
            ServerLocation teleportingFrom = entityPlayer.canvas$lastTeleportOrigin;

            if (teleportingTo != null && teleportingFrom != null && teleportingTo.level() == this.level) {
                // someone is trying to teleport here... no

                if (entityPlayer.isRemoved()) {
                    teleportPlayer(entityPlayer, teleportingFrom);
                }
                else {
                    // if the player isn't removed yet, they are still processing
                    // the initial teleport/portal, so we should delay this a bit
                    RegionizedServer.getInstance().addTask(() -> {
                        teleportPlayer(entityPlayer, teleportingFrom);
                    });
                }

                fixedPlayers += 1;
            }
        }

        if (fixedPlayers > 0) {
            LOGGER.info("Fixed {} players for unload", fixedPlayers);
        }

        // we don't need to do inventory closing, there should be no players here
        // so we skip to saving the regions in the world

        try {
            for (int i = 0; i < regions.size(); i++) {
                final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>
                    region = regions.get(i);
                saveRegionChunks(region, (i + 1) == regions.size());
            }
        } catch (final Throwable thrown) {
            LOGGER.error("Failed to save chunks in {}", getWorldName(), thrown);
            throw thrown;
        } finally {
            LOGGER.info("Saved chunks in {}", getWorldName());
        }

        // now we save level data and force pearl data save

        MinecraftServer.getServer().pearls.save(null);
        saveLevelData(this.level);
        this.level.chunkSource.getDataStorage().close();

        LOGGER.info("Saved {} level data", getWorldName());

        RegionizedServer.getInstance().worlds.remove(this.level);
        MinecraftServer.getServer().removeLevel(this.level);
        MinecraftServer.getServer().server.worlds.remove(this.level.getWorld().getName().toLowerCase(java.util.Locale.ROOT));

        LOGGER.info("Completed {} unload", getWorldName());

        this.level.canvas$unloadTicket.getOrThrow().callback().accept(io.canvasmc.canvas.WorldUnloadResult.SUCCESS);
    }
}
