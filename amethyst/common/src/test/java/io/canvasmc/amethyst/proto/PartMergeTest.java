package io.canvasmc.amethyst.proto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class PartMergeTest {

    private static Report part(final Samples samples, final Timeline timeline) {
        return Report.newBuilder()
            .setFormatVersion(1)
            .addSections(Section.newBuilder().setId("samples").setData(samples.toByteString()))
            .addSections(Section.newBuilder().setId("timeline").setData(timeline.toByteString()))
            .build();
    }

    private static ByteString join(final Report report, final String id) {
        ByteString data = ByteString.EMPTY;
        for (final Section section : report.getSectionsList()) {
            if (section.getId().equals(id)) {
                data = data.concat(section.getData());
            }
        }
        return data;
    }

    @Test
    void concatenatedPartsMerge() throws InvalidProtocolBufferException {
        final Report first = part(
            Samples.newBuilder()
                .addStrings("tick")
                .setCpu(SampleSet.newBuilder().setEnabled(true).addTimeUsDelta(10).addTimeUsDelta(5)
                    .addPhase(SampleSet.Phase.PHASE_TICK).addPhase(SampleSet.Phase.PHASE_TASKS))
                .build(),
            Timeline.newBuilder()
                .addMetricNames("entities")
                .addHandles(Handle.newBuilder().setId(1).setKind(Handle.Kind.KIND_REGION).addTickDurationUs(50))
                .build()
        );
        final Report second = part(
            Samples.newBuilder()
                .addStrings("tasks")
                .setCpu(SampleSet.newBuilder().addTimeUsDelta(20).addPhase(SampleSet.Phase.PHASE_NONE))
                .build(),
            Timeline.newBuilder()
                .addHandles(Handle.newBuilder().setId(1).addTickDurationUs(70))
                .build()
        );

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(first.toByteArray());
        out.writeBytes(second.toByteArray());
        final Report merged = Report.parseFrom(out.toByteArray());

        assertEquals(1, merged.getFormatVersion());
        assertEquals(4, merged.getSectionsCount());

        final Samples samples = Samples.parseFrom(join(merged, "samples"));
        assertEquals(List.of("tick", "tasks"), samples.getStringsList());
        assertTrue(samples.getCpu().getEnabled());
        assertEquals(List.of(10L, 5L, 20L), samples.getCpu().getTimeUsDeltaList());
        assertEquals(
            List.of(SampleSet.Phase.PHASE_TICK, SampleSet.Phase.PHASE_TASKS, SampleSet.Phase.PHASE_NONE),
            samples.getCpu().getPhaseList()
        );

        final Timeline timeline = Timeline.parseFrom(join(merged, "timeline"));
        assertEquals(List.of("entities"), timeline.getMetricNamesList());
        assertEquals(2, timeline.getHandlesCount());
        assertEquals(1, timeline.getHandles(1).getId());
    }

    @Test
    void frameTypesAreAsyncProfilerPlusOne() {
        assertEquals(Frame.Type.TYPE_INTERPRETED, Frame.Type.forNumber(1));
        assertEquals(Frame.Type.TYPE_C1, Frame.Type.forNumber(7));
    }
}
