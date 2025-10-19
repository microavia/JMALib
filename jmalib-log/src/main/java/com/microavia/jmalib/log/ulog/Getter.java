package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.*;

public class Getter {
    interface GetterFunction {
        Object get(Object obj);
    }

    final GetterFunction getterFunction;
    final Type type;
    final Subscription subscription;

    public Getter() {
        this.subscription = new Subscription();
        this.getterFunction = (obj -> null);
        this.type = null;
    }

    Getter(Subscription subscription, GetterFunction getterFunction, Type type) {
        this.subscription = subscription;
        this.getterFunction = getterFunction;
        this.type = type;
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
