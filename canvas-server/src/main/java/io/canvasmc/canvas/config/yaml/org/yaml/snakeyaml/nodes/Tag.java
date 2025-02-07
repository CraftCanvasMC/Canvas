//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.UriEncoder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Tag {
    public static final String PREFIX = "tag:yaml.org,2002:";
    public static final Tag YAML = new Tag("tag:yaml.org,2002:yaml");
    public static final Tag MERGE = new Tag("tag:yaml.org,2002:merge");
    public static final Tag SET = new Tag("tag:yaml.org,2002:set");
    public static final Tag PAIRS = new Tag("tag:yaml.org,2002:pairs");
    public static final Tag OMAP = new Tag("tag:yaml.org,2002:omap");
    public static final Tag BINARY = new Tag("tag:yaml.org,2002:binary");
    public static final Tag INT = new Tag("tag:yaml.org,2002:int");
    public static final Tag FLOAT = new Tag("tag:yaml.org,2002:float");
    public static final Tag TIMESTAMP = new Tag("tag:yaml.org,2002:timestamp");
    public static final Tag BOOL = new Tag("tag:yaml.org,2002:bool");
    public static final Tag NULL = new Tag("tag:yaml.org,2002:null");
    public static final Tag STR = new Tag("tag:yaml.org,2002:str");
    public static final Tag SEQ = new Tag("tag:yaml.org,2002:seq");
    public static final Tag MAP = new Tag("tag:yaml.org,2002:map");
    private static final Map<Tag, Set<Class<?>>> COMPATIBILITY_MAP = new HashMap();
    static {
        Set<Class<?>> floatSet = new HashSet();
        floatSet.add(Double.class);
        floatSet.add(Float.class);
        floatSet.add(BigDecimal.class);
        COMPATIBILITY_MAP.put(FLOAT, floatSet);
        Set<Class<?>> intSet = new HashSet();
        intSet.add(Integer.class);
        intSet.add(Long.class);
        intSet.add(BigInteger.class);
        COMPATIBILITY_MAP.put(INT, intSet);
        Set<Class<?>> timestampSet = new HashSet();
        timestampSet.add(Date.class);

        try {
            timestampSet.add(Class.forName("java.sql.Date"));
            timestampSet.add(Class.forName("java.sql.Timestamp"));
        } catch (ClassNotFoundException var4) {
        }

        COMPATIBILITY_MAP.put(TIMESTAMP, timestampSet);
    }
    private final String value;
    private boolean secondary = false;

    public Tag(String tag) {
        if (tag == null) {
            throw new NullPointerException("Tag must be provided.");
        } else if (tag.length() == 0) {
            throw new IllegalArgumentException("Tag must not be empty.");
        } else if (tag.trim().length() != tag.length()) {
            throw new IllegalArgumentException("Tag must not contain leading or trailing spaces.");
        } else {
            this.value = UriEncoder.encode(tag);
            this.secondary = !tag.startsWith("tag:yaml.org,2002:");
        }
    }

    public Tag(Class<? extends Object> clazz) {
        if (clazz == null) {
            throw new NullPointerException("Class for tag must be provided.");
        } else {
            this.value = "tag:yaml.org,2002:" + UriEncoder.encode(clazz.getName());
        }
    }

    /**
     * @deprecated
     */
    public Tag(URI uri) {
        if (uri == null) {
            throw new NullPointerException("URI for tag must be provided.");
        } else {
            this.value = uri.toASCIIString();
        }
    }

    public boolean isSecondary() {
        return this.secondary;
    }

    public String getValue() {
        return this.value;
    }

    public boolean startsWith(String prefix) {
        return this.value.startsWith(prefix);
    }

    public String getClassName() {
        if (!this.value.startsWith("tag:yaml.org,2002:")) {
            throw new YAMLException("Invalid tag: " + this.value);
        } else {
            return UriEncoder.decode(this.value.substring("tag:yaml.org,2002:".length()));
        }
    }

    public String toString() {
        return this.value;
    }

    public boolean equals(Object obj) {
        return obj instanceof Tag && this.value.equals(((Tag) obj).getValue());
    }

    public int hashCode() {
        return this.value.hashCode();
    }

    public boolean isCompatible(Class<?> clazz) {
        Set<Class<?>> set = COMPATIBILITY_MAP.get(this);
        return set != null && set.contains(clazz);
    }

    public boolean matches(Class<? extends Object> clazz) {
        return this.value.equals("tag:yaml.org,2002:" + clazz.getName());
    }
}
