package net.ewant.scheduler;

import java.util.concurrent.TimeUnit;

public interface CancelableScheduler<T> {

    TimeoutDataHolder<T> cancel(String key);

    TimeoutDataHolder<T> schedule(String key, Runnable runnable, long delay, TimeUnit unit);

    void shutdown();

}
