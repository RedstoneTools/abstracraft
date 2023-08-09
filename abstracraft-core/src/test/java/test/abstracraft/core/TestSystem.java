package test.abstracraft.core;

import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;
import tools.redstone.abstracraft.core.*;
import tools.redstone.abstracraft.core.analysis.DependencyAnalysisHook;
import tools.redstone.abstracraft.core.analysis.MethodDependency;
import tools.redstone.abstracraft.core.usage.Abstraction;
import tools.redstone.abstracraft.core.util.ReflectUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Simple test system because JUnit is stupid and loads all classes automatically.
 */
public class TestSystem {

    // Signifies a test
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Test {
        String testClass();
        String abstractionImpl();
        String[] hooks() default {};
        boolean fieldDependencies() default true;
    }

    public static void main(String[] args) {

    }

    // System.out.println
    static <T> T println(T o) {
        System.out.println(o);
        return o;
    }

    // Run code with exceptions caught
    static void runSafe(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // Run all tests in the given class
    public static void runTests(Class<?> klass, boolean debug) {
        try {
            // create abstraction manager
            final AbstractionManager abstractionManager = new AbstractionManager()
                    .setClassAuditPredicate(name -> name.startsWith(klass.getName()))
                    .setRequiredMethodPredicate(method -> method.ref.name().startsWith("test"));

            Object instance = klass.newInstance();
            for (Method method : klass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                Test testAnnotation = method.getAnnotation(Test.class);
                if (testAnnotation == null) continue;
                method.setAccessible(true);

                // get test name
                final String testName = method.getName();

                // get test class and shit
                String testClassName = klass.getName() + "$" + testAnnotation.testClass();
                String abstractionImplName = klass.getName() + "$" + testAnnotation.abstractionImpl();

                long t1 = System.currentTimeMillis();

                // add default hooks
                abstractionManager
                        .addAnalysisHook(AbstractionManager.checkDependenciesForInterface(Abstraction.class, testAnnotation.fieldDependencies()))
                        .addAnalysisHook(AbstractionManager.checkForExplicitImplementation(Abstraction.class))
                        .addAnalysisHook(AbstractionManager.checkStaticFieldsNotNull());

                // load hooks
                List<Object> hooks = new ArrayList<>();
                for (String partialHookClassName : testAnnotation.hooks()) {
                    String hookClassName = klass.getName() + "$" + partialHookClassName;
                    Class<?> hookClass = ReflectUtil.getClass(hookClassName);
                    if (hookClass == null || !DependencyAnalysisHook.class.isAssignableFrom(hookClass))
                        throw new IllegalArgumentException("Couldn't find hook class by name " + abstractionImplName);
                    if (debug)
                        System.err.println("DEBUG Test " + testName + ": Found hook " + hookClass);
                    Object hook = hookClass.getConstructor().newInstance();
                    abstractionManager.addAnalysisHook((DependencyAnalysisHook) hook);
                    hooks.add(hook);
                }

                // load, register and create abstraction impl
                Class<?> implClass = ReflectUtil.getClass(abstractionImplName);
                if (implClass == null)
                    throw new IllegalArgumentException("Couldn't find implementation class by name " + abstractionImplName);
                if (debug)
                    System.err.println("DEBUG Test " + testName + ": Found abstraction impl " + implClass);
                abstractionManager.registerImpl(implClass);
                Object implInstance = implClass.getConstructor().newInstance();

                long t2 = System.currentTimeMillis();
                if (debug) System.err.println("DEBUG test " + testName + ": loading hooks and setup took " + (t2 - t1) + "ms");

                // load and create test class
                Class<?> testClass = abstractionManager.findClass(testClassName);
                if (testClass == null)
                    throw new IllegalArgumentException("Couldn't find test class by name " + abstractionImplName);
                if (debug)
                    System.err.println("DEBUG Test " + testName + ": Found test " + implClass);
                Object testInstance = testClass.getConstructor().newInstance();

                long t3 = System.currentTimeMillis();
                if (debug) System.err.println("DEBUG test " + testName + ": loading and transforming test class took " + (t3 - t2) + "ms");

                // order arguments
                Object[] args = new Object[method.getParameterTypes().length];
                for (int i = 0, n = method.getParameterTypes().length; i < n; i++) {
                    Class<?> type = method.getParameterTypes()[i];

                    if (type.isAssignableFrom(implClass)) args[i] = implInstance;
                    else if (type.isAssignableFrom(testClass)) args[i] = testInstance;
                    else if (type.isAssignableFrom(AbstractionManager.class)) args[i] = abstractionManager;
                    else for (var hook : hooks) if (type.isAssignableFrom(hook.getClass())) args[i] = hook;
                }

                try {
                    // invoke method
                    method.invoke(instance, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }

                long t4 = System.currentTimeMillis();
                if (debug) System.err.println("DEBUG test " + testName + ": executing tests took " + (t4 - t3) + "ms");
                if (debug) System.err.println("DEBUG test " + testName + ": took " + (t4 - t1) + "ms total");
            }
        } catch (Throwable t) {
            if (t instanceof AssertionFailedError e)
                throw e;
            throw new RuntimeException("Error while running tests for " + klass, t);
        }
    }

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

    // Throw a new AssertionFailedError
    public static void fail(String msg) {
        throw new AssertionFailedError(msg);
    }

    public static void assertDependenciesEquals(Set<MethodDependency> actual, String... expected) {
        if (expected.length != actual.size())
            fail("Expected " + expected.length + " dependencies, got " + actual.size());

        List<MethodDependency> list = new ArrayList<>(actual);
        for (String str : expected) {
            String[] split = str.split(" ");
            boolean optional = split[0].equals("optional");
            String p = split[1];
            String[] split2 = p.split("\\.");
            String cl = split2[0];
            String name = split2[1];

            MethodDependency found = null;
            for (MethodDependency dependency : list) {
                if (dependency.optional() == optional && dependency.info().name().equals(name) &&
                        dependency.info().ownerClassName().endsWith(cl)) {
                    found = dependency;
                    break;
                }
            }

            if (found == null) {
                fail("Expected dependency " + str + ", actual: null");
            }

            // remove for later checks
            list.remove(found);
        }

        // perform checks
        if (!list.isEmpty()) {
            fail("Expected " + expected.length + " dependencies to match, diff: " + list);
        }
    }

}
