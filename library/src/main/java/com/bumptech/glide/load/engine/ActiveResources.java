package com.bumptech.glide.load.engine;

import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.EngineResource.ResourceListener;
import com.bumptech.glide.util.Executors;
import com.bumptech.glide.util.Preconditions;
import com.bumptech.glide.util.Synthetic;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * 获取资源
 * <p>
 * 第一级缓存,表示当前正在活动中的资源
 * <p>
 * 当资源加载成功，或者通过缓存获得资源后都会将其放入 ActiveResources 中，资源被释放时移除出 ActiveResources
 * <p>
 * 由于其中的生命周期较短，所以没有大小限制
 */

final class ActiveResources {
  private final boolean isActiveResourceRetentionAllowed;
  private final Executor monitorClearedResourcesExecutor;
  @VisibleForTesting final Map<Key, ResourceWeakReference> activeEngineResources = new HashMap<>();
  //用来跟踪弱引用（或者软引用、虚引用）是否被 gc 的
  //MessageQueue#addIdleHandler 添加一个 MessageQueue.IdleHandler 对象，Handler 会在线程空闲时调用这个方法
  //引用队列 1.使用队列进行数据监控 2.队列监控的反向操作
  //反向操作，即意味着一个数据变化了，可以通过weakReference对象反向拿相关的数据，从而进行业务的处理。
  //比如，我们可以通过继承weakReference对象，加入自定义的字段值，额外处理。一个类似weakHashMap如下，这时，我们不再将key值作为弱引用处理，而是封装在weakReference对象中，以实现额外的处理
  private final ReferenceQueue<EngineResource<?>> resourceReferenceQueue = new ReferenceQueue<>();

  private ResourceListener listener;

  private volatile boolean isShutdown;
  @Nullable private volatile DequeuedResourceCallback cb;

  ActiveResources(boolean isActiveResourceRetentionAllowed) {
    this(
        isActiveResourceRetentionAllowed,
        java.util.concurrent.Executors.newSingleThreadExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(@NonNull final Runnable r) {
                return new Thread(
                    new Runnable() {
                      @Override
                      public void run() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        r.run();
                      }
                    },
                    "glide-active-resources");
              }
            }));
  }

  @VisibleForTesting
  ActiveResources(
      boolean isActiveResourceRetentionAllowed, Executor monitorClearedResourcesExecutor) {
    this.isActiveResourceRetentionAllowed = isActiveResourceRetentionAllowed;
    this.monitorClearedResourcesExecutor = monitorClearedResourcesExecutor;

    monitorClearedResourcesExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            cleanReferenceQueue();
          }
        });
  }

  void setListener(ResourceListener listener) {
    synchronized (listener) {
      synchronized (this) {
        this.listener = listener;
      }
    }
  }

  synchronized void activate(Key key, EngineResource<?> resource) {
    ResourceWeakReference toPut =
        new ResourceWeakReference(
            key, resource, resourceReferenceQueue, isActiveResourceRetentionAllowed);

    ResourceWeakReference removed = activeEngineResources.put(key, toPut);
    if (removed != null) {
      removed.reset();
    }
  }

  synchronized void deactivate(Key key) {
    ResourceWeakReference removed = activeEngineResources.remove(key);
    if (removed != null) {
      removed.reset();
    }
  }

  @Nullable
  synchronized EngineResource<?> get(Key key) {
    ResourceWeakReference activeRef = activeEngineResources.get(key);
    if (activeRef == null) {
      return null;
    }

    EngineResource<?> active = activeRef.get();
    if (active == null) {
      cleanupActiveReference(activeRef);
    }
    return active;
  }

  @SuppressWarnings({"WeakerAccess", "SynchronizeOnNonFinalField"})
  @Synthetic
  void cleanupActiveReference(@NonNull ResourceWeakReference ref) {
    synchronized (this) {
      activeEngineResources.remove(ref.key);

      if (!ref.isCacheable || ref.resource == null) {
        return;
      }
    }

    EngineResource<?> newResource =
        new EngineResource<>(
            ref.resource, /*isMemoryCacheable=*/ true, /*isRecyclable=*/ false, ref.key, listener);
    listener.onResourceReleased(ref.key, newResource);
  }

  @SuppressWarnings("WeakerAccess")
  @Synthetic
  void cleanReferenceQueue() {
    while (!isShutdown) {
      try {
        //其实就是拿到反向引用的key值(这里的value已经不存在了),因为kv映射已没有意义，将其从map中移除掉
        //在cache中，key不重要并且通常都很少，value才是需要对待的。这里通过监控value变化，反向修改map，以达到控制kv的目的，避免出现无用的kv映射。
        ResourceWeakReference ref = (ResourceWeakReference) resourceReferenceQueue.remove();
        cleanupActiveReference(ref);

        // This section for testing only.
        DequeuedResourceCallback current = cb;
        if (current != null) {
          current.onResourceDequeued();
        }
        // End for testing only.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @VisibleForTesting
  void setDequeuedResourceCallback(DequeuedResourceCallback cb) {
    this.cb = cb;
  }

  @VisibleForTesting
  interface DequeuedResourceCallback {
    void onResourceDequeued();
  }

  @VisibleForTesting
  void shutdown() {
    isShutdown = true;
    if (monitorClearedResourcesExecutor instanceof ExecutorService) {
      ExecutorService service = (ExecutorService) monitorClearedResourcesExecutor;
      Executors.shutdownAndAwaitTermination(service);
    }
  }

  //描述一种强key关系的处理，当value值被回收之后，我们可以通过反向引用将key从map中移除的做法
  //即通过在weakReference中加入其所引用的key值，以获取key信息，再反向移除map信息
  @VisibleForTesting
  static final class ResourceWeakReference extends WeakReference<EngineResource<?>> {
    @SuppressWarnings("WeakerAccess")
    @Synthetic
    final Key key;

    @SuppressWarnings("WeakerAccess")
    @Synthetic
    final boolean isCacheable;

    @Nullable
    @SuppressWarnings("WeakerAccess")
    @Synthetic
    Resource<?> resource;

    @Synthetic
    @SuppressWarnings("WeakerAccess")
    ResourceWeakReference(
        @NonNull Key key,
        @NonNull EngineResource<?> referent,
        @NonNull ReferenceQueue<? super EngineResource<?>> queue,
        boolean isActiveResourceRetentionAllowed) {
      super(referent, queue);
      this.key = Preconditions.checkNotNull(key);
      this.resource =
          referent.isMemoryCacheable() && isActiveResourceRetentionAllowed
              ? Preconditions.checkNotNull(referent.getResource())
              : null;
      isCacheable = referent.isMemoryCacheable();
    }

    void reset() {
      resource = null;
      clear();
    }
  }
}
