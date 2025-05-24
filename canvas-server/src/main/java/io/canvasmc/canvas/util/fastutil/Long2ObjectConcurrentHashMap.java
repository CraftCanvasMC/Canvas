package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Long2ObjectConcurrentHashMap<O> implements Long2ObjectMap<O> {

    public final Map<Long, O> backing;
    O defaultRV;

    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor) {
        this.defaultRV = null;
        this.backing = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    @Override
    public O get(long key) {
        if (backing.containsKey(key)) {
            return backing.get(key);
        } else return defaultRV;
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean containsValue(final Object value) {
        return false;
    }

    @Override
    public O put(final long key, final O val) {
        backing.put(key, val);
        return val;
    }

    @Override
    public O put(final Long key, final O val) {
        backing.put(key, val);
        return val;
    }

    @Override
    public O remove(final long key) {
        try {
            return backing.remove(key);
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public void putAll(Map<? extends Long, ? extends O> m) {
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(O rv) {
        defaultRV = rv;
    }

    @Override
    public O defaultReturnValue() {
        return defaultRV;
    }

    @Override
    public ObjectSet<Entry<O>> long2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetLongWrap(backing);
    }

    @Override
    public @NotNull LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public @NotNull ObjectCollection<O> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

}
