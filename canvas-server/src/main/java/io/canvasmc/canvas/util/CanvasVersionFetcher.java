package io.canvasmc.canvas.util;

import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import static net.kyori.adventure.text.Component.text;

/**
 * This is a replacement version fetcher that is intended to replace {@link com.destroystokyo.paper.PaperVersionFetcher}
 * for general simplicity purposes of the rebrand patch(I don't want to replace half of the paper version fetcher, because I'm lazy)
 *
 * @author dueris
 */
public class CanvasVersionFetcher implements VersionFetcher {
    public static final ApiClient CLIENT = new ApiClient();
    public static final ServerBuildInfo BUILD_INFO = ServerBuildInfo.buildInfo();

    private static final ComponentLogger LOGGER = ComponentLogger.logger(BUILD_INFO.brandName() + "VersionProvider");

    private static final int DISTANCE_ERROR = -1;
    private static final int DISTANCE_UNKNOWN = -2;
    private static final int IS_LOCAL = -3;

    private static final @NotNull TextColor RED = TextColor.color(0xFF5300);
    private static final @NotNull TextColor WARN = TextColor.color(0xFFFF00);
    private static final @NotNull TextColor GREEN = TextColor.color(0x4DE54D);

    private static final @NotNull String DOWNLOAD_PAGE = "https://canvasmc.io/downloads";
    private static final @NotNull Component NEW_LINE = text("\n");

    public CanvasVersionFetcher() {
    }

    @Override
    public long getCacheTime() {
        return 720000;
    }

    @Override
    public @NonNull Component getVersionMessage() {
        return getResponseFromVal(fetchStatus());
    }

    private int fetchStatus() {
        if (BUILD_INFO.buildNumber().isEmpty()) {
            return IS_LOCAL;
        }
        final int localNum = BUILD_INFO.buildNumber().getAsInt();
        try {
            ApiClient.Build build = CLIENT.getLatestBuildForVersion(BUILD_INFO.minecraftVersionId(), true);
            if (build == null) {
                LOGGER.error("Unable to locate build for version {}", BUILD_INFO.minecraftVersionId());
                return DISTANCE_UNKNOWN;
            }
            return build.buildNumber() - localNum;
        } catch (Throwable thrown) {
            LOGGER.error(text("Error parsing version information from CanvasMC's Jenkins API", RED), thrown);
            return DISTANCE_ERROR;
        }
    }

    private @NonNull Component getResponseFromVal(int i) {
        return switch (i) {
            case DISTANCE_ERROR -> text("Error fetching version information for " + BUILD_INFO.brandName(), RED);
            case DISTANCE_UNKNOWN -> text("Unknown response, can't connect?", RED);
            case IS_LOCAL -> text("You are running a development version without access to version information", RED);
            case 0 -> text("You are running the latest version", GREEN);
            default -> {
                if (i > 0) {
                    yield Component.text("You are " + i + " builds behind", WARN)
                        .append(NEW_LINE)
                        .append(Component.text("You can download the latest version from " + DOWNLOAD_PAGE));
                }
                yield text("Unknown response code '" + i + "'", RED);
            }
        };
    }
}
