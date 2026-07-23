package io.canvasmc.canvas.util;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import io.canvasmc.canvas.ClientV2;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import static net.kyori.adventure.text.Component.text;

/**
 * Generic utilities for Canvas
 */
public class Util {
    /**
     * The API client for the {@code canvas} project slug
     */
    public static final ClientV2 CANVAS_CLIENT = ClientV2.getClientFor("canvas");

    private static final ThreadLocal<XoroshiroRandomSource> XOROSHIRO = ThreadLocal.withInitial(() -> new XoroshiroRandomSource(0L, 0L));
    private static final ThreadLocal<SingleThreadedRandomSource> SIMPLE = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(0L));

    /**
     * This method is derived from C2ME as part of the aquifer optimizations
     *
     * @author ishland
     */
    public static void derive(final PositionalRandomFactory deriver, final RandomSource random, final int x, final int y, final int z) {
        if (deriver instanceof final XoroshiroRandomSource.XoroshiroPositionalRandomFactory deriver1) {
            final Xoroshiro128PlusPlus implementation = ((XoroshiroRandomSource) random).randomNumberGenerator;
            //noinspection deprecation
            implementation.seedLo = (Mth.getSeed(x, y, z) ^ deriver1.seedLo);
            implementation.seedHi = (deriver1.seedHi);
        }
        else if (deriver instanceof final LegacyRandomSource.LegacyPositionalRandomFactory deriver1) {
            final SingleThreadedRandomSource random1 = (SingleThreadedRandomSource) random;
            //noinspection deprecation
            random1.setSeed(Mth.getSeed(x, y, z) ^ deriver1.seed);
        }
        else throw new IllegalArgumentException("unsupported deriver");
    }

    /**
     * This method is derived from C2ME as part of the aquifer optimizations
     *
     * @author ishland
     */
    public static RandomSource getRandom(final PositionalRandomFactory deriver) {
        if (deriver instanceof XoroshiroRandomSource.XoroshiroPositionalRandomFactory) {
            return new XoroshiroRandomSource(0L, 0L);
        }
        if (deriver instanceof LegacyRandomSource.LegacyPositionalRandomFactory) {
            return new SingleThreadedRandomSource(0L);
        }
        throw new IllegalArgumentException("unsupported deriver");
    }

    /**
     * This method is derived from C2ME as part of the moonrise executor rewrite fixes
     *
     * @author ishland
     */
    public static <T> T joinFuture(final CompletableFuture<T> future) {
        while (!future.isDone()) {
            LockSupport.parkNanos("Waiting for future", 100_000L);
        }
        return future.join();
    }

    /**
     * Waits for the future to be done, or for the wait period to be up
     *
     * @param future
     *     the future to wait for
     * @param unit
     *     the time unit
     * @param wait
     *     low long(based on the time unit) to wait
     *
     * @return {@code true} if the future completed before the timeout, {@code false} otherwise
     *
     * @author dueris
     */
    public static boolean waitFor(final CompletableFuture<Void> future, final TimeUnit unit, final long wait) {
        final long waitInNanos = unit.toNanos(wait);
        final long targetNanos = System.nanoTime() + waitInNanos;
        while (!future.isDone()) {
            final long remaining = targetNanos - System.nanoTime();
            if (remaining <= 0) break;

            LockSupport.parkNanos("Waiting for future", Math.min(remaining, 1_000_000L));
        }
        return future.isDone();
    }

    /**
     * Removes the files from the provided directory if the path predicate passes
     *
     * @param directory
     *     the directory to search
     * @param removeIf
     *     the predicate to test with
     *
     * @throws java.lang.IllegalArgumentException
     *     if the file provided is not a directory
     */
    public static void removeDirectoryContentsIf(final File directory, final Predicate<Path> removeIf) {
        Preconditions.checkArgument(directory.isDirectory(), "File provided was not a directory");
        try (final Stream<Path> stream = Files.walk(directory.toPath(), 1)) {
            final List<Path> collected = stream.filter(p -> !p.equals(directory.toPath())).toList();
            for (final Path path : collected) {
                if (Files.isRegularFile(path) && removeIf.test(path)) {
                    Files.delete(path);
                }
            }
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't clear directory contents", ioe);
        }
    }

    /**
     * Constructs a Kyori text component with a gradient based off the text colors provided
     *
     * @param textContent
     *     the raw text content of the component
     * @param style
     *     the nullable style specifications to be applied before the gradient colors are applied
     * @param colors
     *     the colors to build a gradient from
     *
     * @return the compiled Kyori component with the color gradient text
     */
    public static Component gradient(final String textContent, final @Nullable Consumer<Style.Builder> style, final TextColor... colors) {
        final Gradient gradient = new Gradient(colors);
        final TextComponent.Builder builder = text();
        if (style != null) {
            builder.style(style);
        }
        final char[] content = textContent.toCharArray();
        gradient.length(content.length);
        for (final char c : content) {
            builder.append(text(c, gradient.nextColor()));
        }
        return builder.build();
    }

    /**
     * Gets the level name used internally by Canvas, this does not match Bukkit. This matches the
     * {@link net.minecraft.resources.Identifier#toDebugFileName()} return value, but removes the {@code minecraft_}
     * part at the start of the string if the namespace is
     * {@link net.minecraft.resources.Identifier#DEFAULT_NAMESPACE}.
     *
     * @param level
     *     the level
     *
     * @return the level name used by Canvas internals
     */
    public static String getLevelName(final Level level) {
        final Identifier dimensionId = level.dimension().identifier();
        final String dimensionName = dimensionId.toDebugFileName();
        if (dimensionId.getNamespace().equalsIgnoreCase(Identifier.DEFAULT_NAMESPACE)) {
            return dimensionName.substring((Identifier.DEFAULT_NAMESPACE + "_").length());
        }
        return dimensionName;
    }

    /**
     * Gets the world name used internally by Canvas, this does not match Bukkit. This matches the
     * {@link net.minecraft.resources.Identifier#toDebugFileName()} return value, but removes the {@code minecraft_}
     * part at the start of the string if the namespace is
     * {@link net.minecraft.resources.Identifier#DEFAULT_NAMESPACE}.
     *
     * @param world
     *     the world
     *
     * @return the world name used by Canvas internals
     */
    public static String getWorldName(final World world) {
        final Identifier dimensionId = Identifier.parse(world.key().asString());
        final String dimensionName = dimensionId.toDebugFileName();
        if (dimensionId.getNamespace().equalsIgnoreCase(Identifier.DEFAULT_NAMESPACE)) {
            return dimensionName.substring((Identifier.DEFAULT_NAMESPACE + "_").length());
        }
        return dimensionName;
    }

    /**
     * Capitalizes the first character in the text
     *
     * @param text
     *     the text to capitalize
     *
     * @return the capitalized text
     */
    public static String capitalize(final String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Translates the provided text from snake case to camel case
     *
     * @param text
     *     the text to convert
     *
     * @return the text in camel case
     */
    public static String snakeToCamelCase(final String text) {
        if (text.isEmpty()) {
            return text;
        }

        final String[] parts = text.split("_");
        final StringBuilder result = new StringBuilder(parts[0].toLowerCase());

        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(capitalize(parts[i]));
            }
        }

        return result.toString();
    }

    /**
     * Formats scheduled time between the base nanoseconds and the target nanoseconds into a readable string to the
     * largest whole unit
     *
     * @param baseNanos
     *     the base nanoseconds
     * @param targetNanos
     *     the target nanoseconds
     *
     * @return the pretty-printed scheduled time
     *
     * @implNote If the target is less than the base, the output will result in saying {@code <unit> ago}.
     *     Otherwise, it will output in the future tense, {@code in <unit>}
     */
    public static String formatTargetTime(final long baseNanos, final long targetNanos) {
        final long diffNanos = targetNanos - baseNanos;
        final boolean past = diffNanos < 0;
        final long abs = Math.abs(diffNanos);
        final String amount = formatNanosToLargestWholeUnit(abs);

        return past ? amount + " ago" : "in " + amount;
    }

    /**
     * Formats the provided nanoseconds to the largest decimal unit as a string
     *
     * @param nanos
     *     the amount of nanoseconds to convert
     *
     * @return the converted nanoseconds to the largest decimal unit. e.g. "5.3s", "10.1ms", etc.
     */
    public static String formatNanosToLargestDecimalUnit(final long nanos) {
        if (nanos < 1_000L)
            return truncateToSecondDecimal(nanos) + "ns";

        if (nanos < 1_000_000_000L) {
            final double ms = nanos / 1_000_000D;
            return truncateToSecondDecimal(ms) + "ms";
        }

        final double seconds = nanos / 1_000_000_000D;

        if (seconds < 60)
            return truncateToSecondDecimal(seconds) + "s";

        if (seconds < 3_600)
            return pluralDecimal(seconds / 60, "minute");

        if (seconds < 86_400)
            return pluralDecimal(seconds / 3_600, "hour");

        return pluralDecimal(seconds / 86_400, "day");
    }

    /**
     * Formats the provided nanoseconds to the largest whole unit as a string
     *
     * @param nanos
     *     the amount of nanoseconds to convert
     *
     * @return the converted nanoseconds to the largest whole unit. e.g. "5s", "10ms", etc.
     */
    public static String formatNanosToLargestWholeUnit(final long nanos) {
        if (nanos < 1_000L)
            return nanos + "ns";

        if (nanos < 1_000_000_000L) {
            final long ms = nanos / 1_000_000L;
            return ms + "ms";
        }

        final long seconds = nanos / 1_000_000_000L;

        if (seconds < 60)
            return seconds + "s";

        if (seconds < 3_600)
            return pluralWhole(seconds / 60, "minute");

        if (seconds < 86_400)
            return pluralWhole(seconds / 3_600, "hour");

        return pluralWhole(seconds / 86_400, "day");
    }

    /**
     * Similar to a generic thread-check from {@link ca.spottedleaf.moonrise.common.util.TickThread}, however this
     * checks for a specific handle instead of data associated with the handle like a location
     *
     * @param handle
     *     the schedulable handle to check for
     * @param reason
     *     the reason if this fails
     */
    public static void ensureScheduleHandle(final TickRegionScheduler.RegionScheduleHandle handle, final String reason) {
        if (handle != TickRegionScheduler.getCurrentTickingTask()) {
            throw new IllegalStateException(reason);
        }
    }

    /**
     * Allows checking if a boolean flag is enabled leniently. This allows for the user to just declare the flag like
     * {@code -DCanvas.test} instead of {@code -DCanvas.test=true} for example. This accepts both of those, defaulting
     * to {@code true} when the {@code =<value>} isn't present.
     *
     * @param flag
     *     the property to search for
     *
     * @return the leniently parsed property
     */
    public static boolean isFlagEnabled(final String flag) {
        final String property = System.getProperty(flag);
        return property != null && (property.isEmpty() || Boolean.parseBoolean(property));
    }

    public static <A> A getEitherOrNull(final Either<A, A> either) {
        final Optional<A> l = either.left();
        final Optional<A> r = either.right();
        return l.orElseGet(r::get);
    }

    private static String pluralDecimal(final double value, final String unit) {
        return truncateToSecondDecimal(value) + " " + unit + (value == 1 ? "" : "s");
    }

    private static String pluralWhole(final long value, final String unit) {
        return value + " " + unit + (value == 1 ? "" : "s");
    }

    private static String truncateToSecondDecimal(final double value) {
        return String.format("%.2f", value);
    }

    @ApiStatus.Internal
    private static final class Gradient {
        private final boolean negativePhase;
        private final TextColor[] colors;
        private int index = 0;
        private int colorIndex = 0;
        private float factorStep = 0;
        private float phase;

        private Gradient(final TextColor... colors) {
            this(0, colors);
        }

        private Gradient(final float phase, final TextColor... colors) {
            if (colors.length < 2) {
                throw new IllegalArgumentException("Gradients must have at least two colors! colors=" + Arrays.toString(colors));
            }
            if (phase > 1.0 || phase < -1.0) {
                throw new IllegalArgumentException(String.format("Phase must be in range [-1, 1]. '%s' is not valid.", phase));
            }
            this.colors = colors;
            if (phase < 0) {
                this.negativePhase = true;
                this.phase = 1 + phase;
                Collections.reverse(Arrays.asList(this.colors));
            }
            else {
                this.negativePhase = false;
                this.phase = phase;
            }
        }

        private void length(final int size) {
            this.colorIndex = 0;
            this.index = 0;
            final int sectorLength = size / (this.colors.length - 1);
            this.factorStep = 1.0f / sectorLength;
            this.phase = this.phase * sectorLength;
        }

        private TextColor nextColor() {
            if (this.factorStep * this.index > 1) {
                this.colorIndex++;
                this.index = 0;
            }

            float factor = this.factorStep * (this.index++ + this.phase);
            // loop around if needed
            if (factor > 1) {
                factor = 1 - (factor - 1);
            }
            if (this.negativePhase && this.colors.length % 2 != 0) {
                // flip the gradient segment for to allow for looping phase -1 through 1
                return this.interpolate(this.colors[this.colorIndex + 1], this.colors[this.colorIndex], factor);
            }
            else {
                return this.interpolate(this.colors[this.colorIndex], this.colors[this.colorIndex + 1], factor);
            }
        }

        private TextColor interpolate(final TextColor color1, final TextColor color2, final float factor) {
            return TextColor.color(
                Math.round(color1.red() + factor * (color2.red() - color1.red())),
                Math.round(color1.green() + factor * (color2.green() - color1.green())),
                Math.round(color1.blue() + factor * (color2.blue() - color1.blue()))
            );
        }
    }
}
