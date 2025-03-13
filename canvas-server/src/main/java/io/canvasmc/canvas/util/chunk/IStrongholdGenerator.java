package io.canvasmc.canvas.util.chunk;

import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

public interface IStrongholdGenerator {
    ThreadLocal<Class<? extends StrongholdPieces.StrongholdPiece>> getActivePieceTypeThreadLocal();

    class Holder {
        public static final IStrongholdGenerator INSTANCE = (IStrongholdGenerator) (new StrongholdPieces());

        public Holder() {
        }
    }
}
