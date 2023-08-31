package tools.redstone.abstracraft;

import tools.redstone.picasso.AbstractionManager;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.adapter.Adapter;
import tools.redstone.picasso.adapter.AdapterAnalysisHook;
import tools.redstone.picasso.adapter.DynamicAdapterRegistry;
import tools.redstone.picasso.analysis.ClassAnalysisHook;
import tools.redstone.picasso.usage.Abstraction;
import tools.redstone.picasso.util.PackageWalker;
import tools.redstone.picasso.adapter.AdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides Abstracraft services like the {@link AbstractionProvider} for the
 * rest of the Abstracraft project. This is so other modules can use local
 * {@link AbstractionProvider}s if they want.
 *
 * This is the only class which is actually specific to Abstracraft. The rest of
 * the core is reusable in other projects.
 */
public class Abstracraft {
    private Abstracraft() { }

    // Finds implementation classes as resources
    public interface ImplFinder {
        Stream<PackageWalker.Resource> findResources(AbstractionProvider manager);

        static ImplFinder inPackage(Class<?> codeSrc, String pkg) {
            var walker = new PackageWalker(codeSrc, pkg);
            return __ -> walker.findResources();
        }
    }

    private static final Abstracraft INSTANCE = new Abstracraft();

    public static Abstracraft getInstance() {
        return INSTANCE;
    }

    /**
     * The abstraction manager.
     */
    private final AbstractionManager manager = AbstractionManager.getInstance();

    /**
     * The adapter registry.
     */
    private final AdapterRegistry adapterRegistry = new DynamicAdapterRegistry();

    /**
     * The abstraction provider.
     */
    private final AbstractionProvider provider = manager.createProvider()
            .addAnalysisHook(AbstractionProvider.excludeCallsOnSelfAsDependencies())
            .addAnalysisHook(AbstractionProvider.checkDependenciesForInterface(Abstraction.class, true))
            .addAnalysisHook(AbstractionProvider.checkStaticFieldsNotNull())
            .addAnalysisHook(AbstractionProvider.checkForExplicitImplementation(Abstraction.class))
            .addAnalysisHook(AbstractionProvider.autoRegisterLoadedImplClasses())
            .addAnalysisHook(new AdapterAnalysisHook(Abstraction.class, adapterRegistry));

    // The packages to find implementation classes to
    // register from
    private final List<ImplFinder> implFinders = new ArrayList<>();

    {
        implFinders.add(ImplFinder.inPackage(Abstraction.class, ABSTRACRAFT_IMPLEMENTATION_PACKAGE));
    }

    protected AbstractionProvider getProvider() {
        return provider;
    }

    // The package under which all impls are located
    public static final String ABSTRACRAFT_IMPLEMENTATION_PACKAGE = "tools.redstone.abstracraft.impl";

    /**
     * Get the adapter registry used
     *
     * @return The adapter registry.
     */
    public AdapterRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    public void registerAdapter(Adapter<?, ?> adapter) {
        getAdapterRegistry().register(adapter);
    }

    /**
     * Adds the given analysis hook to the manager.
     *
     * @param hook The hook.
     */
    public void addAnalysisHook(ClassAnalysisHook hook) {
        provider.addAnalysisHook(hook);
    }

    /**
     * Adds the given implementation class finder.
     *
     * @param finder The finder.
     */
    public void addImplementationFinder(ImplFinder finder) {
        implFinders.add(finder);
    }

    /**
     * Registers all implementation classes found by the given
     * implementation finders.
     */
    public void findAndRegisterImplementations() {
        for (var finder : implFinders) {
            finder.findResources(provider).forEach(provider::loadAndRegisterImpl);
        }
    }

    /**
     * Get a class by the given name if loaded, otherwise
     * transform it using the abstraction manager.
     *
     * @param name The class name.
     * @return The class.
     */
    public Class<?> getOrTransformClass(String name) {
        return provider.findClass(name);
    }

    @SuppressWarnings("unchecked")
    public <A> Class<? extends A> getImplementationClass(Class<A> abstraction) {
        return (Class<? extends A>) manager.getImplByClass(abstraction);
    }

    /**
     * Adapt the given value of type A to a class of type B
     * using the adapter registry.
     *
     * @param value The value to adapt.
     * @param bClass The destination class.
     * @param <A> The source value type.
     * @param <B> The destination value type.
     * @return The destination value.
     */
    @SuppressWarnings("unchecked")
    public static <A, B> B adapt(A value, Class<B> bClass) {
        if (value == null)
            return null;
        var func = INSTANCE.adapterRegistry
                .findAdapterFunction(value.getClass(), bClass);
        if (func == null)
            throw new IllegalArgumentException("No adapter function for " + value.getClass() + " -> " + bClass);
        return (B) func.adapt(value);
    }

}
