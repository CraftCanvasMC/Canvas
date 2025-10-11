package io.canvasmc.canvas.spark.profiler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.lucko.spark.paper.common.sampler.ThreadGrouper;
import me.lucko.spark.paper.proto.SparkSamplerProtos;

/**
 * A {@link ThreadGrouper} implementation that strips the {@code (x#)} pattern
 * from thread names in Spark profiler reports.
 *
 * @author dueris
 */
public class ByName implements ThreadGrouper {
    private static final Pattern PATTERN = Pattern.compile("^(.*?)[-# ]+\\d+$");
    private final Map<Long, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> seen = new ConcurrentHashMap<>();

    public String getGroup(long threadId, String threadName) {
        String cached = this.cache.get(threadId);
        if (cached != null) {
            return cached;
        } else {
            Matcher matcher = PATTERN.matcher(threadName);
            if (!matcher.matches()) {
                return threadName;
            } else {
                String group = matcher.group(1).trim();
                this.cache.put(threadId, group);
                this.seen.computeIfAbsent(group, (g) -> ConcurrentHashMap.newKeySet()).add(threadId);
                return group;
            }
        }
    }

    public String getLabel(String group) {
        return group;
    }

    public SparkSamplerProtos.SamplerMetadata.DataAggregator.ThreadGrouper asProto() {
        return me.lucko.spark.paper.proto.SparkSamplerProtos.SamplerMetadata.DataAggregator.ThreadGrouper.BY_POOL;
    }
}
