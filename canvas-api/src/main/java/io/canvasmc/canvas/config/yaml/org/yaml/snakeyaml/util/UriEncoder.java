//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.external.com.google.gdata.util.common.base.Escaper;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.external.com.google.gdata.util.common.base.PercentEscaper;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public abstract class UriEncoder {
    private static final CharsetDecoder UTF8Decoder;
    private static final String SAFE_CHARS = "-_.!~*'()@:$&,;=[]/";
    private static final Escaper escaper;

    static {
        UTF8Decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        escaper = new PercentEscaper("-_.!~*'()@:$&,;=[]/", false);
    }

    public UriEncoder() {
    }

    public static String encode(String uri) {
        return escaper.escape(uri);
    }

    public static String decode(ByteBuffer buff) throws CharacterCodingException {
        CharBuffer chars = UTF8Decoder.decode(buff);
        return chars.toString();
    }

    public static String decode(String buff) {
        return URLDecoder.decode(buff, StandardCharsets.UTF_8);
    }
}
