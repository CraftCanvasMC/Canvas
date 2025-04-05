package io.canvasmc.canvas.server.network;

import io.canvasmc.canvas.command.ThreadedServerHealthDump;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinThread extends TickScheduler.FullTick<PlayerJoinThread.TickHandle> {
    private static PlayerJoinThread INSTANCE = null;
    private final ConcurrentLinkedQueue<Connection> activeConnections = new ConcurrentLinkedQueue<>();

    public PlayerJoinThread(DedicatedServer server) {
        super(server, ResourceLocation.fromNamespaceAndPath("canvas", "join_thread"), new TickHandle());
        INSTANCE = this;
        MinecraftServer.LOGGER.info("Loaded player join thread to server context");
    }

    public static PlayerJoinThread getInstance() {
        return INSTANCE;
    }

    public static class TickHandle implements WrappedTick {
        @Override
        public boolean blockTick(final WrappedTickLoop loop, final BooleanSupplier hasTimeLeft, final int tickCount) {
            PlayerJoinThread thisAsTickable = (PlayerJoinThread) loop;
            Iterator<Connection> iterator = thisAsTickable.activeConnections.iterator();

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

                            MinecraftServer.LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(MinecraftServer.getServer().logIPs()), var7);
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
            return true;
        }
    }

    public void add(@NotNull Connection connection) {
        setPhase(connection, ConnectionHandlePhases.JOIN);
        this.activeConnections.add(connection);
    }

    public void setPhase(@NotNull Connection connection, ConnectionHandlePhases phase) {
        connection.setPhase(phase);
    }

    @Override
    public net.kyori.adventure.text.@NotNull Component debugInfo() {
        return net.kyori.adventure.text.Component.text()
            .append(net.kyori.adventure.text.Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(net.kyori.adventure.text.Component.text("Actively Processing: ", ThreadedServerHealthDump.PRIMARY))
            .append(net.kyori.adventure.text.Component.text(this.activeConnections.size(), ThreadedServerHealthDump.INFORMATION))
            .build();
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }
}
