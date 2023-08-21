package tools.redstone.abstracraft.analysis;

import tools.redstone.abstracraft.AbstractionManager;

import java.io.PrintStream;
import java.util.Stack;

public class AnalysisContext {

    /**
     * The abstraction manager.
     */
    private final AbstractionManager abstractionManager;

    /**
     * The trace of the methods being analyzed.
     */
    public final Stack<ReferenceInfo> analysisStack = new Stack<>();

    // The current compute stacks from the methods.
    final Stack<Stack<Object>> computeStacks = new Stack<>();

    public AnalysisContext(AbstractionManager abstractionManager) {
        this.abstractionManager = abstractionManager;
    }

    // Leaves a method and updates the context to account for it
    void leaveMethod() {
        analysisStack.pop();
        computeStacks.pop();
    }

    // Updates the context when entering a method, assumes shits already on the stacks.
    void enteredMethod(ReferenceInfo info,
                       Stack<Object> computeStack) {
        analysisStack.push(info);
        computeStacks.push(computeStack);
    }

    void printAnalysisTrace(PrintStream stream) {
        for (int i = analysisStack.size() - 1; i >= 0; i--) {
            var ref = analysisStack.get(i);
            stream.println(" " + (i == analysisStack.size() - 1 ? "-> " : " - ") + ref);
        }
    }

    public AbstractionManager abstractionManager() {
        return abstractionManager;
    }

    public ReferenceInfo currentMethod() {
        if (analysisStack.isEmpty())
            return null;
        return analysisStack.peek();
    }

    public ReferenceAnalysis currentAnalysis() {
        var curr = currentMethod();
        if (curr == null)
            return null;
        return abstractionManager.getReferenceAnalysis(curr);
    }

    /** Gets the current compute stack */
    public Stack<Object> currentComputeStack() {
        if (computeStacks.isEmpty())
            return null;
        return computeStacks.peek();
    }

}
