package io.canvasmc.canvas.util;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a span of time stored as a specific whole number amount of a chrono unit. This is utilized to store time
 * spans and estimate frequencies of events in a more generic fashion in comparison to using ticks for everything,
 * especially given the tick rate can change at runtime as of {@code 1.20.3}.
 * <p>
 * Utilization of this class is intended to be more specific and consistent about time frames rather than relying on
 * ticks always running 20 times per second
 *
 * @author dueris
 */
public class TimeSpan {
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\s*([a-zA-Z]+)$");
    private static final Map<String, ChronoUnit> UNIT_ALIASES;

    static {
        final ImmutableMap.Builder<String, ChronoUnit> map = ImmutableMap.builder();

        map.put("ns", ChronoUnit.NANOS);
        map.put("nano", ChronoUnit.NANOS);
        map.put("nanos", ChronoUnit.NANOS);
        map.put("nanosecond", ChronoUnit.NANOS);
        map.put("nanoseconds", ChronoUnit.NANOS);

        map.put("us", ChronoUnit.MICROS);
        map.put("micro", ChronoUnit.MICROS);
        map.put("micros", ChronoUnit.MICROS);
        map.put("microsecond", ChronoUnit.MICROS);
        map.put("microseconds", ChronoUnit.MICROS);

        map.put("ms", ChronoUnit.MILLIS);
        map.put("milli", ChronoUnit.MILLIS);
        map.put("millis", ChronoUnit.MILLIS);
        map.put("millisecond", ChronoUnit.MILLIS);
        map.put("milliseconds", ChronoUnit.MILLIS);

        map.put("s", ChronoUnit.SECONDS);
        map.put("sec", ChronoUnit.SECONDS);
        map.put("secs", ChronoUnit.SECONDS);
        map.put("second", ChronoUnit.SECONDS);
        map.put("seconds", ChronoUnit.SECONDS);

        map.put("m", ChronoUnit.MINUTES);
        map.put("min", ChronoUnit.MINUTES);
        map.put("mins", ChronoUnit.MINUTES);
        map.put("minute", ChronoUnit.MINUTES);
        map.put("minutes", ChronoUnit.MINUTES);

        map.put("h", ChronoUnit.HOURS);
        map.put("hr", ChronoUnit.HOURS);
        map.put("hrs", ChronoUnit.HOURS);
        map.put("hour", ChronoUnit.HOURS);
        map.put("hours", ChronoUnit.HOURS);

        map.put("d", ChronoUnit.DAYS);
        map.put("day", ChronoUnit.DAYS);
        map.put("days", ChronoUnit.DAYS);

        map.put("w", ChronoUnit.WEEKS);
        map.put("week", ChronoUnit.WEEKS);
        map.put("weeks", ChronoUnit.WEEKS);

        map.put("mo", ChronoUnit.MONTHS);
        map.put("month", ChronoUnit.MONTHS);
        map.put("months", ChronoUnit.MONTHS);

        map.put("y", ChronoUnit.YEARS);
        map.put("yr", ChronoUnit.YEARS);
        map.put("yrs", ChronoUnit.YEARS);
        map.put("year", ChronoUnit.YEARS);
        map.put("years", ChronoUnit.YEARS);

        UNIT_ALIASES = map.build();
    }

    private final ChronoUnit chronoUnit;
    private final long count;
    private final String asString;

    private TimeSpan(final ChronoUnit unit, final long count) {
        this.chronoUnit = unit;
        this.count = count;
        this.asString = count + UNIT_ALIASES.entrySet().stream()
            .filter(entry -> entry.getValue() == chronoUnit)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No alias for unit: " + chronoUnit));
    }

    /**
     * Constructs a new time span from a chrono unit and the amount of said unit type
     *
     * @param unit
     *     the type of unit
     * @param count
     *     the amount of the specified unit
     *
     * @return the constructed time span
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static TimeSpan of(final ChronoUnit unit, final long count) {
        return new TimeSpan(unit, count);
    }

    /**
     * Parses a new time span instance from a raw nullable string, allowing for more generic and lenient input
     *
     * @param str
     *     the string to parse
     *
     * @return the compiled time span
     */
    @Contract("null -> fail")
    public static TimeSpan parse(final @Nullable String str) {
        if (str == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        final String trimmed = str.trim();
        final java.util.regex.Matcher matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid time span format: '" + str + "'");
        }

        final long count = Long.parseLong(matcher.group(1));
        final String unitStr = matcher.group(2).toLowerCase(java.util.Locale.ROOT);

        final ChronoUnit unit = UNIT_ALIASES.get(unitStr);
        if (unit == null) {
            throw new IllegalArgumentException("Unknown time unit: '" + unitStr + "' in '" + str + "'");
        }

        return new TimeSpan(unit, count);
    }

    /**
     * Converts the time unit and the count to nanoseconds
     *
     * @return the time span in nanos
     */
    public long asNanoSpan() {
        return chronoUnit.getDuration().multipliedBy(count).toNanos();
    }

    /**
     * Creates a new {@link java.time.Instant} of the current instant minus the time span, creating an instant targeting
     * the past
     *
     * @return the current instant minus the time span instance
     */
    public Instant inPast() {
        final Instant now = Instant.now();
        return now.minus(count, chronoUnit);
    }

    /**
     * Creates a new {@link java.time.Instant} of the current instant plus the time span, creating an instant targeting
     * the future
     *
     * @return the current instant plus the time span instance
     */
    public Instant inFuture() {
        final Instant now = Instant.now();
        return now.plus(count, chronoUnit);
    }

    /**
     * Gets the current time span as a serializable string in minimal form
     *
     * @return the minimal serialized string
     */
    @Override
    public String toString() {
        return asString;
    }
}
