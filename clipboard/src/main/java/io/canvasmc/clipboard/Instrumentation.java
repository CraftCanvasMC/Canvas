package io.canvasmc.clipboard;

public class Instrumentation {
    public static java.lang.instrument.Instrumentation INSTRUMENTATION = null;

    public static void premain(final String arguments, final java.lang.instrument.Instrumentation instrumentation) {
        Instrumentation.agentmain(arguments, instrumentation);
    }

    public static void agentmain(final String arguments, final java.lang.instrument.Instrumentation instrumentation) {
        if (Instrumentation.INSTRUMENTATION == null) Instrumentation.INSTRUMENTATION = instrumentation;
        if (Instrumentation.INSTRUMENTATION == null)
            throw new NullPointerException("Unable to get instrumentation instance!");
    }
}
