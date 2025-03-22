package io.canvasmc.canvas.server;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

public class ShutdownThread extends TickThread {
    private DedicatedServer server;
    public ShutdownThread(DedicatedServer server) {
        super("Shutdown Thread");
        this.server = server;
    }

    @Override
    public void run() {
        // break tick, stop server.
        if (!server.isRunning()) {
            try {
                MinecraftServer.LOGGER.info("Stopping server");
                this.server.stopped = true;
                this.server.stopServer();
            } catch (Throwable throwable3) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable3);
            } finally {
                if (this.server.services.profileCache() != null) {
                    this.server.services.profileCache().clearExecutor();
                }
            }
        }
    }
}
