package tools.aqua.dse.trace;

import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.paths.PathState;

import java.util.List;

public class Trace {

    private final List<Decision> decisions;

    private final PathResult traceState;

    public Trace(List<Decision> decisions, PathResult state) {
        this.decisions = decisions;
        this.traceState = state;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public PathResult getTraceState() {
        return traceState;
    }
}
