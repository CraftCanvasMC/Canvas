package io.canvasmc.canvas.server;

import java.nio.file.Path;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;

public class TickLoopConstantsUtils {

    public static void hardCrashCatch(Throwable throwable) {
        //noinspection removal
        if (throwable instanceof ThreadDeath) {
            MinecraftServer.LOGGER.error("World thread terminated by WatchDog due to hard crash", throwable);
            return;
        }
        MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
        CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable);
        MinecraftServer server = MinecraftServer.getServer();

        server.fillSystemReport(crashreport.getSystemReport());
        Path path = server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

        if (crashreport.saveToFile(path, ReportType.CRASH)) {
            MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
        } else {
            MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
        }

        server.onServerCrash(crashreport);

        try {
            server.stopped = true;
            server.stopServer();
        } catch (Throwable throwable3) {
            MinecraftServer.LOGGER.error("Exception stopping the server(via level thread)", throwable3);
        } finally {
            if (server.services.profileCache() != null) {
                server.services.profileCache().clearExecutor();
            }
        }
    }
}
