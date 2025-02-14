package com.ishland.flowsched.scheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class ItemTicket<K, V, Ctx> {

    private static final AtomicReferenceFieldUpdater<ItemTicket, Runnable> CALLBACK_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ItemTicket.class, Runnable.class, "callback");

    private final TicketType type;
    private final Object source;
    private final ItemStatus<K, V, Ctx> targetStatus;
    private volatile Runnable callback = null;
//    private int hash = 0;

    public ItemTicket(TicketType type, Object source, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        this.type = Objects.requireNonNull(type);
        this.source = Objects.requireNonNull(source);
        this.targetStatus = Objects.requireNonNull(targetStatus);
        this.callback = callback;
    }

    public Object getSource() {
        return this.source;
    }

    public ItemStatus<K, V, Ctx> getTargetStatus() {
        return this.targetStatus;
    }

    public TicketType getType() {
        return this.type;
    }

    public void consumeCallback() {
        Runnable callback = CALLBACK_UPDATER.getAndSet(this, null);
        if (callback != null) {
            try {
                callback.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemTicket<?, ?, ?> that = (ItemTicket<?, ?, ?>) o;
        return type == that.type && Objects.equals(source, that.source) && Objects.equals(targetStatus, that.targetStatus);
    }

//    public boolean equalsAlternative(ItemTicket<K, V, Ctx> that) {
//        if (this == that) return true;
//        if (that == null) return false;
//        return type == that.type && Objects.equals(source, that.source);
//    }

    @Override
    public int hashCode() {
        // inlined version of Objects.hash(type, source, targetStatus)
        int result = 1;

        result = 31 * result + type.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + targetStatus.hashCode();
        return result;
    }

//    public int hashCodeAlternative() {
//        int hc = hash;
//        if (hc == 0) {
//            // inlined version of Objects.hash(type, source, targetStatus)
//            int result = 1;
//
//            result = 31 * result + type.hashCode();
//            result = 31 * result + source.hashCode();
//            hc = hash = result;
//        }
//        return hc;
//    }

    public static class TicketType {
        public static TicketType DEPENDENCY = new TicketType("flowsched:dependency");
        public static TicketType EXTERNAL = new TicketType("flowsched:external");

        private final String description;

        public TicketType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return this.description;
        }

        // use default equals() and hashCode()

    }
}
