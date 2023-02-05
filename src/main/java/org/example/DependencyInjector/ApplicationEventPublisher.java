package org.example.DependencyInjector;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class ApplicationEventPublisher {
    Map<Class<?>, List<Listener>> listeners = Collections.synchronizedMap(new HashMap<>());

    public void publishEvent(Object event) throws InvocationTargetException, IllegalAccessException {
        Class<?> eventType = event.getClass();
        List<Listener> listenersForEvent =  listeners.get(eventType);
        if (listenersForEvent == null)
            return;

        for (Listener listener : listenersForEvent) {
            listener.invokeEvent(event);
        }
    }

    public void addListener(Listener listener) throws ContainerException {
        Class<?>[] eventTypes = listener.method.getParameterTypes();
        if (eventTypes.length == 0)
            throw new ContainerException("Event listener method " + listener.method.getName() + " does not have event argument");

        Class<?> eventType = eventTypes[0];
        List<Listener> listenersForEvent = listeners.getOrDefault(eventType, new ArrayList<>());
        listenersForEvent.add(listener);
        listeners.put(eventType, listenersForEvent);
    }
}