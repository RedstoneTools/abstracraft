package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;
import tools.redstone.abstracraft.core.analysis.*;
import tools.redstone.abstracraft.core.usage.Abstraction;
import tools.redstone.abstracraft.core.util.ASMUtil;
import tools.redstone.abstracraft.core.util.ReflectUtil;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;

/**
 * Manages all systems related to abstracting.
 * Notably contains the implementation class for each abstraction.
 *
 * Used to check whether a specific behavior is implemented by
 * any given class.
 *
 * @author orbyfied
 */
public class AbstractionManager {

    record DefaultImplAnalysis(Set<ReferenceInfo> unimplementedMethods) { }

    Predicate<String> classAuditPredicate = s -> true;                                                                  // The predicate for abstraction class names.
    Predicate<ClassDependencyAnalyzer.ReferenceAnalysis> requiredMethodPredicate = m -> m.optionalReferenceNumber <= 0; // The predicate for required methods.
    final List<DependencyAnalysisHook> analysisHooks = new ArrayList<>();                                               // The global dependency analysis hooks

    final Map<Class<?>, Class<?>> implByBaseClass = new HashMap<>();                                                    // The registered implementation classes by base class
    final Map<ReferenceInfo, Boolean> implementedCache = new HashMap<>();                                               // A cache to store whether a specific method is implemented for fast access

    final Map<ReferenceInfo, ClassDependencyAnalyzer.ReferenceAnalysis> refAnalysisMap = new HashMap<>();               // All analyzed methods by their descriptor
    final Map<String, ClassDependencyAnalyzer> analyzerMap = new HashMap<>();                                           // All analyzers by class name
    final ClassLoader transformingClassLoader;

    final ClassDependencyAnalyzer partialAnalyzer;

    public AbstractionManager() {
        // create class loader
        this.transformingClassLoader = ReflectUtil.transformingClassLoader(
                // name predicate
                name -> !name.startsWith("java") && classAuditPredicate.test(name),
                // parent class loader
                getClass().getClassLoader(),
                // transformer
                ((name, reader, writer) -> {
                    var analyzer = analyzer(name, true);
                    if (analyzer.getClassAnalysis() == null || !analyzer.getClassAnalysis().completed)
                        analyzer.analyzeAndTransform();
                    analyzer.getClassNode().accept(writer);
                }), ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, true);

        this.partialAnalyzer = new ClassDependencyAnalyzer(this, null);
    }

    public AbstractionManager setClassAuditPredicate(Predicate<String> classAuditPredicate) {
        this.classAuditPredicate = classAuditPredicate;
        return this;
    }

    public AbstractionManager setRequiredMethodPredicate(Predicate<ClassDependencyAnalyzer.ReferenceAnalysis> requiredMethodPredicate) {
        this.requiredMethodPredicate = requiredMethodPredicate;
        return this;
    }

    public Predicate<String> getClassAuditPredicate() {
        return classAuditPredicate;
    }

    public Predicate<ClassDependencyAnalyzer.ReferenceAnalysis> getRequiredMethodPredicate() {
        return requiredMethodPredicate;
    }

    /**
     * Get the base abstraction class from the given interface.
     *
     * @param startClass The starting interface.
     * @return The base abstraction class/interface.
     */
    public List<Class<?>> getApplicableAbstractionClasses(Class<?> startClass) {
        Class<?> current = startClass;
        outer: while (current != null) {
            if (current.isInterface())
                break;

            // find next interface by finding the
            // interface implementing Abstraction
            for (Class<?> itf : current.getInterfaces()) {
                if (Abstraction.class.isAssignableFrom(itf)) {
                    current = itf;
                    continue outer;
                }
            }

            throw new IllegalArgumentException(startClass + " is not an Abstraction class");
        }

        if (current == null)
            throw new IllegalArgumentException("Could not find base abstraction for " + startClass);
        return List.of(current);
    }

    /**
     * Registers the given implementation class.
     *
     * @param implClass The implementation.
     */
    public void registerImpl(Class<?> implClass) {
        for (Class<?> kl : getApplicableAbstractionClasses(implClass)) {
            implByBaseClass.put(kl, implClass);
        }
    }

    /**
     * Get the implementation of the given class if present.
     *
     * @param baseClass The class.
     * @return The implementation.
     */
    public Class<?> getImplByClass(Class<?> baseClass) {
        return implByBaseClass.get(baseClass);
    }

    // Check whether the given ref is implemented
    // without referencing the cache
    private boolean isImplemented0(ReferenceInfo ref) {
        // get abstraction class
        Class<?> refClass = ReflectUtil.getClass(ref.ownerClassName());
        if (refClass == null)
            return false;

        try {
            // check hooks
            for (var hook : analysisHooks) {
                var res = hook.checkImplemented(this, ref, refClass);
                if (res == null) continue;
                return res;
            }

            // otherwise just assume it is available
            // because it exists
            return true;
        } catch (Throwable t) {
            throw new RuntimeException("Error while checking implementation status of " + ref, t);
        }
    }

