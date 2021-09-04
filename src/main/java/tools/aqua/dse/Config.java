package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.solvers.nativez3.NativeZ3SolverProvider;

import java.util.Iterator;
import java.util.Properties;

public class Config {

    public enum ExplorationStrategy  {BFS, DFS, IN_ORDER};

    private ConstraintSolver solver;

    private ExplorationStrategy strategy = ExplorationStrategy.DFS;

    public Config() {
        NativeZ3SolverProvider provider = new NativeZ3SolverProvider();
        this.solver= provider.createSolver(new Properties());
    }

    /**
     * should dse explore open nodes
     * @return
     */
    public boolean getExploreMode() {
        return true;
    }

    /**
     * values to replay before exploring
     *
     * @return
     */
    public Iterator<Valuation> getReplayValues() {
        return null;
    }

    /**
     * use incremental solving
     *
     * @return
     */
    public boolean isIncremental() {
        return false;
    }

    /**
     * constraint solver context
     *
     * @return
     */
    public SolverContext getSolverContext() {
        return this.solver.createContext();
    }

    /**
     * max depth of exploration exceeded at depth
     *
     * @param depth
     * @return
     */
    public boolean maxDepthExceeded(int depth) {
        return false;
    }

    /**
     * exploration strategy
     *
     * @return
     */
    public ExplorationStrategy getStrategy() {
        return strategy;
    }

}
