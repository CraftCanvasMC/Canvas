package io.canvasmc.canvas.server.network;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.ThreadedTickDiagnosis;
import io.canvasmc.canvas.server.AbstractTickLoop;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinThread extends AbstractTickLoop<TickThread, PlayerJoinThread> {
    private static PlayerJoinThread INSTANCE = null;
    private final ConcurrentLinkedQueue<Connection> activeConnections = new ConcurrentLinkedQueue<>();

    public PlayerJoinThread(final String name, final String debugName) {
        super(name, debugName);
        this.setThreadModifier((tickThread) -> {
            tickThread.setName(this.name());
            tickThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            tickThread.setUncaughtExceptionHandler((_, exception) -> LOGGER.error("Uncaught exception in player join thread", exception));
        });
        INSTANCE = this;
        LOGGER.info("Loaded PlayerJoinThread to server context");
    }

    public static PlayerJoinThread getInstance() {
        return INSTANCE;
    }

    public void stopAcceptingConnections() {
        this.ticking = false;
    }

    public void run() {
        Iterator<Connection> iterator = this.activeConnections.iterator();

        while (iterator.hasNext()) {
            Connection connection = iterator.next();
            if (connection.getPhase().equals(ConnectionHandlePhases.PLAY)) {
                iterator.remove();
                continue;
            }
            if (!connection.isConnecting()) {
                if (connection.isConnected()) {
                    try {
                        connection.tick();
                    } catch (Exception var7) {
                        if (connection.isMemoryConnection()) {
                            throw new ReportedException(CrashReport.forThrowable(var7, "Ticking memory connection"));
                        }

                        LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(MinecraftServer.getServer().logIPs()), var7);
                        Component component = Component.literal("Internal server error");
                        connection.send(new ClientboundDisconnectPacket(component), PacketSendListener.thenRun(() -> connection.disconnect(component)));
                        connection.setReadOnly();
                    }
                } else {
                    if (connection.preparing) continue;
                    iterator.remove();
                    connection.handleDisconnection();
                }
            }
        }
    }

    public void add(@NotNull Connection connection) {
        setPhase(connection, ConnectionHandlePhases.JOIN);
        this.activeConnections.add(connection);
    }

    public void setPhase(@NotNull Connection connection, ConnectionHandlePhases phase) {
        connection.setPhase(phase);
    }

    public ServerTickRateManager tickRateManager() {
        return MinecraftServer.getServer().tickRateManager();
    }

    @Override
    public net.kyori.adventure.text.@NotNull Component debugInfo() {
        return net.kyori.adventure.text.Component.text()
            .append(net.kyori.adventure.text.Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(net.kyori.adventure.text.Component.text("Actively Processing: ", ThreadedTickDiagnosis.PRIMARY))
            .append(net.kyori.adventure.text.Component.text(this.activeConnections.size(), ThreadedTickDiagnosis.INFORMATION))
            .build();
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }
}
