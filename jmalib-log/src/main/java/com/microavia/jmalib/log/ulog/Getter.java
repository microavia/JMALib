package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.*;

public class Getter {
    interface GetterFunction {
        Object get(Object obj);
    }

    final GetterFunction getterFunction;
    final Type type;
    final Subscription subscription;
    final String path;
    final int multiIdFilter;

    public Getter() {
        this.subscription = new Subscription();
        this.path = "";
        this.getterFunction = (obj -> null);
        this.type = null;
        this.multiIdFilter = -1;
    }

    Getter(Subscription subscription, String path, GetterFunction getterFunction, Type type, int multiIdFilter) {
        this.subscription = subscription;
        this.path = path;
        this.getterFunction = getterFunction;
        this.type = type;
        this.multiIdFilter = multiIdFilter;
    }

    public String getPath() {
        return this.path;
    }

    public boolean isUpdated() {
        return subscription.isUpdated() && (
                (multiIdFilter == -1 && (subscription.getMultiId() & 0x80) != 0) ||
                        (multiIdFilter != -1 && ((subscription.getMultiId() & 0x7F) == multiIdFilter))
        );
    }

    public Object get() {
        return getterFunction.get(subscription.getValue());
    }

    public Type getType() {
        return type;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public boolean isValid() {
        return type != null;
    }
}
