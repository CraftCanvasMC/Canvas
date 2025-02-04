package io.canvasmc.canvas.util.structure;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import net.minecraft.Util;
import net.minecraft.core.FrontAndTop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class GeneralUtils {
    private GeneralUtils() {}

    // More optimized with checking if the jigsaw blocks can connect
    public static boolean canJigsawsAttach(StructureTemplate.@NotNull JigsawBlockInfo jigsaw1, StructureTemplate.@NotNull JigsawBlockInfo jigsaw2) {
        FrontAndTop prop1 = jigsaw1.info().state().getValue(JigsawBlock.ORIENTATION);
        FrontAndTop prop2 = jigsaw2.info().state().getValue(JigsawBlock.ORIENTATION);

        return prop1.front() == prop2.front().getOpposite() &&
            (prop1.top() == prop2.top() || isRollableJoint(jigsaw1, prop1)) &&
            getStringMicroOptimised(jigsaw1.info().nbt(), "target").equals(getStringMicroOptimised(jigsaw2.info().nbt(), "name"));
    }

    private static boolean isRollableJoint(StructureTemplate.@NotNull JigsawBlockInfo jigsaw1, FrontAndTop prop1) {
        String joint = getStringMicroOptimised(jigsaw1.info().nbt(), "joint");
        if(!joint.equals("rollable") && !joint.equals("aligned")) {
            return !prop1.front().getAxis().isHorizontal();
        }
        else {
            return joint.equals("rollable");
        }
    }

    public static void shuffleAndPrioritize(@NotNull List<StructureTemplate.JigsawBlockInfo> list, RandomSource random) {
        Int2ObjectArrayMap<List<StructureTemplate.JigsawBlockInfo>> buckets = new Int2ObjectArrayMap<>();

        // Add entries to the bucket
        for (StructureTemplate.JigsawBlockInfo structureBlockInfo : list) {
            int key = 0;
            if (structureBlockInfo.info().nbt() != null) {
                key = getIntMicroOptimised(structureBlockInfo.info().nbt(), "selection_priority");
            }

            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(structureBlockInfo);
        }

        // Shuffle the entries in the bucket
        for (List<StructureTemplate.JigsawBlockInfo> bucketList : buckets.values()) {
            Util.shuffle(bucketList, random);
        }

        if (buckets.size() == 1) {
            list.clear();
            copyAll(buckets.int2ObjectEntrySet().fastIterator().next().getValue(), list);
        }
        else if (buckets.size() > 1) {
            // Priorities found. Concat them into a single new master list in reverse order to match vanilla behavior
            list.clear();

            IntArrayList keys = new IntArrayList(buckets.keySet());
            keys.sort(IntComparators.OPPOSITE_COMPARATOR);

            for (int i = 0; i < keys.size(); i++) {
                copyAll(buckets.get(keys.getInt(i)), list);
            }
        }
    }

    public static int getIntMicroOptimised(@NotNull CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag numericTag ? numericTag.getAsInt() : 0;
    }

    public static @NotNull String getStringMicroOptimised(@NotNull CompoundTag tag, String key) {
        return tag.get(key) instanceof StringTag stringTag ? stringTag.getAsString() : "";
    }

    public static <T> void copyAll(@NotNull List<T> src, List<T> dest) {
        // Do not listen to IDE. This is faster than addAll
        for (int i = 0; i < src.size(); i++) {
            dest.add(src.get(i));
        }
    }
}
