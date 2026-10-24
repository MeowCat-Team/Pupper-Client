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
