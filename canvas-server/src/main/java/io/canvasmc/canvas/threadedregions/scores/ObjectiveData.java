package io.canvasmc.canvas.threadedregions.scores;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

public class ObjectiveData {
    private Component formattedDisplayName;
    private ObjectiveCriteria.RenderType renderType;
    private boolean displayAutoUpdate;
    private @Nullable NumberFormat numberFormat;
    private Component displayName;

    @Contract("_ -> new")
    public static @NonNull ObjectiveData copyOf(final @NonNull ObjectiveData toCopy) {
        return new ObjectiveData(
            toCopy.getFormattedDisplayName(),
            toCopy.getRenderType(),
            toCopy.doesDisplayAutoUpdate(),
            toCopy.getNumberFormat(),
            toCopy.getDisplayName()
        );
    }

    public ObjectiveData(
        final Component formattedDisplayName,
        final ObjectiveCriteria.RenderType renderType,
        final boolean displayAutoUpdate,
        @Nullable final NumberFormat numberFormat,
        final Component displayName
    ) {
        this.formattedDisplayName = formattedDisplayName;
        this.renderType = renderType;
        this.displayAutoUpdate = displayAutoUpdate;
        this.numberFormat = numberFormat;
        this.displayName = displayName;
    }

    public ObjectiveData() {
        // no-op
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ObjectiveData setDisplayName(final Component displayName) {
        this.displayName = displayName;
        return this;
    }

    public Component getFormattedDisplayName() {
        return formattedDisplayName;
    }

    public ObjectiveData setFormattedDisplayName(final Component formattedDisplayName) {
        this.formattedDisplayName = formattedDisplayName;
        return this;
    }

    public ObjectiveCriteria.RenderType getRenderType() {
        return renderType;
    }

    public ObjectiveData setRenderType(final ObjectiveCriteria.RenderType renderType) {
        this.renderType = renderType;
        return this;
    }

    public boolean doesDisplayAutoUpdate() {
        return displayAutoUpdate;
    }

    public ObjectiveData setDisplayAutoUpdate(final boolean displayAutoUpdate) {
        this.displayAutoUpdate = displayAutoUpdate;
        return this;
    }

    public @Nullable NumberFormat getNumberFormat() {
        return numberFormat;
    }

    public ObjectiveData setNumberFormat(final @Nullable NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
        return this;
    }

    public Objective.Packed pack(final String name, final ObjectiveCriteria criteria) {
        return new Objective.Packed(name, criteria, this.displayName, this.renderType, this.displayAutoUpdate, Optional.ofNullable(this.numberFormat));
    }
}
