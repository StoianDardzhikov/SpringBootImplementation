package org.example.DependencyInjector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Listener {
    Method method;
    Object instance;

    public Listener(Object instance, Method method) {
        this.method = method;
        this.instance = instance;
    }

    void invokeEvent(Object event) throws InvocationTargetException, IllegalAccessException {
        method.invoke(instance, event);
    }
}