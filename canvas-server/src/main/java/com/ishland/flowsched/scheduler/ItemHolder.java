package com.ishland.flowsched.scheduler;

import com.ishland.flowsched.util.Assertions;
import io.reactivex.rxjava3.core.Completable;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class ItemHolder<K, V, Ctx, UserData> {

    private static final VarHandle FUTURES_HANDLE = MethodHandles.arrayElementVarHandle(CompletableFuture[].class);

    public static final IllegalStateException UNLOADED_EXCEPTION = new IllegalStateException("Not loaded");
    private static final CompletableFuture<Void> UNLOADED_FUTURE = CompletableFuture.failedFuture(UNLOADED_EXCEPTION);
    private static final CompletableFuture<Void> COMPLETED_VOID_FUTURE = CompletableFuture.completedFuture(null);

    @SuppressWarnings("PointlessBitwiseExpression")
    public static final int FLAG_REMOVED = 1 << 0;
    /**
     * Indicates the holder have been marked broken
     * If set, the holder:
     * - will not be allowed to be upgraded any further
     * - will still be allowed to be downgraded, but operations to it should be careful
     */
    public static final int FLAG_BROKEN = 1 << 1;
    /**
     * Indicates the holder have at least one failed transactions and proceeded to retry
     */
    public static final int FLAG_HAVE_RETRIED = 1 << 2;

    private final K key;
    private final ItemStatus<K, V, Ctx> unloadedStatus;
    private final AtomicReference<V> item = new AtomicReference<>();
    private final AtomicReference<UserData> userData = new AtomicReference<>();
    private final BusyRefCounter busyRefCounter = new BusyRefCounter();
    private final AtomicReference<Pair<CancellationSignaller, ItemStatus<K, V, Ctx>>> runningUpgradeAction = new AtomicReference<>();
    private final TicketSet<K, V, Ctx> tickets;
    private volatile ItemStatus<K, V, Ctx> status = null;
//    private final List<Pair<ItemStatus<K, V, Ctx>, Long>> statusHistory = ReferenceLists.synchronize(new ReferenceArrayList<>());
    private final KeyStatusPair<K, V, Ctx>[][] requestedDependencies;
    private final CompletableFuture<Void>[] futures;
    private final AtomicInteger flags = new AtomicInteger(0);
    private final Object2ReferenceLinkedOpenHashMap<K, DependencyInfo> dependencyInfos = new Object2ReferenceLinkedOpenHashMap<>() {
        @Override
        protected void rehash(int newN) {
            if (n < newN) {
                super.rehash(newN);
            }
        }
    };
    private boolean dependencyDirty = false;

    ItemHolder(ItemStatus<K, V, Ctx> initialStatus, K key, ObjectFactory objectFactory) {
        this.unloadedStatus = Objects.requireNonNull(initialStatus);
        this.status = this.unloadedStatus;
        this.key = Objects.requireNonNull(key);
        this.tickets = new TicketSet<>(this.unloadedStatus, objectFactory);

        ItemStatus<K, V, Ctx>[] allStatuses = initialStatus.getAllStatuses();
        this.futures = new CompletableFuture[allStatuses.length];
        this.requestedDependencies = new KeyStatusPair[allStatuses.length][];
        for (int i = 0, allStatusesLength = allStatuses.length; i < allStatusesLength; i++) {
            this.futures[i] = UNLOADED_FUTURE;
            this.requestedDependencies[i] = null;
        }
        VarHandle.fullFence();
    }

    private void createFutures() {
        assertOpen();
        synchronized (this.futures) {
            final ItemStatus<K, V, Ctx> targetStatus = this.getTargetStatus();
            for (int i = this.unloadedStatus.ordinal() + 1; i <= targetStatus.ordinal(); i++) {
                this.futures[i] = this.futures[i] == UNLOADED_FUTURE ? new CompletableFuture<>() : this.futures[i];
            }
        }
    }

    /**
     * Get the target status of this item.
     *
     * @return the target status of this item, or null if no ticket is present
     */
    public ItemStatus<K, V, Ctx> getTargetStatus() {
        return this.tickets.getTargetStatus();
    }

    public ItemStatus<K, V, Ctx> getStatus() {
        return this.status;
    }

    public synchronized boolean isBusy() {
        assertOpen();
        return busyRefCounter.isBusy();
    }

    public ItemStatus<K, V, Ctx> upgradingStatusTo() {
        assertOpen();
        final Pair<CancellationSignaller, ItemStatus<K, V, Ctx>> pair = this.runningUpgradeAction.get();
        return pair != null ? pair.right() : null;
    }

    public void addTicket(ItemTicket<K, V, Ctx> ticket) {
        assertOpen();
        final boolean add = this.tickets.add(ticket);
        if (!add) {
            throw new IllegalStateException("Ticket already exists");
        }
        createFutures();
        boolean needConsumption;
        synchronized (this) {
            needConsumption = ticket.getTargetStatus().ordinal() <= this.getStatus().ordinal();
        }
        if (needConsumption) {
            ticket.consumeCallback();
        }
    }

    public void removeTicket(ItemTicket<K, V, Ctx> ticket) {
        assertOpen();
        final boolean remove = this.tickets.remove(ticket);
        if (!remove) {
            throw new IllegalStateException("Ticket does not exist");
        }
//        createFutures();
    }

    public void submitOp(CompletionStage<Void> op) {
        assertOpen();
//        this.opFuture.set(opFuture.get().thenCombine(op, (a, b) -> null).handle((o, throwable) -> null));
//        this.opFuture.getAndUpdate(future -> future.thenCombine(op, (a, b) -> null).handle((o, throwable) -> null));
        this.busyRefCounter.incrementRefCount();
        op.whenComplete((unused, throwable) -> this.busyRefCounter.decrementRefCount());
    }

    public void subscribeOp(Completable op) {
        assertOpen();
        this.busyRefCounter.incrementRefCount();
        op.onErrorComplete().subscribe(this.busyRefCounter::decrementRefCount);
    }

    BusyRefCounter busyRefCounter() {
        return this.busyRefCounter;
    }

    public void submitUpgradeAction(CancellationSignaller signaller, ItemStatus<K, V, Ctx> status) {
        assertOpen();
        final boolean success = this.runningUpgradeAction.compareAndSet(null, Pair.of(signaller, status));
        Assertions.assertTrue(success, "Only one action can happen at a time");
        signaller.addListener(unused -> this.runningUpgradeAction.set(null));
    }

    public void tryCancelUpgradeAction() {
        assertOpen();
        final Pair<CancellationSignaller, ItemStatus<K, V, Ctx>> signaller = this.runningUpgradeAction.get();
        if (signaller != null) {
            signaller.left().cancel();
        }
    }

    public CompletableFuture<Void> getOpFuture() { // best-effort
        assertOpen();
        if (!this.busyRefCounter.isBusy()) {
            return COMPLETED_VOID_FUTURE;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.busyRefCounter.addListener(() -> future.complete(null));
        return future;
    }

    public void submitOpListener(Runnable runnable) {
        assertOpen();
        this.busyRefCounter.addListener(runnable);
    }

    public void consolidateMarkDirty(StatusAdvancingScheduler<K, V, Ctx, ?> scheduler) {
        assertOpen();
        this.busyRefCounter.addListenerOnce(() -> scheduler.markDirty(this.getKey()));
    }

    public boolean setStatus(ItemStatus<K, V, Ctx> status, boolean isCancellation) {
        assertOpen();
        ItemTicket<K, V, Ctx>[] ticketsToFire = null;
        CompletableFuture<Void> futureToFire = null;
        synchronized (this) {
            final ItemStatus<K, V, Ctx> prevStatus = this.getStatus();
            Assertions.assertTrue(status != prevStatus, "duplicate setStatus call");
//            this.statusHistory.add(Pair.of(status, System.currentTimeMillis()));
            final int compare = Integer.compare(status.ordinal(), prevStatus.ordinal());
            if (compare < 0) { // status downgrade
                Assertions.assertTrue(prevStatus.getPrev() == status, "Invalid status downgrade");

                if (this.getTargetStatus().ordinal() > status.ordinal()) {
                    return false;
                }

                this.status = status;
                synchronized (this.futures) {
                    final ItemStatus<K, V, Ctx> targetStatus = this.getTargetStatus();
                    for (int i = prevStatus.ordinal(); i < this.futures.length; i ++) {
                        if (i > targetStatus.ordinal()) {
                            this.futures[i].completeExceptionally(UNLOADED_EXCEPTION);
                            this.futures[i] = UNLOADED_FUTURE;
                        } else {
                            this.futures[i] = this.futures[i].isDone() ? new CompletableFuture<>() : this.futures[i];
                        }
                    }
                }
            } else if (compare > 0) { // status upgrade
                Assertions.assertTrue(prevStatus.getNext() == status, "Invalid status upgrade");

                this.status = status;
                final CompletableFuture<Void> future = this.futures[status.ordinal()];

                if (!isCancellation) {
                    Assertions.assertTrue(future != UNLOADED_FUTURE);
                    Assertions.assertTrue(!future.isDone());
                }
                futureToFire = future;
                ticketsToFire = this.tickets.getTicketsForStatus(status).toArray(ItemTicket[]::new);
            }
        }
        if (ticketsToFire != null) {
            for (ItemTicket<K, V, Ctx> ticket : ticketsToFire) {
                ticket.consumeCallback();
            }
        }
        if (futureToFire != null) {
            futureToFire.complete(null);
        }
        return true;
    }

    public synchronized void setDependencies(ItemStatus<K, V, Ctx> status, KeyStatusPair<K, V, Ctx>[] dependencies) {
        assertOpen();
        final int ordinal = status.ordinal();
        if (dependencies != null) {
            Assertions.assertTrue(this.requestedDependencies[ordinal] == null, "Duplicate setDependencies call");
            this.requestedDependencies[ordinal] = dependencies;
        } else {
            Assertions.assertTrue(this.requestedDependencies[ordinal] != null, "Duplicate setDependencies call");
            this.requestedDependencies[ordinal] = null;
        }
    }

    public synchronized KeyStatusPair<K, V, Ctx>[] getDependencies(ItemStatus<K, V, Ctx> status) {
        assertOpen();
        return this.requestedDependencies[status.ordinal()];
    }

    public K getKey() {
        return this.key;
    }

    public synchronized CompletableFuture<Void> getFutureForStatus(ItemStatus<K, V, Ctx> status) {
        return this.futures[status.ordinal()].thenApply(Function.identity());
    }

    /**
     * Only for trusted methods
     */
    public synchronized CompletableFuture<Void> getFutureForStatus0(ItemStatus<K, V, Ctx> status) {
        return this.futures[status.ordinal()];
    }
    
    public AtomicReference<V> getItem() {
        return this.item;
    }

    /**
     * Get the user data of this item.
     *
     * @apiNote it is the caller's obligation to ensure the holder is not closed
     * @return the user data
     */
    public AtomicReference<UserData> getUserData() {
        return this.userData;
    }

    public int getFlags() {
        return this.flags.get();
    }

    public void setFlag(int flag) {
        assertOpen();
        this.flags.getAndUpdate(operand -> operand | flag);
    }

    void release() {
        assertOpen();
        this.tickets.assertEmpty();
        setFlag(FLAG_REMOVED);
    }

    public void addDependencyTicket(StatusAdvancingScheduler<K, V, Ctx, ?> scheduler, K key, ItemStatus<K, V, Ctx> status, Runnable callback) {
        synchronized (this.dependencyInfos) {
            final DependencyInfo info = this.dependencyInfos.computeIfAbsent(key, k -> new DependencyInfo(status.getAllStatuses().length));
            final int ordinal = status.ordinal();
            if (info.refCnt[ordinal] == -1) {
                info.refCnt[ordinal] = 0;
                info.callbacks[ordinal] = new ObjectArrayList<>();
                scheduler.addTicket(key, ItemTicket.TicketType.DEPENDENCY, this.getKey(), status, () -> {
                    final ObjectArrayList<Runnable> list;
                    synchronized (this.dependencyInfos) {
                        list = info.callbacks[ordinal];
                        if (list != null) {
                            info.callbacks[ordinal] = null;
                        }
                    }
                    if (list != null) {
                        for (Runnable runnable : list) {
                            try {
                                runnable.run();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    }
                });
            }
            info.refCnt[ordinal] ++;
            final ObjectArrayList<Runnable> list = info.callbacks[ordinal];
            if (list != null) {
                list.add(callback);
            } else {
                callback.run();
            }
        }
    }

    public void removeDependencyTicket(K key, ItemStatus<K, V, Ctx> status) {
        synchronized (this.dependencyInfos) {
            final DependencyInfo info = this.dependencyInfos.get(key);
            Assertions.assertTrue(info != null);
            final int old = info.refCnt[status.ordinal()]--;
            Assertions.assertTrue(old > 0);
            if (old == 1) {
                dependencyDirty = true;
            }
        }
    }

    public boolean isDependencyDirty() {
        synchronized (this.dependencyInfos) {
            return this.dependencyDirty;
        }
    }

    public boolean holdsDependency() {
        synchronized (this.dependencyInfos) {
            for (ObjectBidirectionalIterator<Object2ReferenceMap.Entry<K, DependencyInfo>> iterator = this.dependencyInfos.object2ReferenceEntrySet().fastIterator(); iterator.hasNext(); ) {
                final Object2ReferenceMap.Entry<K, DependencyInfo> entry = iterator.next();
                final DependencyInfo info = entry.getValue();
                int[] refCnt = info.refCnt;
                for (int i : refCnt) {
                    if (i != -1) return true;
                }
            }
            return false;
        }
    }

    public void cleanupDependencies(StatusAdvancingScheduler<K, V, Ctx, ?> scheduler) {
        synchronized (this.dependencyInfos) {
            if (!dependencyDirty) return;
            for (ObjectBidirectionalIterator<Object2ReferenceMap.Entry<K, DependencyInfo>> iterator = this.dependencyInfos.object2ReferenceEntrySet().fastIterator(); iterator.hasNext(); ) {
                Object2ReferenceMap.Entry<K, DependencyInfo> entry = iterator.next();
                final K key = entry.getKey();
                final DependencyInfo info = entry.getValue();
                int[] refCnt = info.refCnt;
                boolean isEmpty = true;
                for (int ordinal = 0, refCntLength = refCnt.length; ordinal < refCntLength; ordinal++) {
                    if (refCnt[ordinal] == 0) {
                        scheduler.removeTicket(key, ItemTicket.TicketType.DEPENDENCY, this.getKey(), this.unloadedStatus.getAllStatuses()[ordinal]);
                        refCnt[ordinal] = -1;
                        info.callbacks[ordinal] = null;
                    }
                    if (refCnt[ordinal] != -1) isEmpty = false;
                }
                if (isEmpty)
                    iterator.remove();
            }
            dependencyDirty = false;
        }
    }

    private void assertOpen() {
        Assertions.assertTrue(isOpen());
    }

    public boolean isOpen() {
        return (this.getFlags() & FLAG_REMOVED) == 0;
    }

    private static class DependencyInfo {
        private final int[] refCnt;
        private final ObjectArrayList<Runnable>[] callbacks;

        private DependencyInfo(int statuses) {
            this.refCnt = new int[statuses];
            this.callbacks = new ObjectArrayList[statuses];
            Arrays.fill(this.refCnt, -1);
        }
    }
}
