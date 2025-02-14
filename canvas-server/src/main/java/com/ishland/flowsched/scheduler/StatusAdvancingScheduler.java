package com.ishland.flowsched.scheduler;

import com.ishland.flowsched.util.Assertions;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/**
 * A scheduler that advances status of items.
 *
 * @param <K> the key type
 * @param <V> the item type
 * @param <Ctx> the context type
 */
public abstract class StatusAdvancingScheduler<K, V, Ctx, UserData> {

    public static final Runnable NO_OP = () -> {
    };

    private final StampedLock itemsLock = new StampedLock();
    private final Object2ReferenceOpenHashMap<K, ItemHolder<K, V, Ctx, UserData>> items = new Object2ReferenceOpenHashMap<>();
    private final AtomicInteger updateSize = new AtomicInteger();
    private final Queue<K> pendingUpdates;
    private final ObjectLinkedOpenHashSet<K> pendingUpdatesInternal = new ObjectLinkedOpenHashSet<>() {
        @Override
        protected void rehash(int newN) {
            if (n < newN) {
                super.rehash(newN);
            }
        }
    };
    private final ObjectFactory objectFactory;

    protected StatusAdvancingScheduler() {
        this(new ObjectFactory.DefaultObjectFactory());
    }

    protected StatusAdvancingScheduler(ObjectFactory objectFactory) {
        this.objectFactory = Objects.requireNonNull(objectFactory);
        this.pendingUpdates = this.objectFactory.newMPSCQueue();
    }

    protected abstract Executor getExecutor();

    protected Scheduler getSchedulerBackedByExecutor() {
        return Schedulers.from(getExecutor());
    }

    protected Executor getBackgroundExecutor() {
        return getExecutor();
    }

    protected Scheduler getSchedulerBackedByBackgroundExecutor() {
        return Schedulers.from(getBackgroundExecutor());
    }

    protected abstract ItemStatus<K, V, Ctx> getUnloadedStatus();

    protected abstract Ctx makeContext(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, KeyStatusPair<K, V, Ctx>[] dependencies, boolean isUpgrade);

    protected ExceptionHandlingAction handleTransactionException(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, boolean isUpgrade, Throwable throwable) {
        throwable.printStackTrace();
        return ExceptionHandlingAction.MARK_BROKEN;
    }

    protected void handleUnrecoverableException(Throwable throwable) {
    }

    /**
     * Called when an item is created.
     *
     * @implNote This method is called before the item is added to the internal map. Make sure to not access the item from the map.
     *           May get called from any thread.
     * @param holder
     */
    protected void onItemCreation(ItemHolder<K, V, Ctx, UserData> holder) {
    }

    /**
     * Called when an item is deleted.
     *
     * @implNote This method is called when the monitor of the holder is held.
     * @param holder
     */
    protected void onItemRemoval(ItemHolder<K, V, Ctx, UserData> holder) {
    }

    protected void onItemUpgrade(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> statusReached) {
    }

    protected void onItemDowngrade(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> statusReached) {
    }

    public boolean tick() {
        {
            K key;
            while ((key = this.pendingUpdates.poll()) != null) {
                this.pendingUpdatesInternal.addAndMoveToLast(key);
                this.updateSize.decrementAndGet();
            }
        }

        boolean hasWork = false;
        while (!this.pendingUpdatesInternal.isEmpty()) {
            hasWork = true;
            K key = this.pendingUpdatesInternal.removeFirst();
            ItemHolder<K, V, Ctx, UserData> holder = getHolder(key);
            if (holder == null) {
                continue;
            }
            synchronized (holder) {
                final ItemStatus<K, V, Ctx> current = holder.getStatus();
                ItemStatus<K, V, Ctx> nextStatus = getNextStatus(current, holder.getTargetStatus());
                if (holder.isBusy()) {
                    final ItemStatus<K, V, Ctx> upgradingStatusTo = holder.upgradingStatusTo();
                    ItemStatus<K, V, Ctx> projectedCurrent = upgradingStatusTo != null ? upgradingStatusTo : current;
                    if (projectedCurrent.ordinal() > nextStatus.ordinal()) {
                        getExecutor().execute(holder::tryCancelUpgradeAction);
                    }
                    holder.consolidateMarkDirty(this);
                    continue;
                }
                if (nextStatus == current) {
                    if (current.equals(getUnloadedStatus())) {
                        if (holder.isDependencyDirty()) { // schedule dependency cleanup async
                            holder.submitOp(CompletableFuture.runAsync(() -> {
                                holder.cleanupDependencies(this);
                            }, getBackgroundExecutor()));
                            holder.consolidateMarkDirty(this);
                            continue;
                        }
                        if (holder.holdsDependency()) {
                            if (holder.isDependencyDirty()) {
                                markDirty(holder.getKey()); // should rarely happen
                                continue;
                            }
                            System.err.println(String.format("BUG: %s still holds some dependencies when ready for unloading", holder.getKey()));
                        }
//                    System.out.println("Unloaded: " + key);
                        this.onItemRemoval(holder);
                        holder.release();
                        final long lock = this.itemsLock.writeLock();
                        try {
                            this.items.remove(key);
                        } finally {
                            this.itemsLock.unlockWrite(lock);
                        }
                        continue;
                    }
                    holder.submitOp(CompletableFuture.runAsync(() -> holder.cleanupDependencies(this), getBackgroundExecutor()));
                    continue; // No need to update
                }
                if (current.ordinal() < nextStatus.ordinal()) {
                    if ((holder.getFlags() & ItemHolder.FLAG_BROKEN) != 0) {
                        continue; // not allowed to upgrade
                    }
                    holder.submitOp(CompletableFuture.runAsync(() -> advanceStatus0(holder, nextStatus, key), getBackgroundExecutor()));
                } else {
                    final boolean success = holder.setStatus(nextStatus, false);
                    if (!success) {
                        continue; // target status is modified to be higher
                    }
                    holder.submitOp(CompletableFuture.runAsync(() -> downgradeStatus0(holder, current, nextStatus, key), getExecutor()));
                }
            }
        }
        return hasWork;
    }

