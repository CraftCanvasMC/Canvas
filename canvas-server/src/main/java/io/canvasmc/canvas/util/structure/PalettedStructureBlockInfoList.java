package io.canvasmc.canvas.util.structure;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public class PalettedStructureBlockInfoList implements List<StructureTemplate.StructureBlockInfo> {
    private static final long[] EMPTY_DATA = new long[0];
    private static final CompoundTag[] NULL_TAGS = new CompoundTag[]{null};
    private static final String UNSUPPORTED_OPERATION_ERROR_MESSAGE = "Structure Layout Optimizer: No mod should be modifying a StructureTemplate's Palette itself. Please reach out to Structure Layout Optimizer dev for this crash to investigate this mod compat issue.";

    protected final long[] data;
    protected final BlockState[] states;
    protected final CompoundTag[] nbts;
    protected final int xBits, yBits, zBits;
    protected final int stateBits, nbtBits;
    protected final int bitsPerEntry;
    protected final int size;
    private WeakReference<List<StructureTemplate.StructureBlockInfo>> cachedStructureBlockInfoList = new WeakReference<>(null);

    public PalettedStructureBlockInfoList(List<StructureTemplate.StructureBlockInfo> infos) {
        this(infos, null);
    }

    public PalettedStructureBlockInfoList(List<StructureTemplate.StructureBlockInfo> infos, Predicate<StructureTemplate.StructureBlockInfo> predicate) {
        List<Entry> entries = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();

        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (StructureTemplate.StructureBlockInfo info : infos) {
            if (predicate != null && !predicate.test(info)) {
                continue;
            }

            int state = states.indexOf(info.state());
            if (state == -1) {
                state = states.size();
                states.add(info.state());
            }

            int tag = indexOf(tags, info.nbt());
            if (tag == -1) {
                tag = tags.size();
                tags.add(info.nbt());
            }

            int x = info.pos().getX();
            int y = info.pos().getY();
            int z = info.pos().getZ();

            if (x < 0 || y < 0 || z < 0) {
                throw new RuntimeException("StructureLayoutOptimizer: Invalid StructureBlockInfo position: " + info.pos());
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }

            entries.add(new Entry(x, y, z, state, tag));
        }

        this.xBits = bits(maxX);
        this.yBits = bits(maxY);
        this.zBits = bits(maxZ);
        this.stateBits = bits(states.size() - 1);
        this.nbtBits = bits(tags.size() - 1);
        this.bitsPerEntry = this.xBits + this.yBits + this.zBits + this.stateBits + this.nbtBits;

        if (this.bitsPerEntry > 64) {
            throw new RuntimeException("StructureLayoutOptimizer: Too many bits per entry: " + this.bitsPerEntry);
        }

        this.size = entries.size();
        if (this.bitsPerEntry != 0) {
            int entriesPerLong = 64 / this.bitsPerEntry;
            this.data = new long[(this.size + entriesPerLong - 1) / entriesPerLong];
            for (int i = 0; i < this.size; i++) {
                this.data[i / entriesPerLong] |= entries.get(i).compress(this.xBits, this.yBits, this.zBits, this.stateBits) << ((i % entriesPerLong) * this.bitsPerEntry);
            }
        } else {
            this.data = EMPTY_DATA;
        }
        this.states = states.toArray(new BlockState[0]);
        this.nbts = tags.size() == 1 && tags.get(0) == null ? NULL_TAGS : tags.toArray(new CompoundTag[0]);
    }

    private static int bits(int i) {
        int bits = 0;
        while (i >= 1 << bits) {
            bits++;
        }
        return bits;
    }

    private static <T> int indexOf(@NotNull List<T> list, T o) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == o) {
                return i;
            }
        }
        return -1;
    }

    private @NotNull List<StructureTemplate.StructureBlockInfo> convertBackToStructureBlockInfoListAndCache() {
        synchronized(data) {
            List<StructureTemplate.StructureBlockInfo> structureBlockInfos = cachedStructureBlockInfoList.get();
            if (structureBlockInfos != null) {
                return structureBlockInfos;
            }

            structureBlockInfos = new ObjectArrayList<>(new PalettedStructureBlockInfoListIterator(this));
            cachedStructureBlockInfoList = new WeakReference<>(structureBlockInfos);
            return structureBlockInfos;
        }
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @NotNull
    @Override
    public Iterator<StructureTemplate.StructureBlockInfo> iterator() {
        return convertBackToStructureBlockInfoListAndCache().iterator();
    }

    @NotNull
    @Override
    public ListIterator<StructureTemplate.StructureBlockInfo> listIterator() {
        return convertBackToStructureBlockInfoListAndCache().listIterator();
    }

    @NotNull
    @Override
    public ListIterator<StructureTemplate.StructureBlockInfo> listIterator(int index) {
        return convertBackToStructureBlockInfoListAndCache().listIterator(index);
    }


    @Override
    public boolean contains(Object o) {
        return convertBackToStructureBlockInfoListAndCache().contains(o);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return new HashSet<>(convertBackToStructureBlockInfoListAndCache()).containsAll(c);
    }

    @NotNull
    @Override
    public Object @NotNull [] toArray() {
        return convertBackToStructureBlockInfoListAndCache().toArray();
    }

    @NotNull
    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] a) {
        return convertBackToStructureBlockInfoListAndCache().toArray(a);
    }

    @Override
    public StructureTemplate.StructureBlockInfo get(int index) {
        return convertBackToStructureBlockInfoListAndCache().get(index);
    }

    @Override
    public int indexOf(Object o) {
        return convertBackToStructureBlockInfoListAndCache().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return convertBackToStructureBlockInfoListAndCache().lastIndexOf(o);
    }

    @NotNull
    @Override
    public List<StructureTemplate.StructureBlockInfo> subList(int fromIndex, int toIndex) {
        return convertBackToStructureBlockInfoListAndCache().subList(fromIndex, toIndex);
    }

    @Override
    public StructureTemplate.StructureBlockInfo set(int index, StructureTemplate.StructureBlockInfo element) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public void add(int index, StructureTemplate.StructureBlockInfo element) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean add(StructureTemplate.StructureBlockInfo info) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public StructureTemplate.StructureBlockInfo remove(int index) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends StructureTemplate.StructureBlockInfo> c) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends StructureTemplate.StructureBlockInfo> c) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_ERROR_MESSAGE);
    }

    private static class Entry {
        private final int x, y, z;
        private final int state, nbt;

        private Entry(int x, int y, int z, int state, int nbt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.nbt = nbt;
        }

        private long compress(int xBits, int yBits, int zBits, int stateBits) {
            return this.x + ((this.y + ((this.z + ((this.state + ((long) this.nbt << stateBits)) << zBits)) << yBits)) << xBits);
        }
    }
}
