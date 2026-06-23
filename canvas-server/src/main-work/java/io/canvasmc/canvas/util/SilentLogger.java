package io.canvasmc.canvas.util;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

public class SilentLogger extends AbstractLogger {

    @Override
    protected String getFullyQualifiedCallerName() {
        return SilentLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(final Level level, final Marker marker, final String messagePattern, final Object[] arguments, final Throwable throwable) {
        // no-op
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return false;
    }
}
