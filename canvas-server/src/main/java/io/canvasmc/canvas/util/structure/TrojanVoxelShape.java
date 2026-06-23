package io.canvasmc.canvas.util.structure;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

public class TrojanVoxelShape extends VoxelShape {
    public final BoxOctree boxOctree;

    public TrojanVoxelShape(BoxOctree boxOctree) {
        super(BitSetDiscreteVoxelShape.withFilledBounds(0, 0, 0, 0, 0, 0, 0, 0, 0));
        this.boxOctree = boxOctree;
    }

    @Override
    public DoubleList getCoords(Direction.@NonNull Axis axis) { // this shouldn't return null as per the annotations on VoxelShape
        return null;
    }
}
