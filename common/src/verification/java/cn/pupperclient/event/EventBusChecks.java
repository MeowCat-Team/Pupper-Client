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

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks for {@link EventBus} registration and dispatch.
 *
 * <p>The bus scans declared fields and methods, so a listener moved to a shared base
 * class only keeps working if the scan walks the superclass chain. These checks pin
 * that behaviour down.
 */
public final class EventBusChecks {
    private static int checks;

    private EventBusChecks() {}

    public static void main(String[] args) {
        inheritedFieldListenerIsRegistered();
        inheritedMethodListenerIsRegistered();
        subclassOverridesBaseListener();
        unregisterRemovesInheritedListeners();
        priorityOrderIsRespected();
        System.out.println("Event bus checks passed: " + checks + " assertions");
    }

    /** A listener declared on a base class must fire for the registered subclass. */
    private static void inheritedFieldListenerIsRegistered() {
        BaseFieldSubscriber subscriber = new BaseFieldSubscriber();
        EventBus bus = new EventBus();
        bus.register(subscriber);
        require(bus.hasListeners(FieldEvent.class), "inherited field listener was not registered");
        bus.post(new FieldEvent());
        require(subscriber.handled == 1, "inherited field listener did not run");
        bus.unregister(subscriber);
    }

    /** The same for an annotated method on a base class. */
    private static void inheritedMethodListenerIsRegistered() {
        BaseMethodSubscriber subscriber = new BaseMethodSubscriber();
        EventBus bus = new EventBus();
        bus.register(subscriber);
        require(bus.hasListeners(MethodEvent.class), "inherited method listener was not registered");
        bus.post(new MethodEvent());
        require(subscriber.handled == 1, "inherited method listener did not run");
        bus.unregister(subscriber);
    }

    /** A subclass listener must not be shadowed by a base listener of the same name. */
    private static void subclassOverridesBaseListener() {
        OverridingSubscriber subscriber = new OverridingSubscriber();
        EventBus bus = new EventBus();
        bus.register(subscriber);
        bus.post(new FieldEvent());
        require(subscriber.handled == 1, "inherited listener did not run alongside the subclass listener");
        require(subscriber.subHandled == 1, "subclass listener did not run");
        bus.unregister(subscriber);
    }

    /** Unregistering must remove inherited listeners too, or a toggle leaks handlers. */
    private static void unregisterRemovesInheritedListeners() {
        BaseFieldSubscriber subscriber = new BaseFieldSubscriber();
        EventBus bus = new EventBus();
        bus.register(subscriber);
        bus.unregister(subscriber);
        require(!bus.hasListeners(FieldEvent.class), "unregister left the inherited listener behind");
        bus.post(new FieldEvent());
        require(subscriber.handled == 0, "removed inherited listener still ran");
    }

    /** A higher priority listener runs first, including across the inheritance boundary. */
    private static void priorityOrderIsRespected() {
        List<String> order = new ArrayList<>();
        EventBus bus = new EventBus();
        bus.register(new PrioritySubscriber(order));
        bus.post(new PriorityEvent());
        require(order.equals(List.of("high", "low")), "priority order was " + order);
        bus.unregister(order);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static class FieldEvent extends Event {}

    private static class MethodEvent extends Event {}

    private static class PriorityEvent extends Event {}

    private static class BaseFieldSubscriber {
        int handled;
        EventBus.EventListener<FieldEvent> onField = event -> handled++;
    }

    private static class BaseMethodSubscriber {
        int handled;

        @EventListener
        public void onMethod(MethodEvent event) {
            handled++;
        }
    }

    private static class OverridingSubscriber extends BaseFieldSubscriber {
        int subHandled;
        EventBus.EventListener<FieldEvent> onSub = event -> subHandled++;
    }

    private static class PrioritySubscriber {
        private final List<String> order;

        PrioritySubscriber(List<String> order) {
            this.order = order;
        }

        @EventListener(priority = EventListener.Priority.HIGH)
        public void high(PriorityEvent event) {
            order.add("high");
        }

        @EventListener(priority = EventListener.Priority.LOW)
        public void low(PriorityEvent event) {
            order.add("low");
        }
    }
}
