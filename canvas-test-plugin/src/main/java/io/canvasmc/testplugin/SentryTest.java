package io.canvasmc.testplugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SentryTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SentryTest.class);

    public static void run() {
        final RuntimeException exception = new RuntimeException("Example Exception");
        LOGGER.error("Test Sentry exception", exception);
    }

}
