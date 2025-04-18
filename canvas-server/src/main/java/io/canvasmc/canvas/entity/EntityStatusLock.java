package io.canvasmc.canvas.entity;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

// basically a stutter-lock, where we try and acquire a lock
// for a certain amount of time, and if we can't, we run a poll
// we run polls for if we need to execute a chunk task to unlock
// this. if we don't process the chunk task that we depend
// on then we run the risk of infinite locking...
public class EntityStatusLock extends ReentrantLock {

    public final Entity entity;
    private Status status;

    public EntityStatusLock(Entity entity) {
        this.entity = entity;
    }

    public void acquire() {
        if (ServerChunkCache.MainThreadExecutor.entityOverride.contains(Thread.currentThread())) return; // pass
        int tries = 0;
        while (!this.tryLock()) {
            // attempt 40 times before we try and exit the lock. this is equivalent to at least 40 milliseconds
            // if we don't do this, then during intra-dimensional teleports we run the risk of infinite locking.
            if (tries++ >= 40) {
                release();
                break;
            }
            LockSupport.parkNanos("wait for acquire", 1_000_000 /*1ms*/);
            this.entity.level().level().getChunkSource().mainThreadProcessor.pollTask(false);
        }
    }

    public void release() {
        if (ServerChunkCache.MainThreadExecutor.entityOverride.contains(Thread.currentThread())) return; // pass
        try {
            this.unlock();
        } catch (IllegalMonitorStateException ignored) {}
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public Thread getOwner() {
        return super.getOwner();
    }

    public static enum Status {
        STATUS_CHANGES,
        POS_CHANGE,
        REMOVED,
        TRACKING,
        SCHEDULER;
    }
}
