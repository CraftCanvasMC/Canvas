package com.ishland.flowsched.executor;

import ca.spottedleaf.concurrentutil.util.Priority;

public interface Task {

    void run(Runnable releaseLocks);

    void propagateException(Throwable t);

    LockToken[] lockTokens();

    Priority priority();

}
