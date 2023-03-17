package com.vam.eventbus.annotation.mode;

/**
 * 所有事件订阅方法生成索引接口
 */
public interface SubscriberInfoIndex {

    SubscriberInfo getSubscriberInfo(Class<?> subscriberClass);

}
