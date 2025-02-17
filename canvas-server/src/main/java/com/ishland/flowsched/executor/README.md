# The Scheduler

The scheduler takes care of locking multiple resources for tasks in a non-blocking way.  
There is no dedicated thread for the scheduler, instead, all worker threads attempts to schedule tasks for themselves.



