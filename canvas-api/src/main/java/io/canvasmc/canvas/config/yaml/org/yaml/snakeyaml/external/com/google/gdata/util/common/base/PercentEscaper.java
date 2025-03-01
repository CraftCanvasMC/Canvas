//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.external.com.google.gdata.util.common.base;

public class PercentEscaper extends UnicodeEscaper {
    public static final String SAFECHARS_URLENCODER = "-_.*";
    public static final String SAFEPATHCHARS_URLENCODER = "-_.!~*'()@:$&,;=";
    public static final String SAFEQUERYSTRINGCHARS_URLENCODER = "-_.!~*'()@:$,;/?:";
    private static final char[] URI_ESCAPED_SPACE = new char[]{'+'};
    private static final char[] UPPER_HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private final boolean plusForSpace;
    private final boolean[] safeOctets;

    public PercentEscaper(String safeChars, boolean plusForSpace) {
        if (safeChars.matches(".*[0-9A-Za-z].*")) {
            throw new IllegalArgumentException("Alphanumeric characters are always 'safe' and should not be explicitly specified");
        } else if (plusForSpace && safeChars.contains(" ")) {
            throw new IllegalArgumentException("plusForSpace cannot be specified when space is a 'safe' character");
        } else if (safeChars.contains("%")) {
            throw new IllegalArgumentException("The '%' character cannot be specified as 'safe'");
        } else {
            this.plusForSpace = plusForSpace;
            this.safeOctets = createSafeOctets(safeChars);
        }
    }

    private static boolean[] createSafeOctets(String safeChars) {
        int maxChar = 122;
        char[] safeCharArray = safeChars.toCharArray();

        for (char c : safeCharArray) {
            maxChar = Math.max(c, maxChar);
        }

        boolean[] octets = new boolean[maxChar + 1];

        for (int c = 48; c <= 57; ++c) {
            octets[c] = true;
        }

        for (int c = 65; c <= 90; ++c) {
            octets[c] = true;
        }

        for (int c = 97; c <= 122; ++c) {
            octets[c] = true;
        }

        for (char c : safeCharArray) {
            octets[c] = true;
        }

        return octets;
    }

    protected int nextEscapeIndex(CharSequence csq, int index, int end) {
        while (true) {
            if (index < end) {
                char c = csq.charAt(index);
                if (c < this.safeOctets.length && this.safeOctets[c]) {
                    ++index;
                    continue;
                }
            }

            return index;
        }
    }

    public String escape(String s) {
        int slen = s.length();

        for (int index = 0; index < slen; ++index) {
            char c = s.charAt(index);
            if (c >= this.safeOctets.length || !this.safeOctets[c]) {
                return this.escapeSlow(s, index);
            }
        }

        return s;
    }

    protected char[] escape(int cp) {
        if (cp < this.safeOctets.length && this.safeOctets[cp]) {
            return null;
        } else if (cp == 32 && this.plusForSpace) {
            return URI_ESCAPED_SPACE;
        } else if (cp <= 127) {
            char[] dest = new char[3];
            dest[0] = '%';
            dest[2] = UPPER_HEX_DIGITS[cp & 15];
            dest[1] = UPPER_HEX_DIGITS[cp >>> 4];
            return dest;
        } else if (cp <= 2047) {
            char[] dest = new char[6];
            dest[0] = '%';
            dest[3] = '%';
            dest[5] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[4] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[2] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[1] = UPPER_HEX_DIGITS[12 | cp];
            return dest;
        } else if (cp <= 65535) {
            char[] dest = new char[9];
            dest[0] = '%';
            dest[1] = 'E';
            dest[3] = '%';
            dest[6] = '%';
            dest[8] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[7] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[5] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[4] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[2] = UPPER_HEX_DIGITS[cp];
            return dest;
        } else if (cp <= 1114111) {
            char[] dest = new char[12];
            dest[0] = '%';
            dest[1] = 'F';
            dest[3] = '%';
            dest[6] = '%';
            dest[9] = '%';
            dest[11] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[10] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[8] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[7] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[5] = UPPER_HEX_DIGITS[cp & 15];
            cp >>>= 4;
            dest[4] = UPPER_HEX_DIGITS[8 | cp & 3];
            cp >>>= 2;
            dest[2] = UPPER_HEX_DIGITS[cp & 7];
            return dest;
        } else {
            throw new IllegalArgumentException("Invalid unicode character value " + cp);
        }
    }
}
