package test.abstracraft.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Simple test system because JUnit is stupid and loads all classes automatically.
 */
public class TestSystem {

    /**
     * Stringifies the given object reflectively.
     *
     * @param obj The object.
     * @return The output string.
     */
    public static String toStringReflectively(Object obj) {
        try {
            // check for primitives
            if (obj == null) return "null";
            if (obj instanceof Number) return obj.toString();
            if (obj instanceof Character) return "'" + obj + "'";
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Boolean) return obj.toString();

            // custom toString
            if (obj.getClass().getMethod("toString").getDeclaringClass() != Object.class) {
                return obj.toString();
            }

            // arrays
            if (obj.getClass().isArray()) {
                Object[] arr = (Object[]) obj;
                StringBuilder b = new StringBuilder("[");
                b.append("[");
                boolean first = true;
                for (Object o : arr) {
                    if (!first) b.append(", ");
                    first = false;

                    b.append(toStringReflectively(o));
                }

                return b.append("]").toString();
            }

            // objects
            StringBuilder b = new StringBuilder("");
            b.append(obj.getClass().getSimpleName()).append("{");
            boolean first = true;
            for (Field field : obj.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!first) b.append(", ");
                b.append(field.getName()).append(": ");
                first = false;
                b.append(toStringReflectively(field.get(obj)));
            }

            return b.append("}").toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
