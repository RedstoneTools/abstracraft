package tools.redstone.abstracraft.core.analysis;

import tools.redstone.abstracraft.core.AbstractionManager;

/**
 * Used to extend functionality of the dependency analyzer.
 */
public interface DependencyAnalysisHook {

    interface ReferenceHook {
        // When the optional block this method was
        // called in is discarded
        default void optionalBlockDiscarded(AnalysisContext context) { }

        // When the method is called through required means
        default void requiredReference(AnalysisContext context) { }

        // When the method is called through an optional block
        default void optionalReference(AnalysisContext context) { }

        // Called in post-analysis
        default void postAnalyze() { }
    }

    // When a method is referenced in a required block
    default ReferenceHook requiredReference(AnalysisContext context, ClassDependencyAnalyzer.ReferenceAnalysis called) { return null; }

    // When a method is referenced in an optional block
    default ReferenceHook optionalReference(AnalysisContext context, ClassDependencyAnalyzer.ReferenceAnalysis called) { return null; }

    // When a new method is entered to be analyzed
    default void enterMethod(AnalysisContext context) { }

    // When a method has finished analyzing
    default void leaveMethod(AnalysisContext context) { }

    // Is dependency checks
    default Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) { return null; }

    // Dependency presence checks
    default Boolean checkImplemented(AbstractionManager manager, ReferenceInfo ref, Class<?> refClass) throws Throwable { return null; }

}
