package cn.pupperclient.event;

import org.apache.logging.log4j.util.InternalApi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EventBus {

    private static EventBus instance = new EventBus();

    public interface EventListener<T extends Event> {
        void onEvent(T event);
        default int getPriority() {
            return 0;
        }
    }

    private final ConcurrentHashMap<Type, CopyOnWriteArrayList<EventListener<?>>> listenerMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field[]> declaredFieldsCache = new HashMap<>();
    private final Map<Class<?>, Method[]> declaredMethodsCache = new HashMap<>();
    private final Map<Object, List<EventListener<?>>> registeredHandlers = new HashMap<>();

    private final Comparator<EventListener<?>> priorityOrder = Comparator
        .comparingInt((EventListener<?> listener) -> listener.getPriority()).reversed();
    private final BiConsumer<List<EventListener<?>>, Comparator<EventListener<?>>> sortCallback = List::sort;
    private final Consumer<Throwable> errorHandler = Throwable::printStackTrace;

    private Field[] getCachedDeclaredFields(final Class<?> clazz) {
        return declaredFieldsCache.computeIfAbsent(clazz, Class::getDeclaredFields);
    }

    private Method[] getCachedDeclaredMethods(final Class<?> clazz) {
        return declaredMethodsCache.computeIfAbsent(clazz, Class::getDeclaredMethods);
    }

    public void register(final Object object) {
        if (registeredHandlers.containsKey(object)) {
            return;
        }

        List<EventListener<?>> handlers = new CopyOnWriteArrayList<>();

        registerFieldListeners(object, handlers);

        registerMethodListeners(object, handlers);

        if (!handlers.isEmpty()) {
            registeredHandlers.put(object, handlers);
        }
    }

    @Deprecated
    private void registerFieldListeners(final Object object, List<EventListener<?>> handlers) {
        for (final Field field : getCachedDeclaredFields(object.getClass())) {
            if (field.getType() == EventListener.class) {
                final EventListener<?> eventListener = getEventHandler(object, field);
                if (eventListener != null) {
                    final Type eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    registerListener(eventType, eventListener);
                    handlers.add(eventListener);
                }
            }
        }
    }

    private void registerMethodListeners(final Object object, List<EventListener<?>> handlers) {
        for (final Method method : getCachedDeclaredMethods(object.getClass())) {
            if (method.isAnnotationPresent(cn.pupperclient.event.EventListener.class)) {
                if (method.getParameterCount() != 1) {
                    throw new IllegalArgumentException(
                        "EventListener method must have exactly one parameter: " + method);
                }

                Class<?> parameterType = method.getParameterTypes()[0];
                if (!Event.class.isAssignableFrom(parameterType)) {
                    throw new IllegalArgumentException(
                        "EventListener method parameter must be a subclass of Event: " + method);
                }

                @SuppressWarnings("unchecked")
                Class<? extends Event> eventType = (Class<? extends Event>) parameterType;

                MethodEventListener<?> handler = new MethodEventListener<>(object, method);
                registerListener(eventType, handler);
                handlers.add(handler);
            }
        }
    }

    private void registerListener(Type eventType, EventListener<?> listener) {
        listenerMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        sortCallback.accept(listenerMap.get(eventType), priorityOrder);
    }

    public <T extends Event> void post(T event) {
        Type eventType = event.getClass();
        CopyOnWriteArrayList<EventListener<?>> listeners = listenerMap.get(eventType);

        if (listeners != null) {
            for (EventListener<?> listener : listeners) {
                if (event.isCancelled()) {
                    break;
                }
                try {
                    @SuppressWarnings("unchecked")
                    EventListener<T> castedListener = (EventListener<T>) listener;
                    castedListener.onEvent(event);
                } catch (Throwable t) {
                    errorHandler.accept(t);
                }
            }
        }
    }

    public boolean hasListeners(Class<? extends Event> eventType) {
        CopyOnWriteArrayList<EventListener<?>> listeners = listenerMap.get(eventType);
        return listeners != null && !listeners.isEmpty();
    }

    public void unregister(final Object object) {
        List<EventListener<?>> handlers = registeredHandlers.remove(object);
        if (handlers != null) {
            for (EventListener<?> handler : handlers) {
                // 从所有事件类型中移除这个处理器
                for (CopyOnWriteArrayList<EventListener<?>> listeners : listenerMap.values()) {
                    listeners.remove(handler);
                }
                // 清理空的事件类型
                listenerMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            }
        }

        // 同时调用旧的取消注册逻辑以确保完全清理
        unregisterOldStyle(object);
    }

    /**
     * 旧的取消注册逻辑 (保持兼容)
     */
    private void unregisterOldStyle(final Object object) {
        modifyEventListenerState(object, (type, listener) -> {
            CopyOnWriteArrayList<EventListener<?>> listeners = listenerMap.get(type);
            if (listeners != null) {
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    listenerMap.remove(type);
                }
            }
        });
    }

    /**
     * 旧的修改事件监听器状态方法 (保持兼容)
     */
    private void modifyEventListenerState(final Object o,
                                          final BiConsumer<Type, EventListener<?>> eventHandlerBiConsumer) {
        final Class<?> type = o.getClass();
        for (final Field field : getCachedDeclaredFields(type)) {
            // 修改这里：使用 EventListener 而不是 EventListener
            if (field.getType() == EventListener.class) {
                final EventListener<?> eventListener = getEventHandler(o, field);
                if (eventListener != null) {
                    final Type eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    eventHandlerBiConsumer.accept(eventType, eventListener);
                }
            }
        }
    }

    private EventListener<?> getEventHandler(final Object o, final Field field) {
        final boolean accessible = field.canAccess(o);
        field.setAccessible(true);
        EventListener<?> fieldSubscription = null;
        try {
            fieldSubscription = (EventListener<?>) field.get(o);
        } catch (IllegalAccessException e) {
            cn.pupperclient.PupperLogger.error("EventBus", "Failed to get event handler", e);
        } finally {
            field.setAccessible(accessible);
        }
        return fieldSubscription;
    }

    public static EventBus getInstance() {
        return instance;
    }
}
