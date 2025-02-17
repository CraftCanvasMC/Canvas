package com.ishland.flowsched.scheduler;

import com.ishland.flowsched.util.Assertions;
import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class TicketSet<K, V, Ctx> {

    private static final AtomicIntegerFieldUpdater<TicketSet> dirtyTargetStatusUpdater = AtomicIntegerFieldUpdater.newUpdater(TicketSet.class, "dirtyTargetStatus");

    private final ItemStatus<K, V, Ctx> initialStatus;
    private final Set<ItemTicket<K, V, Ctx>>[] status2Tickets;
    private volatile int dirtyTargetStatus = 0;
    private volatile int targetStatus = 0;

    public TicketSet(ItemStatus<K, V, Ctx> initialStatus, ObjectFactory objectFactory) {
        this.initialStatus = initialStatus;
        this.targetStatus = initialStatus.ordinal();
        ItemStatus<K, V, Ctx>[] allStatuses = initialStatus.getAllStatuses();
        this.status2Tickets = new Set[allStatuses.length];
        for (int i = 0; i < allStatuses.length; i++) {
            this.status2Tickets[i] = objectFactory.createConcurrentSet();
        }
        VarHandle.fullFence();
    }

    public boolean add(ItemTicket<K, V, Ctx> ticket) {
        ItemStatus<K, V, Ctx> targetStatus = ticket.getTargetStatus();
        final boolean added = this.status2Tickets[targetStatus.ordinal()].add(ticket);
        if (!added) return false;

        dirtyTargetStatusUpdater.set(this, 1);

        return true;
    }

    public boolean remove(ItemTicket<K, V, Ctx> ticket) {
        ItemStatus<K, V, Ctx> targetStatus = ticket.getTargetStatus();
        final boolean removed = this.status2Tickets[targetStatus.ordinal()].remove(ticket);
        if (!removed) return false;

        dirtyTargetStatusUpdater.set(this, 1);

        return true;
    }

    private void updateTargetStatus() {
        synchronized (this) {
            if (dirtyTargetStatusUpdater.compareAndSet(this, 1, 0)) {
                this.targetStatus = this.computeTargetStatusSlow();
            }
        }
    }

    public ItemStatus<K, V, Ctx> getTargetStatus() {
        updateTargetStatus();
        return this.initialStatus.getAllStatuses()[this.targetStatus];
    }

    public Set<ItemTicket<K, V, Ctx>> getTicketsForStatus(ItemStatus<K, V, Ctx> status) {
        return this.status2Tickets[status.ordinal()];
    }

    void clear() {
        for (Set<ItemTicket<K, V, Ctx>> tickets : status2Tickets) {
            tickets.clear();
        }
        dirtyTargetStatusUpdater.set(this, 1);

        VarHandle.fullFence();
    }

    void assertEmpty() {
        for (Set<ItemTicket<K, V, Ctx>> tickets : status2Tickets) {
            Assertions.assertTrue(tickets.isEmpty());
        }
    }

    private int computeTargetStatusSlow() {
        for (int i = this.status2Tickets.length - 1; i > 0; i--) {
            if (!this.status2Tickets[i].isEmpty()) {
                return i;
            }
        }
        return 0;
    }

}
