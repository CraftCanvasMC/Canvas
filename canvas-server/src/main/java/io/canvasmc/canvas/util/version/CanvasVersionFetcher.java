package io.canvasmc.canvas.util.version;

import com.destroystokyo.paper.util.VersionFetcher;
import io.canvasmc.canvas.ClientV2;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.util.Util;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.ServerBuildInfoImpl;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import oshi.SystemInfo;

import static net.kyori.adventure.text.Component.text;

/**
 * This is a replacement version fetcher that is intended to replace {@link com.destroystokyo.paper.PaperVersionFetcher}
 * for general simplicity purposes of the rebrand patch(I don't want to replace half of the paper version fetcher,
 * because I'm lazy)
 *
 * @author dueris
 */
@NullMarked
public class CanvasVersionFetcher implements VersionFetcher {

    private static final TextColor RED = TextColor.color(0xFF5300);
    private static final TextColor YELLOW = TextColor.color(0xFFCD00);
    private static final TextColor GREEN = TextColor.color(0x4DE54D);

    private static final TextColor HEADER = TextColor.color(240, 106, 72);
    private static final TextColor PRIMARY = TextColor.color(200, 69, 32);
    private static final TextColor SECONDARY = TextColor.color(242, 144, 110);
    private static final TextColor INFORMATION = TextColor.color(252, 221, 213);
    private static final TextColor LIST = TextColor.color(122, 32, 10);

    private static final AtomicBoolean USE_CACHE = new AtomicBoolean(true);

    @Override
    public long getCacheTime() {
        if (!USE_CACHE.get()) {
            return Long.MIN_VALUE;
        }
        return 720000;
    }

    @Override
    public Component getVersionMessage() {
        return Component.empty();
    }

    @Nullable
    @Override
    public Component getFullOutMessage() {
        final TextComponent.Builder builder = text();
        final ServerBuildInfoImpl buildInfo = (ServerBuildInfoImpl) ServerBuildInfo.buildInfo();

        builder.append(text("[", LIST, TextDecoration.BOLD));

        TextComponent brandHoverText = Component.textOfChildren(
            text(buildInfo.brandName(), HEADER, TextDecoration.BOLD),
            text(" made by ", PRIMARY),
            text(buildInfo.brandVendor().orElse("Unknown Vendor"), HEADER, TextDecoration.BOLD)
        );
        TextComponent brandComponent = text(buildInfo.brandName(), HEADER, TextDecoration.BOLD);
        if (buildInfo.brandWebsite().isPresent()) {
            brandHoverText = brandHoverText.append(
                text("\n"),
                text(buildInfo.brandWebsite().get(), INFORMATION, TextDecoration.UNDERLINED),
                text(" - Click to open", PRIMARY)
            );
            brandComponent = brandComponent.clickEvent(ClickEvent.openUrl(buildInfo.brandWebsite().get()));
        }
        brandComponent = brandComponent.hoverEvent(HoverEvent.showText(brandHoverText));

        builder.append(brandComponent);
        builder.append(text("] ", LIST, TextDecoration.BOLD));
        builder.append(text(buildInfo.minecraftVersionName(), HEADER));
        builder.append(text(" | ", HEADER, TextDecoration.BOLD));

        builder.append(text(buildInfo.gitBranch().orElse("(Unknown Git Branch)"), SECONDARY));

        if (buildInfo.buildNumber().isPresent()) {
            builder.append(text("#", HEADER));
            builder.append(text(buildInfo.buildNumber().getAsInt(), SECONDARY));
            builder.append(text(" [", HEADER));

            String url = "https://github.com/CraftCanvasMC/Canvas/";
            String commit = buildInfo.gitCommit().orElse("Unknown Commit");

            if (buildInfo.gitCommit().isPresent()) {
                url = url + "commit/" + commit;
            }

            builder.append(text(commit, INFORMATION)
                .hoverEvent(HoverEvent.showText(text("Click to view commit", SECONDARY)))
                .clickEvent(ClickEvent.openUrl(url))
            );
            builder.append(text("]", HEADER));
        }
        else {
            builder.append(text("-", HEADER));
            builder.append(text("(NULL-COMMIT)", SECONDARY));
        }

        builder.append(text(" | ", HEADER, TextDecoration.BOLD));

        final Status status = computeStatus();
        if (!status.isError()) {
            // if we passed once, we don't really need to do this again
            // because the data is consistent throughout the runtime
            USE_CACHE.set(false);
        }

        builder.append(status.getStatus());
        builder.append(text("\n"));

        builder.append(text(">> ", LIST, TextDecoration.BOLD));

        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");
        final List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();

        builder.append(text("Java ", PRIMARY));
        builder.append(text(javaSpecVersion, INFORMATION));
        builder.append(text(", (", PRIMARY));
        builder.append(text(javaVendor, INFORMATION));
        builder.append(text(") ", PRIMARY));
        builder.append(text("OS ", PRIMARY));
        builder.append(text(osName + " " + osArch, SECONDARY));
        builder.append(text(" [", PRIMARY));
        builder.append(
            text("hover", SECONDARY).hoverEvent(HoverEvent.showText(
                Component.textOfChildren(
                    text("Running Java ", PRIMARY),
                    text(javaSpecVersion, SECONDARY),
                    text(" (", PRIMARY),
                    text(javaVmName + " " + javaVmVersion, INFORMATION),
                    text("; ", PRIMARY),
                    text(javaVendor + " " + javaVendorVersion, INFORMATION),
                    text(") ", PRIMARY),
                    text("on ", PRIMARY),
                    text(osName + " " + osVersion, INFORMATION),
                    text(" (", PRIMARY),
                    text(osArch, INFORMATION),
                    text(")\n", PRIMARY),
                    text("FLAGS:", PRIMARY),
                    formatList(inputArguments),
                    text("\nClick to copy flags to clipboard", PRIMARY)
                )
            )).clickEvent(ClickEvent.copyToClipboard(String.join(" ", inputArguments)))
        );
        builder.append(text("]\n", PRIMARY));

        builder.append(text(">> ", LIST, TextDecoration.BOLD));
        builder.append(text("Mem ", PRIMARY));

        final long maxMem = Runtime.getRuntime().maxMemory();
        if (maxMem == Long.MAX_VALUE) {
            builder.append(text("MAX-UNDEFINED", INFORMATION));
        }
        else {
            builder.append(text(String.format("%.1f", maxMem / (1024.0 * 1024.0 * 1024.0)), INFORMATION));
            builder.append(text("GB ", PRIMARY));
        }

        builder.append(text("CPU ", PRIMARY));
        builder.append(text(new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName(), SECONDARY));
        builder.append(text(" (", PRIMARY));
        builder.append(text(Runtime.getRuntime().availableProcessors(), INFORMATION));
        builder.append(text(")", PRIMARY));

        return builder.build();
    }

