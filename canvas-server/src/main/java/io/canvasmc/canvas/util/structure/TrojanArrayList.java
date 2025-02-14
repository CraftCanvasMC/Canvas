package io.canvasmc.canvas.util.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.jetbrains.annotations.Nullable;

public class TrojanArrayList<E extends @Nullable Object> extends ArrayList<E> {
    public final Set<StructurePoolElement> elementsAlreadyParsed = new HashSet<>();
}
