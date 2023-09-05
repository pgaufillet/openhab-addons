/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.automation.jsscripting.internal.threading;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.model.script.ScriptServiceUtil;
import org.openhab.core.scheduler.ScheduledCompletableFuture;
import org.openhab.core.scheduler.Scheduler;
import org.openhab.core.scheduler.SchedulerTemporalAdjuster;

/**
 * A polyfill implementation of NodeJS timer functionality (<code>setTimeout()</code>, <code>setInterval()</code> and
 * the cancel methods) which controls multithreaded execution access to the single-threaded GraalJS contexts.
 *
 * @author Florian Hotze - Initial contribution
 * @author Florian Hotze - Reimplementation to conform standard JS setTimeout and setInterval
 */
public class ThreadsafeTimers {
    private final Object lock;
    private final Scheduler scheduler;
    // Mapping of positive, non-zero integer values (used as timeoutID or intervalID) and the Scheduler
    private final Map<Long, ScheduledCompletableFuture<Object>> idSchedulerMapping = new ConcurrentHashMap<>();
    private AtomicLong lastId = new AtomicLong();
    private String identifier = "noIdentifier";

    public ThreadsafeTimers(Object lock) {
        this.lock = lock;
        this.scheduler = ScriptServiceUtil.getScheduler();
    }

    /**
     * Set the identifier base string used for naming scheduled jobs.
     *
     * @param identifier identifier to use
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Schedules a callback to run at a given time.
     *
     * @param id timerId to append to the identifier base for naming the scheduled job
     * @param zdt time to schedule the job
     * @param callback function to run at the given time
     * @return a {@link ScheduledCompletableFuture}
     */
    private ScheduledCompletableFuture<Object> createFuture(long id, ZonedDateTime zdt, Runnable callback) {
        return scheduler.schedule(() -> {
            synchronized (lock) {
                callback.run();
            }
        }, identifier + ".timeout." + id, zdt.toInstant());
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/setTimeout"><code>setTimeout()</code></a> polyfill.
     * Sets a timer which executes a given function once the timer expires.
     *
     * @param callback function to run after the given delay
     * @param delay time in milliseconds that the timer should wait before the callback is executed
     * @return Positive integer value which identifies the timer created; this value can be passed to
     *         <code>clearTimeout()</code> to cancel the timeout.
     */
    public long setTimeout(Runnable callback, Long delay) {
        return setTimeout(callback, delay, new Object());
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/setTimeout"><code>setTimeout()</code></a> polyfill.
     * Sets a timer which executes a given function once the timer expires.
     *
     * @param callback function to run after the given delay
     * @param delay time in milliseconds that the timer should wait before the callback is executed
     * @param args
     * @return Positive integer value which identifies the timer created; this value can be passed to
     *         <code>clearTimeout()</code> to cancel the timeout.
     */
    public long setTimeout(Runnable callback, Long delay, Object... args) {
        long id = lastId.incrementAndGet();
        ScheduledCompletableFuture<Object> future = createFuture(id, ZonedDateTime.now().plusNanos(delay * 1000000),
                callback);
        idSchedulerMapping.put(id, future);
        return id;
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/clearTimeout"><code>clearTimeout()</code></a> polyfill.
     * Cancels a timeout previously created by <code>setTimeout()</code>.
     *
     * @param timeoutId The identifier of the timeout you want to cancel. This ID was returned by the corresponding call
     *            to setTimeout().
     */
    public void clearTimeout(long timeoutId) {
        ScheduledCompletableFuture<Object> scheduled = idSchedulerMapping.remove(timeoutId);
        if (scheduled != null) {
            scheduled.cancel(true);
        }
    }

    /**
     * Schedules a callback to run in a loop with a given delay between the executions.
     *
     * @param id timerId to append to the identifier base for naming the scheduled job
     * @param delay time in milliseconds that the timer should delay in between executions of the callback
     * @param callback function to run
     */
    private void createLoopingFuture(long id, Long delay, Runnable callback) {
        ScheduledCompletableFuture<Object> future = scheduler.schedule(() -> {
            synchronized (lock) {
                callback.run();
            }
        }, identifier + ".interval." + id, new LoopingAdjuster(Duration.ofMillis(delay)));
        idSchedulerMapping.put(id, future);
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/setInterval"><code>setInterval()</code></a> polyfill.
     * Repeatedly calls a function with a fixed time delay between each call.
     *
     * @param callback function to run
     * @param delay time in milliseconds that the timer should delay in between executions of the callback
     * @return Numeric, non-zero value which identifies the timer created; this value can be passed to
     *         <code>clearInterval()</code> to cancel the interval.
     */
    public long setInterval(Runnable callback, Long delay) {
        return setInterval(callback, delay, new Object());
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/setInterval"><code>setInterval()</code></a> polyfill.
     * Repeatedly calls a function with a fixed time delay between each call.
     *
     * @param callback function to run
     * @param delay time in milliseconds that the timer should delay in between executions of the callback
     * @param args
     * @return Numeric, non-zero value which identifies the timer created; this value can be passed to
     *         <code>clearInterval()</code> to cancel the interval.
     */
    public long setInterval(Runnable callback, Long delay, Object... args) {
        long id = lastId.incrementAndGet();
        createLoopingFuture(id, delay, callback);
        return id;
    }

    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/API/clearInterval"><code>clearInterval()</code></a>
     * polyfill.
     * Cancels a timed, repeating action which was previously established by a call to <code>setInterval()</code>.
     *
     * @param intervalID The identifier of the repeated action you want to cancel. This ID was returned by the
     *            corresponding call to <code>setInterval()</code>.
     */
    public void clearInterval(long intervalID) {
        clearTimeout(intervalID);
    }

    /**
     * Cancels all timed actions (i.e. timeouts and intervals) that were created with this instance of
     * {@link ThreadsafeTimers}.
     * Should be called in a de-initialization/unload hook of the script engine to avoid having scheduled jobs that are
     * running endless.
     */
    public void clearAll() {
        idSchedulerMapping.forEach((id, future) -> future.cancel(true));
        idSchedulerMapping.clear();
    }

    /**
     * This is a temporal adjuster that takes a single delay.
     * This adjuster makes the scheduler run as a fixed rate scheduler from the first time adjustInto was called.
     *
     * @author Florian Hotze - Initial contribution
     */
    private static class LoopingAdjuster implements SchedulerTemporalAdjuster {

        private Duration delay;
        private @Nullable Temporal timeDone;

        LoopingAdjuster(Duration delay) {
            this.delay = delay;
        }

        @Override
        public boolean isDone(Temporal temporal) {
            // Always return false so that a new job will be scheduled
            return false;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal localTimeDone = timeDone;
            Temporal nextTime;
            if (localTimeDone != null) {
                nextTime = localTimeDone.plus(delay);
            } else {
                nextTime = temporal.plus(delay);
            }
            timeDone = nextTime;
            return nextTime;
        }
    }
}
