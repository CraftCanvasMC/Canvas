package io.canvasmc.amethyst;

public final class ProfilerException extends RuntimeException {

    public ProfilerException(final String message) {
        super(message);
    }

    public ProfilerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
