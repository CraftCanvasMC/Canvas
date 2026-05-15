package io.canvasmc.canvas.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Score;
import org.jspecify.annotations.Nullable;

public final class ReadOnlyScore implements ReadOnlyScoreInfo {
    private final Score score;

    public ReadOnlyScore(Score score) {
        this.score = score;
    }

    @Override
    public int value() {
        return score.value();
    }

    @Override
    public boolean isLocked() {
        return score.isLocked();
    }

    @Override
    public @Nullable NumberFormat numberFormat() {
        return score.numberFormat();
    }
}