    private Status computeStatus() {
        final ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();
        final OptionalInt buildNumber = buildInfo.buildNumber();

        if (buildNumber.isEmpty()) {
            return new LocalStatus();
        }

        final int localNum = buildNumber.getAsInt();
        try {
            ClientV2.Build build = Util.CANVAS_CLIENT.getLatestBuild(buildInfo.minecraftVersionId(), true);
            final int distance = build.buildNumber() - localNum;

            return switch (GlobalConfiguration.getBuildStatus()) {
                case LOCAL -> new LocalStatus();
                case STABLE -> new StableStatus(distance);
                case EXPERIMENTAL -> new BetaStatus(distance);
                case UNKNOWN -> new ErrorStatus();
            };
        } catch (Throwable thrown) {
            GlobalConfiguration.LOGGER.error("Error parsing version information from CanvasMC's Jenkins API", thrown);
            return new ErrorStatus();
        }
    }

    private static TextComponent formatList(final List<String> inputArguments) {
        final TextComponent.Builder builder = text();

        builder.append(text("[", LIST));
        for (int i = 0; i < inputArguments.size(); i++) {
            final String arg = inputArguments.get(i);
            builder.append(text(arg, INFORMATION));
            if (i != (inputArguments.size() - 1)) {
                builder.append(text(", ", SECONDARY));
            }
        }
        builder.append(text("]", LIST));

        return builder.build();
    }

    private interface Status {
        Component getStatus();

        boolean isError();
    }

    private static class ErrorStatus implements Status {
        @Contract(value = " -> new", pure = true)
        @Override
        public Component getStatus() {
            return text("ERROR", RED, TextDecoration.BOLD);
        }

        @Override
        public boolean isError() {
            return true;
        }
    }

    private static class LocalStatus implements Status {
        @Contract(value = " -> new", pure = true)
        @Override
        public Component getStatus() {
            return text("DEV", RED, TextDecoration.BOLD);
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    private record BetaStatus(int distance) implements Status {
        @Contract(value = " -> new", pure = true)
        @Override
        public Component getStatus() {
            TextComponent base = text("BETA", YELLOW, TextDecoration.BOLD);
            if (distance > 0) {
                base = base.hoverEvent(HoverEvent.showText(text("You are " + distance + " builds out of date, please update ASAP!", YELLOW)));
            }
            else {
                base = base.hoverEvent(HoverEvent.showText(text("You are on the latest version! :)", GREEN)));
            }
            return base;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    private record StableStatus(int distance) implements Status {
        @Contract(value = " -> new", pure = true)
        @Override
        public Component getStatus() {
            TextComponent base = text("STABLE", GREEN, TextDecoration.BOLD);
            if (distance > 0) {
                base = base.hoverEvent(HoverEvent.showText(text("You are " + distance + " builds out of date, please update ASAP!", YELLOW)));
            }
            else {
                base = base.hoverEvent(HoverEvent.showText(text("You are on the latest version! :)", GREEN)));
            }
            return base;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }
}
