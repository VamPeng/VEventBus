package com.vam.eventbus.annotation.mode;

public interface SubscriberInfo {

    Class<?> getSubscriberClass();

    SubscriberMethod[] getSubscriberMethods();

}