    /**
     * Check whether the given method is implemented for it's
     * owning abstraction.
     *
     * @param method The method.
     * @return Whether it is implemented.
     */
    public boolean isImplemented(ReferenceInfo method) {
        Boolean b = implementedCache.get(method);
        if (b != null)
            return b;

        implementedCache.put(method, b = isImplemented0(method));
        return b;
    }

    /**
     * Check whether all given methods are implemented.
     *
     * @param methods The methods.
     * @return Whether they are all implemented.
     */
    public boolean areAllImplemented(List<ReferenceInfo> methods) {
        if (methods == null)
            return true;

        for (ReferenceInfo i : methods) {
            if (!isImplemented(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check whether all the required given dependencies are implemented.
     *
     * @param dependencies The dependencies.
     * @return Whether they are all implemented.
     */
    public boolean areAllRequiredImplemented(List<MethodDependency> dependencies) {
        return areAllImplemented(dependencies.stream()
                .filter(d -> !d.optional())
                .map(MethodDependency::info)
                .toList());
    }

    /**
     * Manually set whether something is implemented.
     *
     * @param info The method.
     * @param b The status.
     */
    public void setImplemented(ReferenceInfo info, boolean b) {
        implementedCache.put(info, b);
    }

    /**
     * Get or create an analyzer for the given class name.
     *
     * @param className The class name.
     * @return The analyzer.
     */
    public ClassDependencyAnalyzer analyzer(String className, boolean ignoreLoadedClasses) {
        // get cached/active
        String publicName = className.replace('/', '.');
        ClassDependencyAnalyzer analyzer = analyzerMap.get(publicName);
        if (analyzer != null)
            return analyzer;

        try {
            className = className.replace('.', '/');

            if (ignoreLoadedClasses && ReflectUtil.findLoadedClass(transformingClassLoader, publicName) != null) {
                return null;
            }

            if (!classAuditPredicate.test(publicName)) {
                return null;
            }

            // open resource
            String classAsPath = className + ".class";
            try (InputStream stream = transformingClassLoader.getResourceAsStream(classAsPath)) {
                if (stream == null)
                    throw new IllegalArgumentException("Could not find resource stream for " + classAsPath);
                byte[] bytes = stream.readAllBytes();

                ClassReader reader = new ClassReader(bytes);

                // create and register analyzer
                analyzer = new ClassDependencyAnalyzer(this, reader);
                analyzerMap.put(publicName, analyzer);

                analyzer.hooks.addAll(this.analysisHooks);

                return analyzer;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while creating MethodDependencyAnalyzer for class " + className, e);
        }
    }

    public ClassDependencyAnalyzer analyzer(Class<?> klass) {
        return analyzer(klass.getName(), false);
    }

    public ClassDependencyAnalyzer.ReferenceAnalysis getMethodAnalysis(ReferenceInfo info) {
        return refAnalysisMap.get(info);
    }

    public ClassDependencyAnalyzer.ReferenceAnalysis registerAnalysis(ClassDependencyAnalyzer.ReferenceAnalysis analysis) {
        refAnalysisMap.put(analysis.ref, analysis);
        return analysis;
    }

    /**
     * Find/load a class using the transforming class loader
     * of this abstraction manager.
     *
     * @param name The name.
     * @return The class.
     */
    public Class<?> findClass(String name) {
        return ReflectUtil.getClass(name, this.transformingClassLoader);
    }

    /**
     * Analyzes and transforms the given method if it is not
     * being currently analyzed (recursion, it is present in the stack)
     *
     * @param context The context of the analysis.
     * @param info The method to analyze.
     * @return The analysis or null.
     */
    public ClassDependencyAnalyzer.ReferenceAnalysis publicReference(AnalysisContext context, ReferenceInfo info) {
        // check for cached
        var analysis = getMethodAnalysis(info);
        if (analysis != null && analysis.complete && !analysis.partial)
            return analysis;

        // fields are always partial
        if (info.isField()) {
            analysis = new ClassDependencyAnalyzer.ReferenceAnalysis(partialAnalyzer, info);
            analysis.partial = true;
            analysis.complete = true;
            refAnalysisMap.put(info, analysis);
            return analysis;
        }

        // analyze through owner class
        ClassDependencyAnalyzer analyzer = this.analyzer(info.ownerInternalName(), true);
        if (analyzer == null) {
            if (analysis != null)
                return analysis;
            analysis = new ClassDependencyAnalyzer.ReferenceAnalysis(partialAnalyzer, info);
            analysis.partial = true;
            analysis.complete = true;
            refAnalysisMap.put(info, analysis);
            return analysis;
        } else if (analysis != null && analysis.partial) {
            // use actual analyzer to replace partial analysis
            var newAnalysis = analyzer.localMethod(context, info);
            if (newAnalysis.complete) {
                newAnalysis.refHooks.addAll(analysis.refHooks);
                newAnalysis.optionalReferenceNumber += analysis.optionalReferenceNumber;
            }

            return newAnalysis;
        }

        return analyzer.localMethod(context, info);
    }

    public boolean allImplemented(Class<?> klass) {
        var analyzer = analyzer(klass);
        if (analyzer == null || !analyzer.getClassAnalysis().completed)
            return false;
        return analyzer.getClassAnalysis().areAllImplemented(this);
    }

    /**
     * Get the class analysis for the given class, or null
     * if not available.
     *
     * @param klass The class.
     * @return The analysis.
     */
    public ClassDependencyAnalyzer.ClassAnalysis getClassAnalysis(Class<?> klass) {
        var analyzer = analyzer(klass);
        if (analyzer == null || !analyzer.getClassAnalysis().completed)
            return null;
        return analyzer.getClassAnalysis();
    }

    public AbstractionManager addAnalysisHook(DependencyAnalysisHook hook) {
        this.analysisHooks.add(hook);
        this.partialAnalyzer.addHook(hook);
        return this;
    }

    /* ------------ Hooks -------------- */

    public record ClassInheritanceChecker(Class<?> itf, Map<String, Boolean> cache) {
        private static final Map<Class<?>, ClassInheritanceChecker> checkerCache = new HashMap<>();

        public static ClassInheritanceChecker forClass(Class<?> itf) {
            return checkerCache.computeIfAbsent(itf, __ -> new ClassInheritanceChecker(itf, new HashMap<>()));
        }

        public boolean from(String name) {
            Boolean b = cache.get(name);
            if (b != null)
                return b;

            try {
                cache.put(name, b = itf.isAssignableFrom(ReflectUtil.getClass(name)));
                return b;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** Allows dependencies which call to a class implementing the given interface */
    public static DependencyAnalysisHook checkDependenciesForInterface(final Class<?> itf, boolean includeFields) {
        final ClassInheritanceChecker checker = ClassInheritanceChecker.forClass(itf);
        return new DependencyAnalysisHook() {
            @Override
            public Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) {
                if (!includeFields && ref.isField())
                    return null;
                return checker.from(ref.ownerClassName()) ? true : null;
            }
        };
    }

    /** Checks the bytecode and declaration class of methods to determine whether they are implemented */
    public static DependencyAnalysisHook checkForExplicitImplementation(Class<?> unimplementedProvidingItf) {
        final ClassInheritanceChecker checker = ClassInheritanceChecker.forClass(unimplementedProvidingItf);
        return new DependencyAnalysisHook() {
            final Map<Class<?>, DefaultImplAnalysis> defaultImplAnalysisCache = new HashMap<>(); // Cache for default implementation analysis per class

            // Check the bytecode of the owner of the given method
            // to see whether
            private boolean checkBytecodeImplemented(Method method) {
                ReferenceInfo methodInfo = ReferenceInfo.forMethod(method);
                Class<?> klass = method.getDeclaringClass();

                // check cache
                DefaultImplAnalysis analysis = defaultImplAnalysisCache.get(klass);
                if (analysis != null)
                    return !analysis.unimplementedMethods().contains(methodInfo);

                // analyze bytecode
                final Set<ReferenceInfo> unimplementedMethods = new HashSet<>();
                ReflectUtil.analyze(klass, new ClassVisitor(ASMUtil.ASM_V) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        final ReferenceInfo currentMethod = ReferenceInfo.forMethodInfo(klass.getName(), name, descriptor, Modifier.isStatic(access));
                        return new MethodVisitor(ASMUtil.ASM_V) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                // check for Abstraction#unimplemented call
                                if (checker.from(owner.replace('/', '.')) && "unimplemented".equals(name) && descriptor.startsWith("()")) {
                                    unimplementedMethods.add(currentMethod);
                                }
                            }
                        };
                    }
                });

                defaultImplAnalysisCache.put(klass, analysis = new DefaultImplAnalysis(unimplementedMethods));
                return !unimplementedMethods.contains(methodInfo);
            }

            @Override
            public Boolean checkImplemented(AbstractionManager manager, ReferenceInfo ref, Class<?> refClass) throws Throwable {
                if (ref.isField())
                    return null; // nothing to say

                // get implementation class for abstraction
                Class<?> implClass = manager.getImplByClass(refClass);
                if (implClass == null)
                    // object not implemented at all
                    return false;

                // check ref declaration
                Method m = implClass.getMethod(ref.name(), ASMUtil.asClasses(ref.type().getArgumentTypes()));

                if (m.getDeclaringClass() == refClass)
                    return checkBytecodeImplemented(m);
                if (m.getDeclaringClass().isInterface() && !m.isDefault())
                    return false;
                return !Modifier.isAbstract(m.getModifiers());
            }
        };
    }

    /** Checks static field dependencies for a not null value to determine if they're implemented */
    public static DependencyAnalysisHook checkStaticFieldsNotNull() {
        return new DependencyAnalysisHook() {
            @Override
            public Boolean checkImplemented(AbstractionManager manager, ReferenceInfo ref, Class<?> refClass) throws Throwable {
                if (!ref.isField() || !ref.isStatic()) // nothing to say
                    return null;

                // find field
                Field field = refClass.getField(ref.name());
                field.setAccessible(true);

                // check field set
                return field.get(null) != null;
            }
        };
    }

}
