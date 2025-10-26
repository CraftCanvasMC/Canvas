package io.canvasmc.canvas.util;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import java.util.Map;

public class EntityLockedObject2ObjectMap<K, V> implements Object2ObjectMap<K, V> {
    private final Entity entity;
    private final Object2ObjectOpenHashMap<K, V> wrapped;

    public EntityLockedObject2ObjectMap(final Entity entity) {
        this.entity = entity;
        this.wrapped = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public void clear() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        wrapped.clear();
    }

    @Override
    public V remove(final Object key) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.remove(key);
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.remove(key, value);
    }

    @Override
    public int size() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.size();
    }

    @Override
    public boolean isEmpty() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.isEmpty();
    }

    @Override
    public V get(final Object key) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.get(key);
    }

    @Override
    public V put(final K key, final V value) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.put(key, value);
    }

    @Override
    public void putAll(@NotNull final Map<? extends K, ? extends V> m) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        wrapped.putAll(m);
    }

    @Override
    public void defaultReturnValue(final V rv) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        wrapped.defaultReturnValue(rv);
    }

    @Override
    public V defaultReturnValue() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.defaultReturnValue();
    }

    @Override
    public ObjectSet<Entry<K, V>> object2ObjectEntrySet() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.object2ObjectEntrySet();
    }

    @Override
    public @NotNull ObjectSet<K> keySet() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.keySet();
    }

    @Override
    public @NotNull ObjectCollection<V> values() {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.values();
    }

    @Override
    public boolean containsKey(final Object key) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        TickThread.ensureTickThread(entity, "Can only access this map on the owning entity");
        return wrapped.containsValue(value);
    }
}
