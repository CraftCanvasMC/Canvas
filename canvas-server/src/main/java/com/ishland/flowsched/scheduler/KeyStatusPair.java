package com.ishland.flowsched.scheduler;

import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
public final class KeyStatusPair<K, V, Ctx> {
    private final K key;
    private final ItemStatus<K, V, Ctx> status;

    public KeyStatusPair(K key, ItemStatus<K, V, Ctx> status) {
        this.key = key;
        this.status = status;
    }

    public K key() {
        return key;
    }

    public ItemStatus<K, V, Ctx> status() {
        return status;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (KeyStatusPair) obj;
        return Objects.equals(this.key, that.key) &&
               Objects.equals(this.status, that.status);
    }

    @Override
    public int hashCode() {
        // inlined Objects.hash(key, status)
        int result = 1;

        result = 31 * result + key.hashCode();
        result = 31 * result + status.hashCode();

        return result;
    }

    @Override
    public String toString() {
        return "KeyStatusPair[" +
               "key=" + key + ", " +
               "status=" + status + ']';
    }

}
