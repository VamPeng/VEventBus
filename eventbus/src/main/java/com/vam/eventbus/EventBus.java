package com.vam.eventbus;

import android.media.metrics.Event;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vam.eventbus.annotation.mode.SubscriberInfo;
import com.vam.eventbus.annotation.mode.SubscriberInfoIndex;
import com.vam.eventbus.annotation.mode.SubscriberMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ArrayList 的底层是数组，添加移除会比较麻烦
 * <p>
 * CopyOnWriterArrayList 实现了 List 接口
 * Vector是增删改查方法都加了 synchronized 保证同步， 但是每个方法执行都要获得锁，性能会下降
 * <p>
 * 而 CopyOnWriteArrayList 只是增删改上加锁，但是读不加，再读方面性能好于Vector,属于读多写少。
 */
public class EventBus {

    private static volatile EventBus defaultInstance;

    private SubscriberInfoIndex subscriberInfoIndexes;

    private Map<Object, List<Class<?>>> typesBySubscriber;

    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;

    private final Map<Class<?>, Object> stickyEvents;

    private Handler handler;

    private ExecutorService executorService;

    public EventBus() {

        typesBySubscriber = new HashMap<>();

        subscriptionsByEventType = new HashMap<>();

        stickyEvents = new HashMap<>();

        handler = new Handler(Looper.getMainLooper());

        executorService = Executors.newCachedThreadPool();

    }

    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    public void addIndex(SubscriberInfoIndex infoIndex) {
        subscriberInfoIndexes = infoIndex;
    }

    public void register(Object subscriber) {

        Class<?> subscriberClass = subscriber.getClass();

        List<SubscriberMethod> subscriberMethods = findSubscriberMethods(subscriberClass);

        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }

    }

    private List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {

        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);

        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        subscriberMethods = findUsingInfo(subscriberClass);
        if (subscriberMethods != null) {
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
        }

        return subscriberMethods;
    }

    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.getEventType();

        Subscription subscription = new Subscription(subscriber, subscriberMethod);

        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);

        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(subscription)) {
                Log.e("vam >>> ", subscriber.getClass() + "重复注册粘性事件!");
                sticky(subscriberMethod, eventType, subscription);
                return;
            }
        }

        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {

            if (i == size || subscriberMethod.getPriority() > subscriptions.get(i).subscriberMethod.getPriority()) {
                if (!subscriptions.contains(subscription)) subscriptions.add(i, subscription);
                break;
            }

        }

        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);

        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);
        sticky(subscriberMethod, eventType, subscription);

    }

    private void sticky(SubscriberMethod subscriberMethod, Class<?> eventType, Subscription subscription) {

        if (subscriberMethod.isSticky()) {
            Object stickyEvent = stickyEvents.get(eventType);
            if (stickyEvent != null) postToSubscription(subscription, stickyEvent);
        }

    }

    private void postToSubscription(Subscription subscription, Object event) {

        switch (subscription.subscriberMethod.getThreadMode()) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    invokeSubscriber(subscription, event);
                } else {
                    handler.post(() -> invokeSubscriber(subscription, event));
                }
                break;
            case ASYNC:
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    executorService.execute(() -> invokeSubscriber(subscription, event));
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            default:
                throw new IllegalStateException("未知线程模式!" + subscription.subscriberMethod.getThreadMode());

        }

    }

    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        if (subscriberInfoIndexes == null) {
            throw new RuntimeException("未添加索引方法：addIndex()");
        }

        SubscriberInfo info = subscriberInfoIndexes.getSubscriberInfo(subscriberClass);

        if (info != null) return Arrays.asList(info.getSubscriberMethods());

        return null;

    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    public synchronized void unregister(Object subscriber) {

        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);

        if (subscribedTypes != null) {
            subscribedTypes.clear();
            typesBySubscriber.remove(subscriber);
        }

    }

    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.getMethod().invoke(subscription.subscriber, event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clearCache() {
        METHOD_CACHE.clear();
    }

}
