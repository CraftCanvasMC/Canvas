package io.canvasmc.canvas.util.structure;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;

public class PalettedStructureBlockInfoListIterator implements Iterator<StructureTemplate.StructureBlockInfo> {
    private final PalettedStructureBlockInfoList infos;

    private final int xOffset, yOffset, zOffset, stateOffset;
    private final int xMask, yMask, zMask, stateMask, tagMask;

    private int index = 0;

    public PalettedStructureBlockInfoListIterator(@NotNull PalettedStructureBlockInfoList infos) {
        this.infos = infos;
        this.xOffset = infos.xBits;
        this.yOffset = this.xOffset + infos.yBits;
        this.zOffset = this.yOffset + infos.zBits;
        this.stateOffset = this.zOffset + infos.stateBits;
        this.xMask = (1 << infos.xBits) - 1;
        this.yMask = (1 << infos.yBits) - 1;
        this.zMask = (1 << infos.zBits) - 1;
        this.stateMask = (1 << infos.stateBits) - 1;
        this.tagMask = (1 << infos.nbtBits) - 1;
    }

    @Override
    public boolean hasNext() {
        return this.index < this.size();
    }

    @Override
    public StructureTemplate.StructureBlockInfo next() {
        int index = this.toIndex(this.index++);
        if (index >= this.infos.size) {
            throw new IndexOutOfBoundsException();
        }

        int bitsPerEntry = this.infos.bitsPerEntry;
        if (bitsPerEntry == 0) {
            return new StructureTemplate.StructureBlockInfo(new BlockPos(0, 0, 0), this.infos.states[0], this.infos.nbts[0]);
        }

        int entriesPerLong = 64 / bitsPerEntry;
        long entry = this.infos.data[index / entriesPerLong] >>> ((index % entriesPerLong) * bitsPerEntry);

        int x = (int) (entry & this.xMask);
        int y = (int) (entry >> this.xOffset & this.yMask);
        int z = (int) (entry >> this.yOffset & this.zMask);
        int state = (int) (entry >> this.zOffset & this.stateMask);
        int tag = (int) (entry >> this.stateOffset & this.tagMask);

        return new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), this.infos.states[state], this.infos.nbts[tag]);
    }

    protected int toIndex(int index) {
        return index;
    }

    protected int size() {
        return this.infos.size;
    }
}
