package io.canvasmc.canvas.region;

public class RegionCrash extends RuntimeException {
    private final ChunkRegion tickHandle;

    public RegionCrash(Exception exc, ChunkRegion tickHandle) {
        super(exc);
        this.tickHandle = tickHandle;
    }

    @Override
    public String getMessage() {
        return "Encountered region crash at " + this.tickHandle.world.location() + " surrounding chunk " + this.tickHandle.region.getCenterChunk() + ": " + super.getMessage();
    }
}