    private void downgradeStatus0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> current, ItemStatus<K, V, Ctx> nextStatus, K key) {
        // Downgrade
        final KeyStatusPair<K, V, Ctx>[] dependencies = holder.getDependencies(current);
        Assertions.assertTrue(dependencies != null, "No dependencies for downgrade");

        Cancellable cancellable = new Cancellable();

        final Completable completable = Completable.defer(() -> {
                    Assertions.assertTrue(holder.isBusy());
                    final Ctx ctx = makeContext(holder, current, dependencies, false);
                    final CompletionStage<Void> stage = current.downgradeFromThis(ctx, cancellable);
                    return Completable.fromCompletionStage(stage);
                })
                .subscribeOn(getSchedulerBackedByBackgroundExecutor())
                .observeOn(getSchedulerBackedByExecutor())
                .doOnEvent((throwable) -> {
                    try {
                        Assertions.assertTrue(holder.isBusy());

                        {
                            Throwable actual = throwable;
                            while (actual instanceof CompletionException ex) actual = ex.getCause();
                            if (cancellable.isCancelled() && actual instanceof CancellationException) {
                                holder.setStatus(current, true);
                                holder.consolidateMarkDirty(this);
                                return;
                            }
                        }

                        final ExceptionHandlingAction action = this.tryHandleTransactionException(holder, nextStatus, false, throwable);
                        switch (action) {
                            case PROCEED -> {
                                releaseDependencies(holder, current);
                            }
                            case MARK_BROKEN -> {
                                holder.setFlag(ItemHolder.FLAG_BROKEN);
                                clearDependencies0(holder, current);
                            }
                        }
                        holder.consolidateMarkDirty(this);
                        onItemDowngrade(holder, nextStatus);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
        holder.subscribeOp(completable);
    }

    private void advanceStatus0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key) {
        // Advance
        final KeyStatusPair<K, V, Ctx>[] dependencies = nextStatus.getDependencies(holder);
        final CancellationSignaller dependencyCompletable = getDependencyFuture0(dependencies, holder, nextStatus);
        Cancellable cancellable = new Cancellable();

        CancellationSignaller signaller = new CancellationSignaller(unused -> {
            cancellable.cancel();
            dependencyCompletable.cancel();
        });

        final Completable completable = Completable.create(emitter -> dependencyCompletable.addListener(throwable -> {
                    if (throwable != null) {
                        emitter.onError(throwable);
                    } else {
                        emitter.onComplete();
                    }
                }))
                .observeOn(getSchedulerBackedByBackgroundExecutor())
                .andThen(Completable.defer(() -> {
                    Assertions.assertTrue(holder.isBusy());
                    final Ctx ctx = makeContext(holder, nextStatus, dependencies, false);
                    final CompletionStage<Void> stage = nextStatus.upgradeToThis(ctx, cancellable);
                    return Completable.fromCompletionStage(stage).cache();
                }))
                .observeOn(getSchedulerBackedByBackgroundExecutor())
                .doOnEvent(throwable -> {
                    try {
                        Assertions.assertTrue(holder.isBusy());

                        {
                            Throwable actual = throwable;
                            while (actual instanceof CompletionException ex) actual = ex.getCause();
                            if (cancellable.isCancelled() && actual instanceof CancellationException) {
                                if (holder.getDependencies(nextStatus) != null) {
                                    releaseDependencies(holder, nextStatus);
                                }
                                try {
                                    signaller.fireComplete(actual);
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                }
                                holder.consolidateMarkDirty(this);
                                return;
                            }
                        }

                        Assertions.assertTrue(holder.getDependencies(nextStatus) != null);

                        final ExceptionHandlingAction action = this.tryHandleTransactionException(holder, nextStatus, true, throwable);
                        switch (action) {
                            case PROCEED -> {
                                holder.setStatus(nextStatus, false);
                                rerequestDependencies(holder, nextStatus);
                                holder.consolidateMarkDirty(this);
                                onItemUpgrade(holder, nextStatus);
                            }
                            case MARK_BROKEN -> {
                                holder.setFlag(ItemHolder.FLAG_BROKEN);
                                clearDependencies0(holder, nextStatus);
                                holder.consolidateMarkDirty(this);
                            }
                        }

                        try {
                            signaller.fireComplete(null);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } catch (Throwable t) {
                        try {
                            holder.setFlag(ItemHolder.FLAG_BROKEN);
                            clearDependencies0(holder, nextStatus);
                            holder.consolidateMarkDirty(this);
                        } catch (Throwable t1) {
                            t.addSuppressed(t1);
                        }
                        t.printStackTrace();
                    }
                })
                .onErrorComplete()
                .cache();

        holder.submitUpgradeAction(signaller, nextStatus);
        holder.subscribeOp(completable);
        completable.subscribe(() -> signaller.fireComplete(null), signaller::fireComplete);
    }

    private void rerequestDependencies(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> status) { // sync externally
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(status);
        final KeyStatusPair<K, V, Ctx>[] newDep = status.getDependencies(holder);
        final KeyStatusPair<K, V, Ctx>[] toAdd = status.getDependenciesToAdd(holder);
        final KeyStatusPair<K, V, Ctx>[] toRemove = status.getDependenciesToRemove(holder);
        holder.setDependencies(status, null);
        holder.setDependencies(status, newDep);
        for (KeyStatusPair<K, V, Ctx> pair : toAdd) {
            holder.addDependencyTicket(this, pair.key(), pair.status(), NO_OP);
        }
        for (KeyStatusPair<K, V, Ctx> pair : toRemove) {
            holder.removeDependencyTicket(pair.key(), pair.status());
        }
    }

    public ItemHolder<K, V, Ctx, UserData> getHolder(K key) {
        long stamp = this.itemsLock.tryOptimisticRead();
        if (stamp != 0L) {
            try {
                ItemHolder<K, V, Ctx, UserData> holder = this.items.get(key);
                if (this.itemsLock.validate(stamp)) {
                    return holder;
                }
                // fall through
            } catch (Throwable ignored) {
                // fall through
            }
        }

        stamp = this.itemsLock.readLock();
        try {
            return this.items.get(key);
        } finally {
            this.itemsLock.unlockRead(stamp);
        }
    }

    private ItemHolder<K, V, Ctx, UserData> getOrCreateHolder(K key) {
        final ItemHolder<K, V, Ctx, UserData> holder = getHolder(key);
        if (holder != null) {
            return holder;
        }
        final long lock = this.itemsLock.writeLock();
        try {
            return this.items.computeIfAbsent(key, this::createHolder);
        } finally {
            this.itemsLock.unlockWrite(lock);
        }
    }

    public int itemCount() {
        VarHandle.acquireFence();
        return this.items.size();
    }

    protected void markDirty(K key) {
        boolean needWakeup = this.updateSize.getAndIncrement() == 0;
        this.pendingUpdates.add(key);
        if (needWakeup) wakeUp();
    }

    protected void wakeUp() {
    }

    private CancellationSignaller getDependencyFuture0(KeyStatusPair<K, V, Ctx>[] dependencies, ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus) {
        AtomicInteger satisfied = new AtomicInteger(0);
        final int size = dependencies.length;
        holder.setDependencies(nextStatus, dependencies);
        if (size == 0) {
            return CancellationSignaller.COMPLETED;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        final CancellationSignaller signaller = new CancellationSignaller(signaller1 -> {
            if (satisfied.get() == 0);
            if (finished.compareAndSet(false, true)) {
                releaseDependencies(holder, nextStatus);
                signaller1.fireComplete(new CancellationException());
            }
        });
        try {
            final KeyStatusPair<K, V, Ctx> keyStatusPair = new KeyStatusPair<>(holder.getKey(), nextStatus);
            for (KeyStatusPair<K, V, Ctx> dependency : dependencies) {
                Assertions.assertTrue(!dependency.key().equals(holder.getKey()));
                holder.addDependencyTicket(this, dependency.key(), dependency.status(), () -> {
//                    Assertions.assertTrue(this.getHolder(dependency.key()).getStatus().ordinal() >= dependency.status().ordinal());
                    final int incrementAndGet = satisfied.incrementAndGet();
                    Assertions.assertTrue(incrementAndGet <= size, "Satisfied more than expected");
                    if (incrementAndGet == size) {
                        if (finished.compareAndSet(false, true)) {
                            getExecutor().execute(() -> signaller.fireComplete(null));
                        }
                    }
                });
            }
        } catch (Throwable t) {
            signaller.fireComplete(t);
        }
        return signaller;
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket(key, key, targetStatus, callback);
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, Object source, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket(key, ItemTicket.TicketType.EXTERNAL, source, targetStatus, callback);
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, ItemTicket.TicketType type, Object source, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket0(key, new ItemTicket<>(type, source, targetStatus, callback));
    }

    private ItemHolder<K, V, Ctx, UserData> addTicket0(K key, ItemTicket<K, V, Ctx> ticket) {
        if (this.getUnloadedStatus().equals(ticket.getTargetStatus())) {
            throw new IllegalArgumentException("Cannot add ticket to unloaded status");
        }
        try {
            while (true) {
                ItemHolder<K, V, Ctx, UserData> holder = this.getOrCreateHolder(key);

                synchronized (holder) {
                    if (!holder.isOpen()) {
                        // holder got removed before we had chance to add a ticket to it, retry
//                        System.out.println(String.format("Retrying addTicket0(%s, %s)", key, ticket));
                        continue;
                    }
                    holder.busyRefCounter().incrementRefCount();
                }
                try {
                    holder.addTicket(ticket);
                    holder.consolidateMarkDirty(this);
                } finally {
                    holder.busyRefCounter().decrementRefCount();
                }
                return holder;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
    }

    private ItemHolder<K, V, Ctx, UserData> createHolder(K k) {
        final ItemHolder<K, V, Ctx, UserData> holder1 = new ItemHolder<>(this.getUnloadedStatus(), k, this.objectFactory);
        this.onItemCreation(holder1);
        VarHandle.fullFence();
        return holder1;
    }

    public void removeTicket(K key, ItemStatus<K, V, Ctx> targetStatus) {
        this.removeTicket(key, ItemTicket.TicketType.EXTERNAL, key, targetStatus);
    }

    public void removeTicket(K key, ItemTicket.TicketType type, Object source, ItemStatus<K, V, Ctx> targetStatus) {
        ItemHolder<K, V, Ctx, UserData> holder = this.getHolder(key);
        if (holder == null) {
            throw new IllegalStateException("No such item");
        }
        holder.removeTicket(new ItemTicket<>(type, source, targetStatus, null));
        this.markDirty(key);
    }

    private ItemStatus<K, V, Ctx> getNextStatus(ItemStatus<K, V, Ctx> current, ItemStatus<K, V, Ctx> target) {
        Assertions.assertTrue(target != null);
        final int compare = Integer.compare(current.ordinal(), target.ordinal());
        if (compare < 0) {
            return current.getNext();
        } else if (compare == 0) {
            return current;
        } else {
            return current.getPrev();
        }
    }

    private ExceptionHandlingAction tryHandleTransactionException(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, boolean isUpgrade, Throwable throwable) {
        if (throwable == null) { // no exception to handle
            return ExceptionHandlingAction.PROCEED;
        }
        try {
            return this.handleTransactionException(holder, nextStatus, isUpgrade, throwable);
        } catch (Throwable t) {
            t.printStackTrace();
            return ExceptionHandlingAction.MARK_BROKEN;
        }
    }

    private void clearDependencies0(final ItemHolder<K, V, Ctx, UserData> holder, final ItemStatus<K, V, Ctx> fromStatus) { // sync externally
        for (int i = fromStatus.ordinal(); i > 0; i--) {
            final ItemStatus<K, V, Ctx> status = this.getUnloadedStatus().getAllStatuses()[i];
            this.releaseDependencies(holder, status);
            holder.setDependencies(status, new KeyStatusPair[0]);
        }
    }

    private void releaseDependencies(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> status) {
        final KeyStatusPair<K, V, Ctx>[] dependencies = holder.getDependencies(status);
        for (KeyStatusPair<K, V, Ctx> dependency : dependencies) {
            holder.removeDependencyTicket(dependency.key(), dependency.status());
        }
        holder.setDependencies(status, null);
    }

    protected boolean hasPendingUpdates() {
        return !this.pendingUpdates.isEmpty();
    }

    protected boolean continueProcessWork() {
        return this.updateSize.get() != 0;
    }

}
