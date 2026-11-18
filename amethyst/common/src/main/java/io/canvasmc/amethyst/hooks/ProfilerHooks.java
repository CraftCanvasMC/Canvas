package io.canvasmc.amethyst.hooks;

import org.jspecify.annotations.Nullable;

public final class ProfilerHooks {

    @Nullable
    private static volatile HookSession ACTIVE;

    private ProfilerHooks() {
    }

    @Nullable
    public static HookSession active() {
        return ACTIVE;
    }

    public static synchronized void install(final HookSession session) {
        if (ACTIVE != null) {
            throw new IllegalStateException("A profiler session is already running");
        }

        ACTIVE = session;
    }

    public static synchronized void uninstall(final HookSession session) {
        if (ACTIVE == session) {
            ACTIVE = null;
        }
    }
}
