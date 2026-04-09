package io.canvasmc.canvas.util.version;

import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jspecify.annotations.NonNull;

import static net.kyori.adventure.text.Component.text;

/**
 * This is a replacement version fetcher that is intended to replace {@link com.destroystokyo.paper.PaperVersionFetcher}
 * for general simplicity purposes of the rebrand patch(I don't want to replace half of the paper version fetcher,
 * because I'm lazy)
 *
 * @author dueris
 */
public class CanvasVersionFetcher implements VersionFetcher {
    private static final int DISTANCE_ERROR = -1;
    private static final int DISTANCE_UNKNOWN = -2;
    private static final int IS_LOCAL = -3;
    private static final @NonNull TextColor RED = TextColor.color(0xFF5300);
    private static final @NonNull TextColor WARN = TextColor.color(0xFFCD00);
    private static final @NonNull TextColor GREEN = TextColor.color(0x4DE54D);
    private static final @NonNull URL DOWNLOAD_URL;
    private static final @NonNull Component NEW_LINE = text("\n");
    public static final ApiClient CLIENT = ApiClient.getClientFor("canvas");
    public static final ServerBuildInfo BUILD_INFO = ServerBuildInfo.buildInfo();
    private static final ComponentLogger LOGGER = ComponentLogger.logger(BUILD_INFO.brandName() + "VersionProvider");

    static {
        try {
            DOWNLOAD_URL = URI.create("https://canvasmc.io/downloads/").toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't create download url", e);
        }
    }

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
        final OptionalInt buildNumber = BUILD_INFO.buildNumber();
        if (buildNumber.isEmpty() || buildNumber.getAsInt() == -1) {
            return IS_LOCAL;
        }
        final int localNum = buildNumber.getAsInt();
        try {
            ApiClient.Build build = CLIENT.getLatestBuild(BUILD_INFO.minecraftVersionId(), true);
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
                        .append(Component.text("You can download the latest version from the ").append(
                            Component.text("downloads page").clickEvent(ClickEvent.openUrl(DOWNLOAD_URL)).decorate(TextDecoration.UNDERLINED)
                        ));
                }
                yield text("Unknown response code '" + i + "'", RED);
            }
        };
    }
}
