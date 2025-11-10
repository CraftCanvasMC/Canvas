/*
 * MIT License
 *
 * Copyright (c) 2018-2019 Falkreon (Isaac Ellingson)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.canvasmc.canvas.configuration.jankson;

import io.canvasmc.canvas.configuration.jankson.api.Marshaller;
import io.canvasmc.canvas.configuration.jankson.impl.MarshallerImpl;
import io.canvasmc.canvas.configuration.jankson.impl.serializer.CommentSerializer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JsonObject extends JsonElement implements Map<String, JsonElement> {
    private final List<Entry> entries = new ArrayList<>();
    @SuppressWarnings("deprecation")
    protected Marshaller marshaller = MarshallerImpl.getFallback();

    /**
     * If there is an entry at this key, and that entry is a json object, return it. Otherwise returns null.
     */
    @Nullable
    public JsonObject getObject(@Nonnull String name) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(name)) {
                if (entry.value instanceof JsonObject) {
                    return (JsonObject) entry.value;
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Replaces a key-value mapping in this object if it exists, or adds the mapping to the end of the object if it
     * doesn't. Returns the old value mapped to this key if there was one.
     */
    public JsonElement put(@Nonnull String key, @Nonnull JsonElement elem, @Nullable String comment) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(key)) {
                JsonElement result = entry.value;
                entry.value = elem;
                entry.comment = comment;
                return result;
            }
        }

        //If we reached here, there's no existing mapping, so make one.
        Entry entry = new Entry();
        if (elem instanceof JsonObject) ((JsonObject) elem).marshaller = marshaller;
        if (elem instanceof JsonArray) ((JsonArray) elem).marshaller = marshaller;
        entry.key = key;
        entry.value = elem;
        entry.comment = comment;
        entries.add(entry);
        return null;
    }

    @Nonnull
    public JsonElement putDefault(@Nonnull String key, @Nonnull JsonElement elem, @Nullable String comment) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(key)) {
                return entry.value;
            }
        }

        //If we reached here, there's no existing mapping, so make one.
        Entry entry = new Entry();
        entry.key = key;
        entry.value = elem;
        entry.comment = comment;
        entries.add(entry);
        return elem;
    }

    /**
     * May return null if the existing object can't be marshalled to elem's class
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T putDefault(@Nonnull String key, @Nonnull T elem, @Nullable String comment) {
        return (T) putDefault(key, elem, elem.getClass(), comment);
    }

    /**
     * May return null if the existing object can't be marshalled to the target class
     */
    @Nullable
    public <T> T putDefault(@Nonnull String key, @Nonnull T elem, Class<? extends T> clazz, @Nullable String comment) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(key)) {
                return marshaller.marshall(clazz, entry.value);
            }
        }

        //If we reached here, there's no existing mapping, so make one.
        Entry entry = new Entry();
        entry.key = key;
        entry.value = marshaller.serialize(elem);
        if (entry.value == null) entry.value = JsonNull.INSTANCE;
        entry.comment = comment;
        entries.add(entry);
        return elem;
    }

    /**
     * Gets a minimal set of key-value-comment settings which, if added to the supplied JsonObject, would produce this
     * JsonObject. See BasicTests::testDiffAgainstDefaults() for more details on this comparison.
     *
     * <ul>
     *   <li>If a key is present in the default and not in the object, it's skipped
     *   <li>If a key is an object, a deep (recursive) comparison occurs. Comments are ignored in this comparison.
     *   <li>All other types, including lists, receive a shallow comparison of its value. The comment is ignored in this comparison.
     *   <li>Whether deep or shallow, if the key is found to be identical in value to its default, it is skipped.
     *   <li>If the key is found to be different than its default, the key, value, and comment are represented in the
     *       output.
     * </ul>
     */
    @Nonnull
    public JsonObject getDelta(@Nonnull JsonObject defaults) {
        JsonObject result = new JsonObject();
        for (Entry entry : entries) {
            String key = entry.key;
            JsonElement defaultValue = defaults.get(key);
            if (defaultValue == null) {
                result.put(entry.key, entry.value, entry.comment);
                continue;
            }

            if (entry.value instanceof JsonObject) {
                if (defaultValue instanceof JsonObject) {
                    JsonObject subDelta = ((JsonObject) entry.value).getDelta((JsonObject) defaultValue);
                    if (subDelta.isEmpty()) {
                        continue;
                    } else {
                        result.put(entry.key, subDelta, entry.comment);
                        continue;
                    }
                }
            }

            if (entry.value.equals(defaultValue)) continue;

            result.put(entry.key, entry.value, entry.comment);
        }

        return result;
    }

    /**
     * Returns the comment "attached to" a given key-value mapping, which is to say, the comment appearing immediately
     * before it or the single-line comment to the right of it.
     */
    @Nullable
    public String getComment(@Nonnull String name) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(name)) {
                return entry.comment;
            }
        }

        return null;
    }

    public void setComment(@Nonnull String name, @Nullable String comment) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(name)) {
                entry.comment = comment;
                return;
            }
        }
    }

    @Override
    public String toJson(boolean comments, boolean newlines, int depth) {
        JsonGrammar grammar = JsonGrammar.builder().withComments(comments).printWhitespace(newlines).build();
        return toJson(grammar, depth);
    }

    @Override
    public String toJson(JsonGrammar grammar, int depth) {
        StringBuilder builder = new StringBuilder();
        boolean skipBraces = depth == 0 && grammar.bareRootObject;
        int effectiveDepth = (grammar.bareRootObject) ? Math.max(depth - 1, 0) : depth;
        int nextDepth = (grammar.bareRootObject) ? depth : depth + 1;

        if (!skipBraces) {
            builder.append("{");

            if (grammar.printWhitespace && entries.size() > 0) {
                builder.append('\n');
            } else {
                builder.append(' ');
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);

            if (grammar.printWhitespace) {
                for (int j = 0; j < nextDepth; j++) {
                    builder.append("\t");
                }
            }

            CommentSerializer.print(builder, entry.comment, Math.max(effectiveDepth, 0), grammar);

            boolean quoted = !grammar.printUnquotedKeys;
            if (entry.key.contains(" ")) quoted = true;

            if (quoted) builder.append("\"");
            builder.append(entry.key);
            if (quoted) builder.append("\"");
            builder.append(": ");
            //if (entry.value instanceof JsonObject) {
            //	builder.append(((JsonObject)entry.value).toJson(grammar, depth+1));
            //} else if (entry.value instanceof JsonArray) {
            //	builder.append(((JsonArray)entry.value).toJson(grammar, depth+1));
            //} else {
            builder.append(entry.value.toJson(grammar, depth + 1));
            //}

            if (grammar.printCommas) {
                if (i < entries.size() - 1 || grammar.printTrailingCommas) {
                    builder.append(",");
                    if (i < entries.size() - 1 && !grammar.printWhitespace) builder.append(' ');
                }
            } else {
                builder.append(" ");
            }

            if (grammar.printWhitespace) {
                builder.append('\n');
            }
        }

        if (!skipBraces) {
            if (entries.size() > 0) {
                if (grammar.printWhitespace) {
                    for (int j = 0; j < effectiveDepth; j++) {
                        builder.append("\t");
                    }
                } else {
                    builder.append(' ');
                }
            }

            builder.append("}");
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        return toJson(true, false, 0);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof final JsonObject otherObject)) return false;
        if (entries.size() != otherObject.entries.size()) return false;

        //Lists are identical sizes, but if the contents, comments, or ordering are at all different, fail them
        for (int i = 0; i < entries.size(); i++) {
            Entry a = entries.get(i);
            Entry b = otherObject.entries.get(i);

            if (!a.equals(b)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    public Marshaller getMarshaller() {
        return this.marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    @Nullable
    public <E> E get(@Nonnull Class<E> clazz, @Nonnull String key) {
        if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");

        JsonElement elem = get(key);
        return marshaller.marshall(clazz, elem);
    }

    //Convenience getters

    public boolean getBoolean(@Nonnull String key, boolean defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asBoolean(defaultValue);
        }
        return defaultValue;
    }

    public byte getByte(@Nonnull String key, byte defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asByte(defaultValue);
        }
        return defaultValue;
    }

    public char getChar(@Nonnull String key, char defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asChar(defaultValue);
        }
        return defaultValue;
    }

    public short getShort(@Nonnull String key, short defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asShort(defaultValue);
        }
        return defaultValue;
    }

    public int getInt(@Nonnull String key, int defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asInt(defaultValue);
        }
        return defaultValue;
    }

    public long getLong(@Nonnull String key, long defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asLong(defaultValue);
        }
        return defaultValue;
    }

    public float getFloat(@Nonnull String key, float defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asFloat(defaultValue);
        }
        return defaultValue;
    }

    public double getDouble(@Nonnull String key, double defaultValue) {
        JsonElement elem = get(key);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asDouble(defaultValue);
        }
        return defaultValue;
    }

    /**
     * Gets a (potentially nested) element from this object if it exists.
     *
     * @param clazz The expected class of the element
     * @param key   The keys of the nested elements, separated by periods, such as "foo.bar.baz"
     * @return The element at that location, if it exists and is of the proper type, otherwise null.
     */
    @Nullable
    public <E> E recursiveGet(@Nonnull Class<E> clazz, @Nonnull String key) {
        if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");
        String[] parts = key.split("\\.");
        JsonObject cur = this;
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.isEmpty()) throw new IllegalArgumentException("Cannot get from broken key '" + key + "'");
            JsonElement elem = cur.get(s);
            if (i < parts.length - 1) {
                //elem must be a JsonObject or we're sunk
                if (elem instanceof JsonObject) {
                    cur = (JsonObject) elem;
                    continue;
                } else {
                    return null;
                }
            } else {
                return marshaller.marshall(clazz, elem);
				
				/*
				if (clazz.isAssignableFrom(elem.getClass())) {
					return (E) elem;
				} else {
					return null;
				}*/
            }
        }
        throw new IllegalArgumentException("Cannot get from broken key '" + key + "'");
    }

    /**
     * Gets a (potentially nested) element from this object if it exists, or creates it and any intermediate objects
     * needed to put it at the indicated location in the hierarchy.
     *
     * @param clazz The expected class of the element
     * @param key   The keys of the nested elements, separated by periods, such as "foo.bar.baz"
     * @return The element at that location if it exists, or the newly-created element if it did not previously exist.
     */
    @SuppressWarnings("unchecked")
    public <E extends JsonElement> E recursiveGetOrCreate(@Nonnull Class<E> clazz, @Nonnull String key, @Nonnull E fallback, @Nullable String comment) {
        if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");
        String[] parts = key.split("\\.");
        JsonObject cur = this;
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.isEmpty()) throw new IllegalArgumentException("Cannot get from broken key '" + key + "'");
            JsonElement elem = cur.get(s);
            if (i < parts.length - 1) {
                //elem must be a JsonObject or we're sunk
                if (elem instanceof JsonObject) {
                    cur = (JsonObject) elem;
                    continue;
                } else {
                    JsonObject replacement = new JsonObject();
                    cur.put(s, replacement);
                    cur = replacement;
                    continue;
                }
            } else {
                if (elem != null && clazz.isAssignableFrom(elem.getClass())) {
                    return (E) elem;
                } else {
                    E result = (E) fallback.clone();
                    cur.put(key, result, comment);
                    return result;
                }
            }
        }

        throw new IllegalArgumentException("Cannot get from broken key '" + key + "'");
    }

    @Override
    public JsonObject clone() {
        JsonObject result = new JsonObject();
        for (Entry entry : entries) {
            result.put(entry.key, entry.value.clone(), entry.comment);
        }
        result.marshaller = marshaller;
        return result;
    }

    //implements Cloneable {

    /**
     * Replaces a key-value mapping in this object if it exists, or adds the mapping to the end of the object if it
     * doesn't. Returns the old value mapped to this key if there was one.
     */
    @Override
    @Nullable
    public JsonElement put(@Nonnull String key, @Nonnull JsonElement elem) {
        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase(key)) {
                JsonElement result = entry.value;
                entry.value = elem;
                return result;
            }
        }

        //If we reached here, there's no existing mapping, so make one.
        Entry entry = new Entry();
        entry.key = key;
        entry.value = elem;
        entries.add(entry);
        return null;
    }

    //}

    //implements Map<JsonElement> {

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        if (key == null) return false;
        if (!(key instanceof String)) return false;

        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase((String) key)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean containsValue(@Nullable Object val) {
        if (val == null) return false;
        if (!(val instanceof JsonElement)) return false;

        for (Entry entry : entries) {
            if (entry.value.equals(val)) return true;
        }

        return false;
    }

    /**
     * Creates a semi-live shallow copy instead of a live view
     */
    @Override
    public Set<Map.Entry<String, JsonElement>> entrySet() {
        Set<Map.Entry<String, JsonElement>> result = new LinkedHashSet<>();
        for (Entry entry : entries) {
            result.add(new Map.Entry<String, JsonElement>() {
                @Override
                public String getKey() {
                    return entry.key;
                }

                @Override
                public JsonElement getValue() {
                    return entry.value;
                }

                @Override
                public JsonElement setValue(JsonElement value) {
                    JsonElement oldValue = entry.value;
                    entry.value = value;
                    return oldValue;
                }

            });
        }

        return result;
    }

    @Override
    @Nullable
    public JsonElement get(@Nullable Object key) {
        if (key == null || !(key instanceof String)) return null;

        for (Entry entry : entries) {
            if (entry.key.equalsIgnoreCase((String) key)) {
                return entry.value;
            }
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns a defensive copy instead of a live view
     */
    @Override
    @Nonnull
    public Set<String> keySet() {
        Set<String> keys = new HashSet<>();
        for (Entry entry : entries) {
            keys.add(entry.key);
        }
        return keys;
    }

    @Override
    public void putAll(Map<? extends String, ? extends JsonElement> map) {
        for (Map.Entry<? extends String, ? extends JsonElement> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    @Nullable
    public JsonElement remove(@Nullable Object key) {
        if (key == null || !(key instanceof String)) return null;

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.key.equalsIgnoreCase((String) key)) {
                return entries.remove(i).value;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public Collection<JsonElement> values() {
        List<JsonElement> values = new ArrayList<>();
        for (Entry entry : entries) {
            values.add(entry.value);
        }
        return values;
    }

    private static final class Entry {
        private String comment;
        private String key;
        private JsonElement value;

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof final Entry o)) return false;
            if (!Objects.equals(comment, o.comment)) return false;
            if (!key.equals(o.key)) return false;
            return value.equals(o.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(comment, key, value);
        }
    }
    //}
}
