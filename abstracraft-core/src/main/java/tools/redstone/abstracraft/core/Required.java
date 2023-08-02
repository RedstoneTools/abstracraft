package tools.redstone.abstracraft.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Required {
    /**
     * The classes required by this behavior.
     */
    Class<?>[] value();
}