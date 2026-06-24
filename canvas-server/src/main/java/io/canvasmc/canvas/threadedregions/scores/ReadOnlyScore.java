package io.canvasmc.canvas.threadedregions.scores;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Score;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ReadOnlyScore implements ReadOnlyScoreInfo {
    private final int value;
    private final boolean locked;
    private final NumberFormat numberFormat;

    public ReadOnlyScore(final @NonNull Score score) {
        this.value = score.value();
        this.locked = score.isLocked();
        this.numberFormat = score.numberFormat();
    }

    @Override
    public int value() {
        return value;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public @Nullable NumberFormat numberFormat() {
        return numberFormat;
    }
}
