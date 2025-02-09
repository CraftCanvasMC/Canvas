package io.canvasmc.canvas.server.render;

import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;
import javax.swing.*;

public class MSPTGraph extends JComponent {
    private static final int GRAPH_WIDTH = 350;
    private static final int GRAPH_HEIGHT = 100;
    private final LinkedList<Double> msptData = new LinkedList<>();
    private final Timer timer;
    private String hoverText = "";

    public MSPTGraph() {
        timer = new Timer(50, (_) -> repaint());
        timer.start();

        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = Math.min(e.getX(), msptData.size() - 1);
                if (index >= 0) {
                    hoverText = "MSPT: " + String.format("%.2f", msptData.get(index));
                } else {
                    hoverText = "";
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {}
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(GRAPH_WIDTH, GRAPH_HEIGHT);
    }

    public void update(double latestMspt) {
        if (msptData.size() >= GRAPH_WIDTH) {
            msptData.removeFirst();
        }
        msptData.add(latestMspt);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, GRAPH_WIDTH, GRAPH_HEIGHT);

        int i = 0;
        for (double mspt : msptData) {
            int height = Math.min((int) (mspt * 2), GRAPH_HEIGHT);
            g.setColor(interpolateColor(mspt));
            g.drawLine(i, GRAPH_HEIGHT, i, GRAPH_HEIGHT - height);
            i++;
        }

        g.setColor(Color.BLACK);
        g.drawString(hoverText, 10, 15);
    }

    private @NotNull Color interpolateColor(double mspt) {
        float ratio = (float) Math.min(mspt / 50.0, 1.0);
        int r = (int) (0x00 * (1 - ratio) + 0xFF * ratio);
        int g = (int) (0xFF * (1 - ratio) + 0x00 * ratio);
        return new Color(r, g, 0);
    }

    public void stop() {
        timer.stop();
    }
}
