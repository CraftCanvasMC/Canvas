/*
 * This file is part of Pufferfish (https://github.com/pufferfish-gg/Pufferfish)
 *
 * Pufferfish is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Pufferfish is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Pufferfish. If not, see <https://www.gnu.org/licenses/>.
 */

package gg.pufferfish.pufferfish.sentry;

import io.canvasmc.canvas.Config;
import io.sentry.Sentry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SentryManager {

    private static final Logger logger = LogManager.getLogger(SentryManager.class);

    private SentryManager() {

    }

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        Level logLevel = null;
        for (Level element : Level.values()) {
            if (element.name().equalsIgnoreCase(Config.INSTANCE.sentry.logLevel)) {
                logLevel = element;
                break;
            }
        }
        if (logLevel == null) {
            logger.error("Invalid log level, defaulting to ERROR.");
            logLevel = Level.ERROR;
        }
        try {
            initialized = true;

            Sentry.init(options -> {
                options.setDsn(Config.INSTANCE.sentry.dsn);
                options.setMaxBreadcrumbs(100);
            });

            PufferfishSentryAppender appender = new PufferfishSentryAppender(logLevel);
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
            logger.info("Sentry logging started!");
        } catch (Exception e) {
            logger.warn("Failed to initialize sentry!", e);
            initialized = false;
        }
    }

}
