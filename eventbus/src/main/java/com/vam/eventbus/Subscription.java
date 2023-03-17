package com.vam.eventbus;

import androidx.annotation.Nullable;

import com.vam.eventbus.annotation.mode.SubscriberMethod;

public class Subscription {

    final Object subscriber;
    final SubscriberMethod subscriberMethod;

    public Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Subscription){
            Subscription otherSubscription = (Subscription) obj;
            // 删除官方 subscriber == otherSubscription.subscriber判断条件
            // 原因：粘性事件bug，多次调用和移除时会重现，参考Subscription.java  L37
            // 也有hashCode判断，但是粘性和非粘性几轮debug下来发现，根本没有走 hashCode 方法。
            return subscriberMethod.equals(otherSubscription.subscriberMethod);
        }else {
            return false;
        }
    }



}
