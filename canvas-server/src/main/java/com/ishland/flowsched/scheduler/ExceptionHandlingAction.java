package com.ishland.flowsched.scheduler;

public enum ExceptionHandlingAction {

    /**
     * Ignore the exception and proceed to continue
     */
    PROCEED,
    /**
     * Abort the transaction and clear all dependencies, marking it broken
     */
    MARK_BROKEN,

}
