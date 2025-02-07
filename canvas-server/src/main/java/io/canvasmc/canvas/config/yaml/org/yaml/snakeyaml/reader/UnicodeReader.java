//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.reader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class UnicodeReader extends Reader {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset UTF16BE = StandardCharsets.UTF_16BE;
    private static final Charset UTF16LE = StandardCharsets.UTF_16LE;
    private static final int BOM_SIZE = 3;
    PushbackInputStream internalIn;
    InputStreamReader internalIn2 = null;

    public UnicodeReader(InputStream in) {
        this.internalIn = new PushbackInputStream(in, 3);
    }

    public String getEncoding() {
        return this.internalIn2.getEncoding();
    }

    protected void init() throws IOException {
        if (this.internalIn2 == null) {
            byte[] bom = new byte[3];
            int n = this.internalIn.read(bom, 0, bom.length);
            Charset encoding;
            int unread;
            if (bom[0] == -17 && bom[1] == -69 && bom[2] == -65) {
                encoding = UTF8;
                unread = n - 3;
            } else if (bom[0] == -2 && bom[1] == -1) {
                encoding = UTF16BE;
                unread = n - 2;
            } else if (bom[0] == -1 && bom[1] == -2) {
                encoding = UTF16LE;
                unread = n - 2;
            } else {
                encoding = UTF8;
                unread = n;
            }

            if (unread > 0) {
                this.internalIn.unread(bom, n - unread, unread);
            }

            CharsetDecoder decoder = encoding.newDecoder().onUnmappableCharacter(CodingErrorAction.REPORT);
            this.internalIn2 = new InputStreamReader(this.internalIn, decoder);
        }
    }

    public void close() throws IOException {
        this.init();
        this.internalIn2.close();
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        this.init();
        return this.internalIn2.read(cbuf, off, len);
    }
}
