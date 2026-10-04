package cn.pupperclient.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventListener {
    Priority priority() default Priority.NORMAL;

    enum Priority {
        LOWEST(-100),
        LOW(-50),
        NORMAL(0),
        HIGH(50),
        HIGHEST(1001);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
