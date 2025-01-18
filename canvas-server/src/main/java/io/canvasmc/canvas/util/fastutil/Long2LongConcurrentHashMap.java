package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Long2LongConcurrentHashMap implements Long2LongMap {

    public Map<Long, Long> backing = new ConcurrentHashMap<>();
    long defaultRV;

    public Long2LongConcurrentHashMap(long defaultRV) {
        this.defaultRV = defaultRV;
    }

    @Override
    public long get(long key) {
        if (backing.containsKey(key)) {
            return backing.get(key);
        } else return defaultRV;
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public long put(final long key, final long val) {
        backing.put(key,val);
        return val;
    }

    @Override
    public Long put(final Long key, final Long val) {
        backing.put(key,val);
        return val;
    }

    @Override
    public long remove(final long key) {
        try {
            return backing.remove(key);
        }catch (NullPointerException e){
            return 0;
        }
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Long> m) {
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(long rv) {
        defaultRV = rv;
    }

    @Override
    public long defaultReturnValue() {
        return defaultRV;
    }

    @Override
    public ObjectSet<Entry> long2LongEntrySet() {
        return FastUtilHackUtil.entrySetLongLongWrap(backing);
    }


    @Override
    public LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public LongCollection values() {
        return FastUtilHackUtil.wrapLongs(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public boolean containsValue(long value) {
        return backing.containsValue(value);
    }


}
