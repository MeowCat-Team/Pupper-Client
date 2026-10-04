package cn.pupperclient.event;

import java.lang.reflect.Method;

public class MethodEventListener<T extends Event> implements EventBus.EventListener<T> {
    private final Object target;
    private final Method method;
    private final Class<T> eventType;
    private final int priority;

    @SuppressWarnings("unchecked")
    public MethodEventListener(Object target, Method method) {
        this.target = target;
        this.method = method;
        this.eventType = (Class<T>) method.getParameterTypes()[0];

        EventListener annotation = method.getAnnotation(EventListener.class);
        this.priority = annotation != null ? annotation.priority().getValue() : 0;

        if (!method.canAccess(target)) {
            method.setAccessible(true);
        }
    }

    @Override
    public void onEvent(T event) {
        try {
            method.invoke(target, event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke event handler method", e);
        }
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public Class<T> getEventType() {
        return eventType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MethodEventListener<?> that = (MethodEventListener<?>) obj;
        return target.equals(that.target) && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return target.hashCode() + method.hashCode();
    }
}
