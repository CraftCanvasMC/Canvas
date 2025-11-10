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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class JsonArray extends JsonElement implements List<JsonElement>, Iterable<JsonElement> {
    private final List<Entry> entries = new ArrayList<>();
    protected Marshaller marshaller = MarshallerImpl.getFallback();

    public JsonArray() {
    }

    public <T> JsonArray(T[] ts, Marshaller marshaller) {
        this.marshaller = marshaller;
        for (T t : ts) {
            this.add(marshaller.serialize(t));
        }
    }

    public JsonArray(Collection<?> ts, Marshaller marshaller) {
        this.marshaller = marshaller;
        for (Object t : ts) {
            this.add(marshaller.serialize(t));
        }
    }

    public JsonElement get(int i) {
        return entries.get(i).value;
    }

    //Convenience getters

    public String getString(int index, String defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asString();
        }
        return defaultValue;
    }

    public boolean getBoolean(int index, boolean defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asBoolean(defaultValue);
        }
        return defaultValue;
    }

    public byte getByte(int index, byte defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asByte(defaultValue);
        }
        return defaultValue;
    }

    public char getChar(int index, char defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asChar(defaultValue);
        }
        return defaultValue;
    }

    public short getShort(int index, short defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asShort(defaultValue);
        }
        return defaultValue;
    }

    public int getInt(int index, int defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asInt(defaultValue);
        }
        return defaultValue;
    }

    public long getLong(int index, long defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asLong(defaultValue);
        }
        return defaultValue;
    }

    public float getFloat(int index, float defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asFloat(defaultValue);
        }
        return defaultValue;
    }

    public double getDouble(int index, double defaultValue) {
        JsonElement elem = get(index);
        if (elem != null && elem instanceof JsonPrimitive) {
            return ((JsonPrimitive) elem).asDouble(defaultValue);
        }
        return defaultValue;
    }

    public String getComment(int i) {
        return entries.get(i).comment;
    }

    public void setComment(int i, String comment) {
        entries.get(i).comment = comment;
    }

    @Override
    public String toJson(boolean comments, boolean newlines, int depth) {
        JsonGrammar grammar = JsonGrammar.builder().withComments(comments).printWhitespace(newlines).build();
        return toJson(grammar, depth);
    }

    @Override
    public String toJson(JsonGrammar grammar, int depth) {
        StringBuilder builder = new StringBuilder();

        int effectiveDepth = (grammar.bareRootObject) ? Math.max(depth - 1, 0) : depth;
        int nextDepth = (grammar.bareRootObject) ? depth - 1 : depth;

        builder.append("[");

        if (entries.size() > 0) {
            if (grammar.printWhitespace) {
                builder.append('\n');
            } else {
                builder.append(' ');
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);

            if (grammar.printWhitespace) {
                for (int j = 0; j < effectiveDepth + 1; j++) {
                    builder.append("\t");
                }
            }

            CommentSerializer.print(builder, entry.comment, effectiveDepth, grammar);

            builder.append(entry.value.toJson(grammar, depth + 1));

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

        if (entries.size() > 0) {
            if (grammar.printWhitespace && depth > 0) {
                for (int j = 0; j < effectiveDepth; j++) {
                    builder.append("\t");
                }
            }
        }

        if (entries.size() > 0) {
            if (!grammar.printWhitespace) builder.append(' ');
        }

        builder.append(']');

        return builder.toString();
    }

    public String toString() {
        return toJson(true, false, 0);
    }

    public boolean add(@Nonnull JsonElement e, String comment) {
        //if (contains(e)) return false;

        Entry entry = new Entry();
        entry.value = e;
        entry.comment = comment;
        entries.add(entry);
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof JsonArray)) return false;

        List<Entry> a = this.entries;
        List<Entry> b = ((JsonArray) other).entries;
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            Entry ae = a.get(i);
            Entry be = b.get(i);
            if (!ae.value.equals(be.value)) return false;
            if (!Objects.equals(ae.comment, be.comment)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Nullable
    public <E> E get(@Nonnull Class<E> clazz, int index) {
        JsonElement elem = get(index);
        return marshaller.marshall(clazz, elem);
    }

    public Marshaller getMarshaller() {
        return this.marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    //IMPLEMENTATION for Cloneable
    @Override
    public JsonArray clone() {
        JsonArray result = new JsonArray();
        result.marshaller = marshaller;
        for (Entry entry : entries) {
            result.add(entry.value.clone(), entry.comment);
        }
        return result;
    }

    //implements List<JsonElement> {

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean add(@Nonnull JsonElement e) {
        Entry entry = new Entry();
        entry.value = e;
        entries.add(entry);
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends JsonElement> c) {
        boolean result = false;
        for (JsonElement elem : c) result |= add(elem);

        return result;
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public boolean contains(Object o) {
        if (o == null || !(o instanceof JsonElement)) return false;

        for (Entry entry : entries) {
            if (entry.value.equals(o)) return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) return false;
        }

        return true;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean remove(Object o) {
        for (int i = 0; i < entries.size(); i++) {
            Entry cur = entries.get(i);
            if (cur.value.equals(o)) {
                entries.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("removeAll not supported");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll not supported");
    }

    @Override
    public JsonElement[] toArray() {
        JsonElement[] result = new JsonElement[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            result[i] = entries.get(i).value;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        if (a.length < entries.size()) a = (T[]) new Object[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            a[i] = (T) entries.get(i).value;
        }
        if (a.length > entries.size()) {
            a[entries.size()] = null; //Little-known and basically unused quirk of the toArray contract
        }
        return a;
    }

    @Override
    public Iterator<JsonElement> iterator() {
        return new EntryIterator(entries);
    }

    @Override
    public void add(int index, JsonElement element) {
        entries.add(index, new Entry(element));
    }

    @Override
    public boolean addAll(int index, Collection<? extends JsonElement> elements) {
        if (elements.isEmpty()) return false;
        int i = index;
        for (JsonElement element : elements) {
            entries.add(i, new Entry(element));
            i++;
        }
        return true;
    }

    @Override
    public int indexOf(Object obj) {
        if (obj == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            JsonElement val = entries.get(i).value;
            if (val != null && val.equals(obj)) return i;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object obj) {
        if (obj == null) return -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonElement val = entries.get(i).value;
            if (val != null && val.equals(obj)) return i;
        }
        return -1;
    }

    @Override
    public ListIterator<JsonElement> listIterator() {
        return new EntryIterator(entries);
    }

    @Override
    public ListIterator<JsonElement> listIterator(int index) {
        return new EntryIterator(entries, index);
    }

    @Override
    public JsonElement remove(int index) {
        return entries.remove(index).value;
    }

    @Override
    public JsonElement set(int index, JsonElement element) {
        Entry old = entries.set(index, new Entry(element));
        return (old == null) ? null : old.value;
    }

    @Override
    public List<JsonElement> subList(int arg0, int arg1) {
        throw new UnsupportedOperationException(); //TODO: Implement
    }

    //}


    //MISC CLASSES

    private static class EntryIterator implements ListIterator<JsonElement> {
        private final ListIterator<Entry> delegate;

        public EntryIterator(List<Entry> list) {
            delegate = list.listIterator();
        }

        public EntryIterator(List<Entry> list, int index) {
            delegate = list.listIterator(index);
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public JsonElement next() {
            return delegate.next().value;
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        @Override
        public void add(JsonElement elem) {
            delegate.add(new Entry(elem));
        }

        @Override
        public boolean hasPrevious() {
            return delegate.hasPrevious();
        }

        @Override
        public int nextIndex() {
            return delegate.nextIndex();
        }

        @Override
        public JsonElement previous() {
            return delegate.previous().value;
        }

        @Override
        public int previousIndex() {
            return delegate.previousIndex();
        }

        @Override
        public void set(JsonElement obj) {
            delegate.set(new Entry(obj));
        }
    }

    private static class Entry {
        String comment;
        JsonElement value;

        public Entry() {
        }

        public Entry(JsonElement value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof final Entry o)) return false;
            return Objects.equals(comment, o.comment) &&
                Objects.equals(value, o.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(comment, value);
        }
    }
}
