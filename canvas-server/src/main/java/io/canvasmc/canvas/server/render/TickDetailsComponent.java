package io.canvasmc.canvas.server.render;

import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import java.awt.*;
import javax.swing.*;
import net.minecraft.server.MinecraftServer;

public class TickDetailsComponent extends JPanel {
    private final Timer timer;
    private final MSPTGraph msptGraph;
    private final AverageTickTimeAccessor accessor;

    public TickDetailsComponent(MinecraftServer server, AverageTickTimeAccessor accessor) {
        super(new BorderLayout());

        this.accessor = accessor;
        setOpaque(false);

        msptGraph = new MSPTGraph();

        add(msptGraph, BorderLayout.NORTH);

        timer = new Timer(500, (_) -> msptGraph.update(accessor.getAverageTickTime()));
        timer.start();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(350, 200);
    }

    public void close() {
        timer.stop();
        msptGraph.stop();
    }
}
