package titech.project.nama.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread tools
 *
 */
public final class ThreadHelper {
    private final Handler mMainHandler;
    private final ThreadPoolExecutor mExecutorService;
    private Handler mWorkHandler;

    private ThreadHelper() {
        mMainHandler = new Handler(Looper.getMainLooper());
        // copy from AsyncTask THREAD_POOL_EXECUTOR
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
            }
        };
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, Math.min(cpuCount - 1, 4));
        int maxPoolSize = cpuCount * 2 + 1;
        BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>(128);
        mExecutorService = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 30, TimeUnit.SECONDS, blockingQueue, threadFactory);
        mExecutorService.allowCoreThreadTimeOut(true);
    }

    public static ThreadHelper getInstance() {
        return ThreadHelperHolder.INSTANCE;
    }

    private synchronized void ensureSubHandler() {
        if (mWorkHandler == null) {
            HandlerThread handlerThread = new HandlerThread("WorkHandler");
            handlerThread.start();
            mWorkHandler = new Handler(handlerThread.getLooper());
        }
    }

    /**
     *  async function with no return
     *
     * @param callable
     * @param callback
     */
    public <T> void enqueueOnUiThread(final Callable<T> callable, final Callback<T> callback) {
        if (callable != null) {
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final CountDownLatch countDownLatch = new CountDownLatch(1);
                        if (callback != null) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onStart();
                                    countDownLatch.countDown();
                                }
                            });
                        }
                        countDownLatch.await(1000, TimeUnit.MILLISECONDS);
                        final T t = callable.call();
                        if (callback != null) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onSuccess(t);
                                }
                            });
                        }
                    } catch (final Throwable throwable) {
                        if (callback != null) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFailure(throwable);
                                }
                            });
                        }
                    } finally {
                        if (callback != null) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFinish();
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    /**
     *  async function with return
     *
     * @param callable
     * @param callback
     */
    public <T> void enqueue(final Callable<T> callable, final Callback<T> callback) {
        if (callable != null) {
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (callback != null) {
                            callback.onStart();
                        }
                        final T t = callable.call();
                        if (callback != null) {
                            callback.onSuccess(t);
                        }
                    } catch (final Throwable throwable) {
                        if (callback != null) {
                            callback.onFailure(throwable);
                        }
                    } finally {
                        if (callback != null) {
                            callback.onFinish();
                        }
                    }
                }
            });
        }
    }


    /**
     * @param r
     */
    public void execute(Runnable r) {
        if (r != null) {
            mExecutorService.execute(r);
        }
    }

    /**
     * @param task
     * @param <T>
     * @return
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task != null) {
            return mExecutorService.submit(task);
        } else {
            return null;
        }
    }

    /**
     * @param r
     * @param delayMillis
     * @return
     */
    public boolean postDelayed(Runnable r, long delayMillis) {
        ensureSubHandler();
        return mWorkHandler.postDelayed(r, delayMillis);
    }

    /**
     * @param r
     * @param uptimeMillis
     * @return
     */
    public boolean postAtTime(Runnable r, long uptimeMillis) {
        ensureSubHandler();
        return mWorkHandler.postAtTime(r, uptimeMillis);
    }

    /**
     * @param r
     */
    public void runOnUiThread(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        } else {
            mMainHandler.post(r);
        }
    }

    /**
     * @param r
     * @param delay
     * @return
     */
    public boolean runOnUiPostDelayed(Runnable r, long delay) {
        if (r != null) {
            return mMainHandler.postDelayed(r, delay);
        } else {
            return false;
        }
    }

    /**
     * @param r
     * @param uptimeMillis
     * @return
     */
    public boolean runOnUiPostAtTime(Runnable r, long uptimeMillis) {
        if (r != null) {
            return mMainHandler.postAtTime(r, uptimeMillis);
        } else {
            return false;
        }
    }

    /**
     * @param r
     */
    public void removeUiCallbacks(Runnable r) {
        if (r != null) {
            mMainHandler.removeCallbacks(r);
        }
    }

    /**
     * @param r
     */
    public void removeWorkCallbacks(Runnable r) {
        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacks(r);
        }
    }


    public void removeUiAllTasks() {
        mMainHandler.removeCallbacksAndMessages(null);
    }


    public void shutdown() {
        if (!mExecutorService.isShutdown()) {
            mExecutorService.shutdown();
        }
        if (mWorkHandler != null) {
            mWorkHandler.getLooper().quitSafely();
        }
    }


    /**
     * onStart-->onSuccess-->onFinish
     * onStart-->onFailure-->onFinish
     */
    public static abstract class Callback<T> {
        protected void onStart() {
        }

        protected void onFinish() {
        }

        protected void onSuccess(T result) {
        }

        protected void onFailure(Throwable throwable) {
        }
    }

    private static class ThreadHelperHolder {
        private static final ThreadHelper INSTANCE = new ThreadHelper();
    }

}
