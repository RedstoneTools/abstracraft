package tools.redstone.abstracraft.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static tools.redstone.abstracraft.util.CollectionUtil.addIfNotNull;

/**
 * Stores data about a reference. If not partial, this will also include
 * data gathered from analyzing the bytecode of the method.
 */
public class ReferenceAnalysis {
    public final ClassDependencyAnalyzer analyzer;                            // The analyzer instance.
    public final ReferenceInfo ref;                                           // The reference this analysis covers
    public List<ReferenceInfo> requiredDependencies = new ArrayList<>();      // All recorded required dependencies used by this method
    public int optionalReferenceNumber = 0;                                   // Whether this method is referenced in an optionally() block
    public List<ReferenceAnalysis> allAnalyzedReferences = new ArrayList<>(); // The analysis objects of all methods/fields normally called by this method
    public boolean complete = false;                                          // Whether this analysis has completed all mandatory tasks
    public boolean partial = false;                                           // Whether this analysis is used purely to store meta or if it is actually analyzed with bytecode analysis
    public final boolean field;

    public List<DependencyAnalysisHook.ReferenceHook> refHooks = new ArrayList<>();

    public ReferenceAnalysis(ClassDependencyAnalyzer analyzer, ReferenceInfo ref) {
        this.analyzer = analyzer;
        this.ref = ref;
        this.field = ref.isField();
    }

    // Checked refHooks.add
    private void addRefHook(DependencyAnalysisHook hook, Supplier<DependencyAnalysisHook.ReferenceHook> supplier) {
        // create new ref hook
        addIfNotNull(refHooks, supplier.get());
    }

    // Register and propagate that this method is part of an optional block
    public void referenceOptional(AnalysisContext context) {
        for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.optionalReference(context, this));
        for (var refHook : refHooks) refHook.optionalReference(context);

        this.optionalReferenceNumber += 2;
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.referenceOptional(context);
        }
    }

    // Register and propagate that this method is required
    public void referenceRequired(AnalysisContext context) {
        for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.requiredReference(context, this));
        for (var refHook : refHooks) refHook.requiredReference(context);

        this.optionalReferenceNumber -= 1;
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.referenceRequired(context);
        }
    }

    // Register and propagate that this method was dropped from an optionally() block
    public void optionalReferenceDropped(AnalysisContext context) {
        for (var refHook : refHooks) refHook.optionalBlockDiscarded(context);
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.optionalReferenceDropped(context);
        }
    }

    // Finish analysis of the method
    public void postAnalyze() {
        for (var refHook : refHooks) refHook.postAnalyze();
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.postAnalyze();
        }
    }

    public void registerReference(ReferenceInfo info) {
        addIfNotNull(allAnalyzedReferences, analyzer.getReferenceAnalysis(info));
    }

    public void registerReference(ReferenceAnalysis analysis) {
        allAnalyzedReferences.add(analysis);
    }

    public boolean isPartial() {
        return partial;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isField() {
        return field;
    }
}
