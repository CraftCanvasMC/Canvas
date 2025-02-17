package com.ishland.flowsched.scheduler;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.concurrent.CompletionStage;

/**
 * Represents the status of an item.
 * <p>
 * Implementations must also implement {@link Comparable}, and higher statuses must be greater than lower statuses.
 *
 * @param <Ctx> the context type
 */
public interface ItemStatus<K, V, Ctx> {

    @SuppressWarnings("rawtypes")
    static KeyStatusPair[] EMPTY_DEPENDENCIES = new KeyStatusPair[0];

    default ItemStatus<K, V, Ctx> getPrev() {
        if (this.ordinal() > 0) {
            return getAllStatuses()[this.ordinal() - 1];
        } else {
            return null;
        }
    }

    default ItemStatus<K, V, Ctx> getNext() {
        final ItemStatus<K, V, Ctx>[] allStatuses = getAllStatuses();
        if (this.ordinal() < allStatuses.length - 1) {
            return allStatuses[this.ordinal() + 1];
        } else {
            return null;
        }
    }

    ItemStatus<K, V, Ctx>[] getAllStatuses();

    int ordinal();

    CompletionStage<Void> upgradeToThis(Ctx context, Cancellable cancellable);

    CompletionStage<Void> downgradeFromThis(Ctx context, Cancellable cancellable);

    /**
     * Get the dependencies of the given item at the given status.
     * <p>
     * The returned collection must not contain the given item itself.
     *
     * @param holder the item holder
     * @return the dependencies
     */
    KeyStatusPair<K, V, Ctx>[] getDependencies(ItemHolder<K, V, Ctx, ?> holder);

    default KeyStatusPair<K, V, Ctx>[] getDependenciesToRemove(ItemHolder<K, V, Ctx, ?> holder) {
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(this);
        final KeyStatusPair<K, V, Ctx>[] newDep = this.getDependencies(holder);
        final ObjectOpenHashSet<KeyStatusPair<K, V, Ctx>> toRemove = new ObjectOpenHashSet<>(curDep);
        for (KeyStatusPair<K, V, Ctx> pair : newDep) {
            toRemove.remove(pair);
        }
        return toRemove.toArray(KeyStatusPair[]::new);
    }

    default KeyStatusPair<K, V, Ctx>[] getDependenciesToAdd(ItemHolder<K, V, Ctx, ?> holder) {
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(this);
        final KeyStatusPair<K, V, Ctx>[] newDep = this.getDependencies(holder);
        final ObjectOpenHashSet<KeyStatusPair<K, V, Ctx>> toAdd = new ObjectOpenHashSet<>(newDep);
        for (KeyStatusPair<K, V, Ctx> pair : curDep) {
            toAdd.remove(pair);
        }
        return toAdd.toArray(KeyStatusPair[]::new);
    }

}
