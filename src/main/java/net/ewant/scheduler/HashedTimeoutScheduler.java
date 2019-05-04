package net.ewant.scheduler;

import net.ewant.thread.ExecutorFactory;
import net.ewant.thread.NameGroupThreadFactory;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 高效定时任务调度器
 */
public class HashedTimeoutScheduler<T> implements CancelableScheduler<T> {

    /**
     * 所有超时任务
     */
    private final ConcurrentMap<String, TimeoutDataHolder<T>> scheduledFutures = PlatformDependent.newConcurrentHashMap();

    /**
     *  时间槽调度器
     */
    private HashedWheelTimer wheelTimer;

    /**
     * 超时任务执行器
     */
    private Executor timeoutExecutor;

    public HashedTimeoutScheduler(String schedulerExecutorName){
        this(schedulerExecutorName, 1);
    }

    public HashedTimeoutScheduler(String schedulerExecutorName, int executeThreads){
        this.wheelTimer = new HashedWheelTimer(new NameGroupThreadFactory(schedulerExecutorName));
        this.timeoutExecutor = ExecutorFactory.getExecutor(schedulerExecutorName, executeThreads);
    }

    public TimeoutDataHolder<T> cancel(String key) {
        if(key == null){
            return null;
        }
        TimeoutDataHolder<T> timeoutDataHolder = scheduledFutures.remove(key);
        if (timeoutDataHolder != null) {
            timeoutDataHolder.getTimeout().cancel();
        }
        return timeoutDataHolder;
    }

    public TimeoutDataHolder<T> schedule(final String key, final Runnable runnable, long delay, TimeUnit unit) {
        TimeoutDataHolder<T> timeoutDataHolder = new TimeoutDataHolder<T>(wheelTimer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                try {
                    timeoutExecutor.execute(runnable);
                } finally {
                    if(key != null){
                        scheduledFutures.remove(key);
                    }
                }
            }
        }, delay, unit));

        replaceScheduledFuture(key, timeoutDataHolder);

        return timeoutDataHolder;
    }

    public void shutdown() {
        wheelTimer.stop();
        if(timeoutExecutor instanceof ExecutorService){
            ((ExecutorService) timeoutExecutor).shutdown();
        }
    }

    private void replaceScheduledFuture(final String key, final TimeoutDataHolder<T> newTimeout) {
        final TimeoutDataHolder<T> oldTimeout;
        if (newTimeout.getTimeout().isExpired()) {
            // no need to put already expired timeout to scheduledFutures map.
            // simply remove old timeout
            oldTimeout = scheduledFutures.remove(key);
        } else {
            oldTimeout = scheduledFutures.put(key, newTimeout);
        }
        // if there was old timeout, cancel it
        if (oldTimeout != null) {
            oldTimeout.getTimeout().cancel();
        }
    }

    public long taskCount(){
    	return wheelTimer.pendingTimeouts();
    }
}
