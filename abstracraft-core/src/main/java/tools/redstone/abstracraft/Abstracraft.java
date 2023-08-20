package tools.redstone.abstracraft;

import tools.redstone.abstracraft.adapter.Adapter;
import tools.redstone.abstracraft.adapter.AdapterAnalysisHook;
import tools.redstone.abstracraft.analysis.ClassAnalysisHook;
import tools.redstone.abstracraft.usage.Abstraction;
import tools.redstone.abstracraft.util.PackageWalker;
import tools.redstone.abstracraft.adapter.AdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides Abstracraft services like the {@link AbstractionManager} for the
 * rest of the Abstracraft project. This is so other modules can use local
 * {@link AbstractionManager}s if they want.
 */
public class Abstracraft {

    // Finds implementation classes as resources
    public interface ImplFinder {
        Stream<PackageWalker.Resource> findResources(AbstractionManager manager);

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
    private final AbstractionManager manager = new AbstractionManager()
            .addAnalysisHook(AbstractionManager.checkDependenciesForInterface(Abstraction.class, true))
            .addAnalysisHook(AbstractionManager.checkStaticFieldsNotNull())
            .addAnalysisHook(AbstractionManager.checkForExplicitImplementation(Abstraction.class))
            .addAnalysisHook(new AdapterAnalysisHook(Abstraction.class, getAdapterRegistry()));

    // The packages to find implementation classes to
    // register from
    private final List<ImplFinder> implFinders = new ArrayList<>();

    {
        implFinders.add(ImplFinder.inPackage(Abstraction.class, ABSTRACRAFT_IMPLEMENTATION_PACKAGE));
    }

    protected AbstractionManager getManager() {
        return manager;
    }

    // The package under which all impls are located
    public static final String ABSTRACRAFT_IMPLEMENTATION_PACKAGE = "tools.redstone.abstracraft.impl";

    /**
     * Get the adapter registry used
     *
     * @return The adapter registry.
     */
    public AdapterRegistry getAdapterRegistry() {
        return AdapterRegistry.getInstance();
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
        manager.addAnalysisHook(hook);
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
            manager.registerImplsFromResources(finder.findResources(manager));
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
        return manager.findClass(name);
    }

    @SuppressWarnings("unchecked")
    public <A> Class<? extends A> getImplementationClass(Class<A> abstraction) {
        return (Class<? extends A>) manager.getImplByClass(abstraction);
    }

}
