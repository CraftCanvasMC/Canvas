package io.canvasmc.canvas.server.render;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.gui.MinecraftServerGui;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class TickTimesGraphDisplay extends JComponent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TITLE = "Tick Times - Minecraft server";
    public static TickTimesGraphDisplay INSTANCE = null;
    final AtomicBoolean isClosing = new AtomicBoolean();
    private final DedicatedServer server;
    private final Collection<Runnable> finalizers = Lists.newArrayList();

    private TickTimesGraphDisplay(DedicatedServer server, List<AverageTickTimeAccessor> accessors) {
        this.server = server;
        this.setPreferredSize(new Dimension(854, 480));
        this.setLayout(new GridLayout(3, 2, 5, 5)); // 3 rows, 2 columns, with spacing

        try {
            for (AverageTickTimeAccessor accessor : accessors) {
                this.add(this.buildGraphPanel(accessor));
            }

            for (int i = accessors.size(); i < 6; i++) {
                this.add(new JPanel());
            }
        } catch (Exception e) {
            LOGGER.error("Couldn't build tick graph GUI", e);
        }
    }

    public static void showFrameFor(final DedicatedServer server, List<AverageTickTimeAccessor> accessors) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        final JFrame jFrame = new JFrame(TITLE);
        final TickTimesGraphDisplay gui = new TickTimesGraphDisplay(server, accessors);
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.add(gui);
        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
        jFrame.setName(TITLE);
        try {
            jFrame.setIconImage(javax.imageio.ImageIO.read(TickTimesGraphDisplay.class.getClassLoader().getResourceAsStream("tick_graph.png")));
        } catch (java.io.IOException ignored) {
        }
        jFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!gui.isClosing.getAndSet(true)) {
                    server.halt(true);
                    gui.runFinalizers();
                    MinecraftServerGui.INSTANCE.runFinalizers();
                }
            }
        });
        gui.addFinalizer(jFrame::dispose);
        INSTANCE = gui;
    }

    public void addFinalizer(Runnable finalizer) {
        this.finalizers.add(finalizer);
    }

    private @NotNull JComponent buildGraphPanel(AverageTickTimeAccessor accessor) {
        JPanel panel = new JPanel(new BorderLayout());
        TickDetailsComponent statsComponent = new TickDetailsComponent(this.server, accessor);
        this.finalizers.add(statsComponent::close);
        panel.add(statsComponent);
        panel.setBorder(new TitledBorder(new EtchedBorder(), accessor.getName()));
        return panel;
    }

    public void close() {
        if (!this.isClosing.getAndSet(true)) {
            this.runFinalizers();
        }
    }

    void runFinalizers() {
        this.finalizers.forEach(Runnable::run);
    }
}
