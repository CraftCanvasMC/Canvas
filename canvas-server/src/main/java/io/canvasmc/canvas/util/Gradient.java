package io.canvasmc.canvas.util;

import java.util.Arrays;
import java.util.Collections;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NonNull;

public final class Gradient {
    private final boolean negativePhase;
    private final TextColor[] colors;
    private int index = 0;
    private int colorIndex = 0;
    private float factorStep = 0;
    private float phase;

    public Gradient(final @NonNull TextColor... colors) {
        this(0, colors);
    }

    public Gradient(final float phase, final @NonNull TextColor @NonNull ... colors) {
        if (colors.length < 2) {
            throw new IllegalArgumentException("Gradients must have at least two colors! colors=" + Arrays.toString(colors));
        }
        if (phase > 1.0 || phase < -1.0) {
            throw new IllegalArgumentException(String.format("Phase must be in range [-1, 1]. '%s' is not valid.", phase));
        }
        this.colors = colors;
        if (phase < 0) {
            this.negativePhase = true;
            this.phase = 1 + phase;
            Collections.reverse(Arrays.asList(this.colors));
        } else {
            this.negativePhase = false;
            this.phase = phase;
        }
    }

    public void length(final int size) {
        this.colorIndex = 0;
        this.index = 0;
        final int sectorLength = size / (this.colors.length - 1);
        this.factorStep = 1.0f / sectorLength;
        this.phase = this.phase * sectorLength;
    }

    public @NonNull TextColor nextColor() {
        if (this.factorStep * this.index > 1) {
            this.colorIndex++;
            this.index = 0;
        }

        float factor = this.factorStep * (this.index++ + this.phase);
        // loop around if needed
        if (factor > 1) {
            factor = 1 - (factor - 1);
        }
        if (this.negativePhase && this.colors.length % 2 != 0) {
            // flip the gradient segment for to allow for looping phase -1 through 1
            return this.interpolate(this.colors[this.colorIndex + 1], this.colors[this.colorIndex], factor);
        } else {
            return this.interpolate(this.colors[this.colorIndex], this.colors[this.colorIndex + 1], factor);
        }
    }

    private @NonNull TextColor interpolate(final @NonNull TextColor color1, final @NonNull TextColor color2, final float factor) {
        return TextColor.color(
            Math.round(color1.red() + factor * (color2.red() - color1.red())),
            Math.round(color1.green() + factor * (color2.green() - color1.green())),
            Math.round(color1.blue() + factor * (color2.blue() - color1.blue()))
        );
    }
}
