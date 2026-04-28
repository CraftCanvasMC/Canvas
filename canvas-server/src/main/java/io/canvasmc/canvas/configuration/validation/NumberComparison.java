package io.canvasmc.canvas.configuration.validation;

import io.canvasmc.canvas.configuration.Part;

public record NumberComparison(Type type, float... n) implements Part.Validation<Number> {

    @Override
    public void validate(final Number number) {
        if (number == null) throw new IllegalArgumentException("Value must not be null");

        final float val = number.floatValue();

        switch (type) {
            case GREATER_THAN -> {
                if (val <= n[0])
                    throw new IllegalArgumentException("Expected value > " + n[0] + ", got " + val);
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                if (val < n[0])
                    throw new IllegalArgumentException("Expected value >= " + n[0] + ", got " + val);
            }
            case LESS_THAN -> {
                if (val >= n[0])
                    throw new IllegalArgumentException("Expected value < " + n[0] + ", got " + val);
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                if (val > n[0])
                    throw new IllegalArgumentException("Expected value <= " + n[0] + ", got " + val);
            }
            case EQUAL -> {
                if (val != n[0])
                    throw new IllegalArgumentException("Expected value == " + n[0] + ", got " + val);
            }
            case BETWEEN -> {
                if (n.length < 2)
                    throw new IllegalStateException("BETWEEN requires two bounds, only one was provided");
                if (val < n[0] || val > n[1])
                    throw new IllegalArgumentException("Expected value between " + n[0] + " and " + n[1] + ", got " + val);
            }
        }
    }

    public enum Type {
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL_TO,
        BETWEEN,
        EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL_TO
    }
}
