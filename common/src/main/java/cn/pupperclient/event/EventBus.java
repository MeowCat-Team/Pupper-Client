/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.event;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import cn.pupperclient.PupperLogger;

/**
 * Reflection-driven event dispatch.
 *
 * <p>Mods register and unregister from the client thread while network and audio
 * threads post events, so every structure here is safe for concurrent use.
 */
public class EventBus {

    private static final EventBus instance = new EventBus();

    public interface EventListener<T extends Event> {
        void onEvent(T event);
        default int getPriority() {
            return 0;
        }
    }

    /** One listener and the single event type it was registered under. */
    private record Registration(Type eventType, EventListener<?> listener) {}

    private final ConcurrentHashMap<Type, CopyOnWriteArrayList<EventListener<?>>> listenerMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method[]> declaredMethodsCache = new ConcurrentHashMap<>();
    private final Map<Object, List<Registration>> registeredHandlers = new ConcurrentHashMap<>();

    private final Comparator<EventListener<?>> priorityOrder = Comparator
        .comparingInt((EventListener<?> listener) -> listener.getPriority()).reversed();

    /**
     * Fields of the class and its superclasses.
     *
     * <p>Listeners declared on a shared base class have to reach the subclass that is
     * registered, otherwise moving a listener up the hierarchy would silently stop it.
     */
    private Field[] getCachedDeclaredFields(final Class<?> clazz) {
        return declaredFieldsCache.computeIfAbsent(clazz, EventBus::allFields);
    }

    /** Methods of the class and its superclasses, for the same reason as the fields. */
    private Method[] getCachedDeclaredMethods(final Class<?> clazz) {
        return declaredMethodsCache.computeIfAbsent(clazz, EventBus::allMethods);
    }

    private static Field[] allFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields.toArray(new Field[0]);
    }

    private static Method[] allMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            Collections.addAll(methods, current.getDeclaredMethods());
        }
        return methods.toArray(new Method[0]);
    }

    public void register(final Object object) {
        if (object == null || registeredHandlers.containsKey(object)) {
            return;
        }

        List<Registration> handlers = new ArrayList<>();
        collectFieldListeners(object, handlers);
        collectMethodListeners(object, handlers);
        if (handlers.isEmpty()) {
            return;
        }

        // A repeated registration for the same object is a no-op, including a race
        // where two threads register it at once.
        if (registeredHandlers.putIfAbsent(object, List.copyOf(handlers)) != null) {
            return;
        }
        for (Registration handler : handlers) {
            addListener(handler.eventType(), handler.listener());
        }
    }

    @Deprecated
    private void collectFieldListeners(final Object object, List<Registration> handlers) {
        for (final Field field : getCachedDeclaredFields(object.getClass())) {
            if (field.getType() != EventListener.class) {
                continue;
            }
            final EventListener<?> eventListener = getEventHandler(object, field);
            if (eventListener == null) {
                continue;
            }
            final Type eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            handlers.add(new Registration(eventType, eventListener));
        }
    }

    private void collectMethodListeners(final Object object, List<Registration> handlers) {
        for (final Method method : getCachedDeclaredMethods(object.getClass())) {
            if (!method.isAnnotationPresent(cn.pupperclient.event.EventListener.class)) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException(
                    "EventListener method must have exactly one parameter: " + method);
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(parameterType)) {
                throw new IllegalArgumentException(
                    "EventListener method parameter must be a subclass of Event: " + method);
            }

            handlers.add(new Registration(parameterType, new MethodEventListener<>(object, method)));
        }
    }

    private void addListener(Type eventType, EventListener<?> listener) {
        CopyOnWriteArrayList<EventListener<?>> listeners =
            listenerMap.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        // Re-sorting only this bucket keeps higher-priority listeners first after a late registration.
        listeners.sort(priorityOrder);
    }

    public <T extends Event> void post(T event) {
        CopyOnWriteArrayList<EventListener<?>> listeners = listenerMap.get(event.getClass());
        if (listeners == null) {
            return;
        }

        for (EventListener<?> listener : listeners) {
            if (event.isCancelled()) {
                return;
            }
            try {
                @SuppressWarnings("unchecked")
                EventListener<T> castedListener = (EventListener<T>) listener;
                castedListener.onEvent(event);
            } catch (Throwable t) {
                // One broken listener must not stop the rest of the dispatch, and the
                // client keeps running, so record it with the game log instead of stderr.
                PupperLogger.error("EventBus", "Listener failed for " + event.getClass().getSimpleName(), t);
            }
        }
    }

    public boolean hasListeners(Class<? extends Event> eventType) {
        CopyOnWriteArrayList<EventListener<?>> listeners = listenerMap.get(eventType);
        return listeners != null && !listeners.isEmpty();
    }

    public void unregister(final Object object) {
        if (object == null) {
            return;
        }

        List<Registration> handlers = registeredHandlers.remove(object);
        if (handlers == null) {
            return;
        }

        for (Registration handler : handlers) {
            removeListener(handler.eventType(), handler.listener());
        }
    }

    private void removeListener(Type eventType, EventListener<?> listener) {
        listenerMap.computeIfPresent(eventType, (type, listeners) -> {
            listeners.remove(listener);
            return listeners.isEmpty() ? null : listeners;
        });
    }

    private EventListener<?> getEventHandler(final Object o, final Field field) {
        final boolean accessible = field.canAccess(o);
        field.setAccessible(true);
        try {
            return (EventListener<?>) field.get(o);
        } catch (IllegalAccessException e) {
            PupperLogger.error("EventBus", "Failed to get event handler", e);
            return null;
        } finally {
            field.setAccessible(accessible);
        }
    }

    public static EventBus getInstance() {
        return instance;
    }
}
