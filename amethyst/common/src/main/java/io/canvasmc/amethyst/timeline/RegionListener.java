package io.canvasmc.amethyst.timeline;

public interface RegionListener {

    void created(final String world, final long handle, final long[] sections);

    void destroyed(final String world, final long handle);

    void active(final String world, final long handle);

    void inactive(final String world, final long handle);

    void merged(final String world, final long from, final long into, final long[] survivorSections);

    void split(final String world, final long from, final long[] into);
}
