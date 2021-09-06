package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import org.checkerframework.checker.units.qual.C;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.tree.ConstraintsTree;
import tools.aqua.dse.tree.ConstraintsTreeAnalysis;

public class Explorer {

    private Valuation nextValuation = new Valuation();

    private final ConstraintsTree ctree;

    public Explorer(Config config) {
        this.ctree = new ConstraintsTree(config);
    }

    public void addTrace(Trace t) {
        if (t == null) {
            ctree.failCurrentTargetBuggy("not executed or failed");
        }
        else {
            for (Decision d : t.getDecisions()) {
                ConstraintsTree.BranchEffect effect = ctree.decision(d);
            }
            ctree.finish(t.getTraceState());
        }
        this.nextValuation = ctree.findNext();
    }

    public boolean hasNextValuation() {
        return nextValuation != null;
    }

    public Valuation getNextValuation() {
        return nextValuation;
    }

    public ConstraintsTreeAnalysis getAnalysis() {
        return new ConstraintsTreeAnalysis(this.ctree);
    }
}
