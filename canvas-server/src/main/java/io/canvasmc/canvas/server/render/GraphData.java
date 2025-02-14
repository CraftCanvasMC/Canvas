package io.canvasmc.canvas.server.render;

import java.awt.*;
import org.jetbrains.annotations.NotNull;

public record GraphData(double mspt) {

    public int getMsptHeight() {
        return (int) Math.min(mspt * 2, 100);
    }

    public @NotNull Color getFillColor() {
        return getColor(mspt);
    }

    public Color getLineColor() {
        return getColor(mspt).darker();
    }

    private @NotNull Color getColor(double mspt) {
        if (mspt <= 20) return new Color(0x00FF00);
        if (mspt <= 40) return new Color(0xFFFF00);
        return new Color(0xFF0000);
    }
}
