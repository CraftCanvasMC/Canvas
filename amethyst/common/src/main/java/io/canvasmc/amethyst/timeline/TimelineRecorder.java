package io.canvasmc.amethyst.timeline;

import io.canvasmc.amethyst.AmethystUtils;
import io.canvasmc.amethyst.ProfileClock;
import io.canvasmc.amethyst.proto.Handle;
import io.canvasmc.amethyst.proto.MetricRow;
import io.canvasmc.amethyst.proto.RegionEvent;
import io.canvasmc.amethyst.proto.RegionSections;
import io.canvasmc.amethyst.proto.RegionSnapshot;
import io.canvasmc.amethyst.proto.Timeline;
import io.canvasmc.amethyst.proto.World;
import io.canvasmc.amethyst.table.IndexTable;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class TimelineRecorder implements RegionListener {

    private final ProfileClock clock;
    private final LongSupplier nanoTime;
    private final int sectionShift;

    @Nullable
    private final FocusArea focus;

    // area mode: every handle the area was in, in order, and the same as a set for lookups
    private final LongArrayList focusChain = new LongArrayList();
    private final LongOpenHashSet followed = new LongOpenHashSet();
    private final IndexTable<String> worlds = new IndexTable<>();
    private final IndexTable<String> metricNames = new IndexTable<>();
    private final List<RegionEvent> events = new ObjectArrayList<>();
    private final List<RegionSnapshot> snapshots = new ObjectArrayList<>();
    private final List<MetricRow> metricRows = new ObjectArrayList<>();
    private final Long2ObjectOpenHashMap<@Nullable HandleTicks> ticks = new Long2ObjectOpenHashMap<>();

    // the focus split, and the new region holding the area isn't created yet
    private boolean focusSplitting;

    public TimelineRecorder(
        final ProfileClock clock,
        final LongSupplier nanoTime,
        final int sectionShift,
        @Nullable
        final FocusArea focus
    ) {
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.sectionShift = sectionShift;
        this.focus = focus;
        if (focus != null) {
            follow(focus.handle());
        }
    }

    public TimelineRecorder(
        final ProfileClock clock,
        final int sectionShift,
        @Nullable
        final FocusArea focus
    ) {
        this(clock, System::nanoTime, sectionShift, focus);
    }

    @Override
    public synchronized void created(final String world, final long handle, final long[] sections) {
        // the focus split, and this new region holds the area, so it's the focus now
        final boolean holdsArea =
            focusSplitting
            && focus != null
            && focus.world().equals(world)
            && contains(sections, focus.anchorSection(sectionShift));

        if (holdsArea) {
            focusSplitting = false;
            follow(handle);
        }

        ticks(handle, world);
        record(RegionEvent.Type.TYPE_CREATE, world, handle, new long[0], sections);
    }

    @Override
    public synchronized void destroyed(final String world, final long handle) {
        final HandleTicks dead = ticks.get(handle);
        if (dead != null) {
            dead.finish(nanoTime.getAsLong());
        }

        record(RegionEvent.Type.TYPE_DESTROY, world, handle, new long[0], null);
    }

    @Override
    public synchronized void active(final String world, final long handle) {
        record(RegionEvent.Type.TYPE_ACTIVE, world, handle, new long[0], null);
    }

    @Override
    public synchronized void inactive(final String world, final long handle) {
        record(RegionEvent.Type.TYPE_INACTIVE, world, handle, new long[0], null);
    }

    @Override
    public synchronized void merged(
        final String world,
        final long from,
        final long into,
        final long[] survivorSections
    ) {
        if (focus != null && current() == from) {
            follow(into);
        }

        final RegionEvent.Builder event = event(RegionEvent.Type.TYPE_MERGE, world, from, new long[]{into});
        if (event != null) {
            events.add(event.addSections(sections(into, survivorSections)).build());
        }
    }

    @Override
    public synchronized void split(final String world, final long from, final long[] into) {
        if (focus != null && current() == from) {
            focusSplitting = true;
        }

        // the new regions sections come with their created calls
        record(RegionEvent.Type.TYPE_SPLIT, world, from, into, null);
    }

    public synchronized HandleTicks ticks(
        final long handle,
        @Nullable
        final String world
    ) {
        HandleTicks existing = ticks.get(handle);
        if (existing == null) {
            existing = new HandleTicks(clock, handle, world == null ? -1 : worlds.add(world), nanoTime.getAsLong());
            ticks.put(handle, existing);
        }

        return existing;
    }

    public synchronized void snapshot(final String world, final Collection<Region> regions) {
        final long timeUs = clock.sinceStartUs(nanoTime.getAsLong());
        final RegionSnapshot.Builder snapshot = RegionSnapshot.newBuilder()
            .setTimeUs(timeUs)
            .setWorld(worlds.add(world));
        for (final Region region : regions) {
            if (focus == null || region.handle() == current()) {
                snapshot.addRegions(sections(region.handle(), region.sections()));
                metricRows.add(metricRow(timeUs, region.handle(), region.metrics()).build());
            }
        }

        // area mode and the focus isnt in this world
        if (snapshot.getRegionsCount() > 0) {
            snapshots.add(snapshot.build());
        }
    }

    public synchronized void globalMetrics(final Object2LongMap<String> metrics) {
        if (keeps(0L)) {
            metricRows.add(metricRow(clock.sinceStartUs(nanoTime.getAsLong()), 0L, metrics).build());
        }
    }

    public synchronized boolean keeps(final long handle) {
        return focus == null || followed.contains(handle);
    }

    public LongPredicate keepHandle() {
        return this::keeps;
    }

    public synchronized LongList focusChain() {
        return new LongArrayList(focusChain);
    }

    public synchronized Timeline write() {
        final Timeline.Builder out = Timeline.newBuilder().setSectionShift(sectionShift);
        for (final String world : worlds.unwritten()) {
            out.addWorlds(World.newBuilder().setKey(world));
        }

        out.addAllMetricNames(metricNames.unwritten());

        // area mode still drains the handles it drops, so they dont pile up
        final ObjectIterator<@Nullable HandleTicks> iterator = ticks.values().iterator();
        while (iterator.hasNext()) {
            final HandleTicks handle = Objects.requireNonNull(iterator.next());
            final Handle written = handle.write();
            if (keeps(handle.id())) {
                out.addHandles(written);
            }

            if (handle.finished()) {
                iterator.remove();
            }
        }

        out.addAllEvents(events)
            .addAllSnapshots(snapshots)
            .addAllMetricRows(metricRows);

        events.clear();
        snapshots.clear();
        metricRows.clear();

        return out.build();
    }

    private long current() {
        return focusChain.getLong(focusChain.size() - 1);
    }

    private void follow(final long handle) {
        focusChain.add(handle);
        followed.add(handle);
    }

    private void record(
        final RegionEvent.Type type,
        final String world,
        final long handle,
        final long[] related,
        final long @Nullable [] sections
    ) {
        final RegionEvent.Builder event = event(type, world, handle, related);
        if (event != null) {
            if (sections != null) {
                event.addSections(sections(handle, sections));
            }
            events.add(event.build());
        }
    }

    // null when area mode drops it, neither side of the event is in the focus lineage
    private RegionEvent.@Nullable Builder event(
        final RegionEvent.Type type,
        final String world,
        final long handle,
        final long[] related
    ) {
        if (focus != null && !followed.contains(handle) && !anyFollowed(related)) {
            return null;
        }

        final RegionEvent.Builder event = RegionEvent.newBuilder()
            .setTimeUs(clock.sinceStartUs(nanoTime.getAsLong()))
            .setType(type)
            .setWorld(worlds.add(world))
            .setHandle(handle);
        for (final long other : related) {
            event.addRelated(other);
        }

        return event;
    }

    private boolean anyFollowed(final long[] handles) {
        for (final long handle : handles) {
            if (followed.contains(handle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(final long[] sections, final long section) {
        for (final long owned : sections) {
            if (owned == section) {
                return true;
            }
        }
        return false;
    }

    private MetricRow.Builder metricRow(final long timeUs, final long handle, final Object2LongMap<String> metrics) {
        final int[] indices = new int[metrics.size()];
        final long[] values = new long[metrics.size()];

        int count = 0;
        int width = 0;
        for (final Object2LongMap.Entry<String> metric : metrics.object2LongEntrySet()) {
            indices[count] = metricNames.add(metric.getKey());
            values[count] = metric.getLongValue();
            width = Math.max(width, indices[count] + 1);
            count++;
        }

        final long[] aligned = new long[width];
        for (int i = 0; i < count; i++) {
            aligned[indices[i]] = values[i];
        }

        final MetricRow.Builder row = MetricRow.newBuilder()
            .setTimeUs(timeUs)
            .setHandle(handle);
        for (final long value : aligned) {
            row.addValues(value);
        }

        return row;
    }

    private static RegionSections sections(final long handle, final long[] sections) {
        final RegionSections.Builder out = RegionSections.newBuilder().setHandle(handle);
        for (final long section : sections) {
            out.addSectionX(AmethystUtils.keyX(section)).addSectionZ(AmethystUtils.keyZ(section));
        }

        return out.build();
    }

    public record Region(long handle, long[] sections, Object2LongMap<String> metrics) { }
}
