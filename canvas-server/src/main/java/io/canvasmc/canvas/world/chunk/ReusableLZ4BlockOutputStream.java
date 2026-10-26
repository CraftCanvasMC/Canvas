package io.canvasmc.canvas.world.chunk;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Checksum;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Writes the same bytes as {@code new LZ4BlockOutputStream(out)}, but reuses its block buffers per thread
 * instead of allocating them for every chunk save.
 * <p>
 * A stream opened while the thread's buffers are still in use gets its own buffers.
 */
public final class ReusableLZ4BlockOutputStream extends FilterOutputStream {

    private static final byte[] MAGIC = "LZ4Block".getBytes(StandardCharsets.US_ASCII);
    private static final int MAGIC_LENGTH = MAGIC.length;
    private static final int HEADER_LENGTH = MAGIC_LENGTH + 1 + 4 + 4 + 4;
    private static final int BLOCK_SIZE = 1 << 16;
    private static final int COMPRESSION_LEVEL = Math.max(0, 32 - Integer.numberOfLeadingZeros(BLOCK_SIZE - 1) - 10);
    private static final int COMPRESSION_METHOD_RAW = 0x10;
    private static final int COMPRESSION_METHOD_LZ4 = 0x20;
    private static final int DEFAULT_SEED = 0x9747b28c;
    private static final LZ4Compressor COMPRESSOR = LZ4Factory.fastestInstance().fastCompressor();
    private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

    private final Buffers buffers;
    private boolean finished;
    private boolean released;
    private int o;

    private ReusableLZ4BlockOutputStream(final OutputStream out, final Buffers buffers) {
        super(out);
        this.buffers = buffers;
    }

    public static ReusableLZ4BlockOutputStream wrap(final OutputStream out) {
        Buffers buffers = BUFFERS.get();
        if (buffers.inUse) {
            buffers = new Buffers();
        }

        buffers.inUse = true;
        return new ReusableLZ4BlockOutputStream(out, buffers);
    }

    private void ensureNotFinished() {
        if (this.finished || this.released) {
            throw new IllegalStateException("This stream is already closed");
        }
    }

    @Override
    public void write(final int b) throws IOException {
        this.ensureNotFinished();
        if (this.o == BLOCK_SIZE) {
            this.flushBufferedData();
        }

        this.buffers.buffer[this.o++] = (byte) b;
    }

    @Override
    public void write(final byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + len + ", length=" + b.length);
        }

        this.ensureNotFinished();
        final byte[] buffer = this.buffers.buffer;
        while (this.o + len > BLOCK_SIZE) {
            final int l = BLOCK_SIZE - this.o;
            System.arraycopy(b, off, buffer, this.o, l);
            this.o = BLOCK_SIZE;
            this.flushBufferedData();
            off += l;
            len -= l;
        }

        System.arraycopy(b, off, buffer, this.o, len);
        this.o += len;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void flush() throws IOException {
        if (this.out != null) {
            this.out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (this.released) {
            return;
        }

        try {
            if (!this.finished) {
                this.finish();
            }

            if (this.out != null) {
                this.out.close();
                this.out = null;
            }
        } finally {
            this.released = true;
            this.buffers.inUse = false;
        }
    }

    private void finish() throws IOException {
        this.ensureNotFinished();
        this.flushBufferedData();

        final byte[] compressedBuffer = this.buffers.compressedBuffer;
        compressedBuffer[MAGIC_LENGTH] = (byte) (COMPRESSION_METHOD_RAW | COMPRESSION_LEVEL);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 1);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 5);
        writeIntLE(0, compressedBuffer, MAGIC_LENGTH + 9);
        this.out.write(compressedBuffer, 0, HEADER_LENGTH);
        this.finished = true;
        this.out.flush();
    }

    private void flushBufferedData() throws IOException {
        if (this.o == 0) {
            return;
        }

        final byte[] buffer = this.buffers.buffer;
        final byte[] compressedBuffer = this.buffers.compressedBuffer;
        final Checksum checksum = this.buffers.checksum;
        checksum.reset();
        checksum.update(buffer, 0, this.o);
        final int check = (int) checksum.getValue();

        int compressedLength = COMPRESSOR.compress(buffer, 0, this.o, compressedBuffer, HEADER_LENGTH);
        final int compressMethod;
        if (compressedLength >= this.o) {
            compressMethod = COMPRESSION_METHOD_RAW;
            compressedLength = this.o;
            System.arraycopy(buffer, 0, compressedBuffer, HEADER_LENGTH, this.o);
        } else {
            compressMethod = COMPRESSION_METHOD_LZ4;
        }

        compressedBuffer[MAGIC_LENGTH] = (byte) (compressMethod | COMPRESSION_LEVEL);
        writeIntLE(compressedLength, compressedBuffer, MAGIC_LENGTH + 1);
        writeIntLE(this.o, compressedBuffer, MAGIC_LENGTH + 5);
        writeIntLE(check, compressedBuffer, MAGIC_LENGTH + 9);
        this.out.write(compressedBuffer, 0, HEADER_LENGTH + compressedLength);
        this.o = 0;
    }

    private static void writeIntLE(final int i, final byte[] buf, int off) {
        buf[off++] = (byte) i;
        buf[off++] = (byte) (i >>> 8);
        buf[off++] = (byte) (i >>> 16);
        buf[off] = (byte) (i >>> 24);
    }

    private static final class Buffers {

        private final byte[] buffer = new byte[BLOCK_SIZE];
        private final byte[] compressedBuffer = new byte[HEADER_LENGTH + COMPRESSOR.maxCompressedLength(BLOCK_SIZE)];
        // same checksum LZ4BlockOutputStream uses
        private final Checksum checksum = XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum();
        // may be released from another thread
        private volatile boolean inUse;

        private Buffers() {
            System.arraycopy(MAGIC, 0, this.compressedBuffer, 0, MAGIC_LENGTH);
        }
    }
}
