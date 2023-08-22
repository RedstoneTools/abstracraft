package test.abstracraft.core;

import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;
import tools.redstone.abstracraft.Abstracraft;
import tools.redstone.abstracraft.AbstractionManager;
import tools.redstone.abstracraft.AbstractionProvider;
import tools.redstone.abstracraft.adapter.AdapterAnalysisHook;
import tools.redstone.abstracraft.adapter.AdapterRegistry;
import tools.redstone.abstracraft.analysis.Dependency;
import tools.redstone.abstracraft.analysis.ClassAnalysisHook;
import tools.redstone.abstracraft.analysis.ReferenceDependency;
import tools.redstone.abstracraft.analysis.RequireOneDependency;
import tools.redstone.abstracraft.usage.Abstraction;
import tools.redstone.abstracraft.util.PackageWalker;
import tools.redstone.abstracraft.util.ReflectUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Simple test system because JUnit is stupid and loads all classes automatically.
 */
public class TestSystem {

    static final Field FIELD_Abstracraft_abstractionProvider;

    static {
        try {
            FIELD_Abstracraft_abstractionProvider = Abstracraft.class.getDeclaredField("provider");
            FIELD_Abstracraft_abstractionProvider.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static AbstractionProvider getGlobalAbstractionProvider() {
        try {
            return (AbstractionProvider) FIELD_Abstracraft_abstractionProvider.get(Abstracraft.getInstance());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Test interface
    public record TestInterface(Class<?> rootClass, String testMethod, AbstractionProvider abstractionManager) {
        @SuppressWarnings("unchecked")
        public <T> T runTransformed(String cName, String mName, Object... args) {
            try {
                // find full class name
                if (cName == null || cName.isEmpty())
                    cName = "." + testMethod;
                cName = cName.startsWith(".") ?
                        rootClass.getName() + cName.replace('.', '$') :
                        cName;

                // load class
                Class<?> klass = abstractionManager.findClass(cName);

                // find and execute method
                Method m = null;
                for (Method m2 : klass.getDeclaredMethods()) {
                    if (!m2.getName().equals(mName)) continue;
                    m = m2;
                    break;
                }

                if (m == null)
                    throw new NoSuchMethodException("No method for " + klass + " by name " + mName);

                boolean isStatic = Modifier.isStatic(m.getModifiers());

                Object instance;
                if (isStatic) instance = null;
                else {
                    var constructor = klass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    instance = constructor.newInstance();
                }

                m.setAccessible(true);
                return (T) m.invoke(instance, args);
            } catch (Throwable t) {
                throw new RuntimeException("Error while executing " + cName + "#" + mName, t);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T runTransformed(String mName, Object... args) {
            return runTransformed(null, mName, args);
        }
    }

    // Signifies a test
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Test {
        boolean globalAbstractionManager() default false;
        String testClass() default "";
        String abstractionImpl() default "";
        String[] hooks() default {};
        boolean fieldDependencies() default true;
        boolean autoRegisterImpls() default false;
    }

    // System.out.println
    public static <T> T println(T o) {
        System.out.println(o);
        return o;
    }

    // Run code with exceptions caught
    public static void runSafe(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // Run all tests in the given class
    public static void runTests(Class<?> klass, boolean debug) {
        try {
            Object instance = klass.newInstance();
            for (Method method : klass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                Test testAnnotation = method.getAnnotation(Test.class);
                if (testAnnotation == null) continue;
                method.setAccessible(true);

                final AbstractionProvider abstractionProvider = testAnnotation.globalAbstractionManager() ?
                        // use global abstraction manager
                        getGlobalAbstractionProvider() :
                        // create new abstraction manager
                        new AbstractionProvider(AbstractionManager.getInstance())
                                .setClassAuditPredicate(name -> name.startsWith(klass.getName()))
                                .setRequiredMethodPredicate(m -> m.ref.name().startsWith("test"))
                                .addAnalysisHook(AbstractionProvider.excludeCallsOnSelfAsDependencies())
                                .addAnalysisHook(AbstractionProvider.checkDependenciesForInterface(Abstraction.class, testAnnotation.fieldDependencies()))
                                .addAnalysisHook(AbstractionProvider.checkForExplicitImplementation(Abstraction.class))
                                .addAnalysisHook(AbstractionProvider.checkStaticFieldsNotNull())
                                .addAnalysisHook(AbstractionProvider.autoRegisterLoadedImplClasses())
                                .addAnalysisHook(new AdapterAnalysisHook(Abstraction.class, AdapterRegistry.getInstance()));

                // get test name
                final String testName = method.getName();

                // get test class and shit
                String testClassName = testAnnotation.testClass().isEmpty() ? null : klass.getName() + "$" + testAnnotation.testClass();
                String abstractionImplName = testAnnotation.abstractionImpl().isEmpty() ? null : klass.getName() + "$" + testAnnotation.abstractionImpl();

                long t1 = System.currentTimeMillis();

                // load hooks
                List<Object> hooks = new ArrayList<>();
                for (String partialHookClassName : testAnnotation.hooks()) {
                    String hookClassName = klass.getName() + "$" + partialHookClassName;
                    Class<?> hookClass = ReflectUtil.getClass(hookClassName);
                    if (hookClass == null || !ClassAnalysisHook.class.isAssignableFrom(hookClass))
                        throw new IllegalArgumentException("Couldn't find hook class by name " + abstractionImplName);
                    if (debug)
                        System.out.println("DEBUG test " + testName + ": Found hook " + hookClass);
                    Object hook = hookClass.getConstructor().newInstance();
                    abstractionProvider.addAnalysisHook((ClassAnalysisHook) hook);
                    hooks.add(hook);
                }

                Class<?> implClass  = null;
                Object implInstance = null;
                if (abstractionImplName != null) {
                    // load, register and create abstraction impl
                    implClass = ReflectUtil.getClass(abstractionImplName);
                    if (implClass == null)
                        throw new IllegalArgumentException("Couldn't find implementation class by name " + abstractionImplName);
                    if (debug)
                        System.out.println("DEBUG test " + testName + ": Found abstraction impl " + implClass);
                    abstractionProvider.abstractionManager().registerImpl(implClass);
                    implInstance = implClass.getConstructor().newInstance();
                }

                // auto register impls
                if (testAnnotation.autoRegisterImpls()) {
                    abstractionProvider.registerImplsFromResources(
                            new PackageWalker(klass, klass.getPackageName())
                                .findResources()
                                .filter(r -> r.name().startsWith(klass.getSimpleName() + "$"))
                                .filter(r -> r.trimmedName().endsWith("Impl")));
                }

                long t2 = System.currentTimeMillis();
                if (debug) System.out.println("DEBUG test " + testName + ": loading hooks and setup took " + (t2 - t1) + "ms");

                // load and create test class
                Class<?> testClass = null;
                Object testInstance = null;
                if (testClassName != null) {
                    testClass = abstractionProvider.findClass(testClassName);
                    if (testClass == null)
                        throw new IllegalArgumentException("Couldn't find test class by name " + abstractionImplName);
                    if (debug)
                        System.out.println("DEBUG test " + testName + ": Found test " + implClass);
                    testInstance = testClass.getConstructor().newInstance();
                }

                long t3 = System.currentTimeMillis();
                if (debug) System.out.println("DEBUG test " + testName + ": loading and transforming test class took " + (t3 - t2) + "ms");

                TestInterface testInterface = new TestInterface(klass, method.getName(), abstractionProvider);

                // order arguments
                Object[] args = new Object[method.getParameterTypes().length];
                for (int i = 0, n = method.getParameterTypes().length; i < n; i++) {
                    Class<?> type = method.getParameterTypes()[i];

                    if (implClass != null && type.isAssignableFrom(implClass)) args[i] = implInstance;
                    else if (testClass != null && type.isAssignableFrom(testClass)) args[i] = testInstance;
                    else if (type.isAssignableFrom(AbstractionProvider.class)) args[i] = abstractionProvider;
                    else if (TestInterface.class == type) args[i] = testInterface;
                    else for (var hook : hooks) if (type.isAssignableFrom(hook.getClass())) args[i] = hook;
                }

                try {
                    // invoke method
                    method.invoke(instance, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }

                if (testClass != null) {
                    try {
                        // check for testClass#run
                        Method mRun = testClass.getDeclaredMethod("run");
                        mRun.setAccessible(true);
                        mRun.invoke(testInstance);
                    } catch (NoSuchMethodException ignored) {

                    } catch (InvocationTargetException e) {
                        throw new RuntimeException("Exception while running " + testClass.getSimpleName() + "#run", e.getCause());
                    }
                }

                long t4 = System.currentTimeMillis();
                if (debug) System.out.println("DEBUG test " + testName + ": executing tests took " + (t4 - t3) + "ms");
                if (debug) System.out.println("DEBUG test " + testName + ": took " + (t4 - t1) + "ms total");
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

    public static boolean isDependenciesEqual(List<? extends Dependency> actual, String... expected) {
        int count = 0;
        for (String str : expected) {
            String[] split = str.split(" ");

            // check method dependency
            boolean optional = split[0].equals("optional");
            String[] split2 = split[1].split("\\.");
            String cl = split2[0];
            String name = split2[1];

            for (Dependency dep : actual) {
                if (!(dep instanceof ReferenceDependency dependency)) continue;
                if (dependency.optional() == optional && dependency.info().name().equals(name) &&
                        dependency.info().className().endsWith(cl)) {
                    count++;
                    break;
                }
            }
        }

        return count == expected.length;
    }

    public static void assertDependenciesEquals(Collection<Dependency> actual, String... expected) {
        if (expected.length != actual.size())
            fail("Expected " + expected.length + " dependencies, got " + actual.size());

        List<Dependency> list = new ArrayList<>(actual);
        for (String str : expected) {
            String[] split = str.split(" ");
            Dependency found = null;

            // check for none present
            if (split[0].equals("none")) {
                for (Dependency dep : list) {
                    if (!(dep instanceof RequireOneDependency dependency)) continue;
                    if (!dependency.implemented()) {
                        found = dependency;
                        break;
                    }
                }
            } else if (split[0].equals("one")) {
                List<String> dependencies = Arrays.stream(split)
                        .skip(1)
                        .map(s -> "required " + s)
                        .toList();

                for (Dependency dep : list) {
                    if (!(dep instanceof RequireOneDependency dependency)) continue;
                    if (isDependenciesEqual(dependency.dependencies(), dependencies.toArray(new String[0]))) {
                        found = dependency;
                        break;
                    }
                }
            } else {
                // check method dependency
                boolean optional = split[0].equals("optional");
                String[] split2 = split[1].split("\\.");
                String cl = split2[0];
                String name = split2[1];

                for (Dependency dep : list) {
                    if (!(dep instanceof ReferenceDependency dependency)) continue;
                    if (dependency.optional() == optional && dependency.info().name().equals(name) &&
                            dependency.info().className().endsWith(cl)) {
                        found = dependency;
                        break;
                    }
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
