package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;

import java.util.Iterator;

public class Config {

    public boolean getExploreMode() {
        return true;
    }

    public Iterator<Valuation> getReplayValues() {
        return null;
    }

    public boolean isIncremental() {
        return false;
    }

    public SolverContext getSolverContext() {
        return null;
    }

    public enum ExplorationStrategy  {BFS, DFS, IN_ORDER};

    public boolean maxDepthExceeded(int depth) {
        return false;
    }

    public ExplorationStrategy getStrategy() {
        return null;
    }

}
