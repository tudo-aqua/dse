package tools.aqua.dse.witness.svcomp;

public abstract class WitnessSegment {
    public String type; // This is very one sides yet. All types are: assumption, target,function_enter,function_return,branching
    public String action;
    public WitnessLocation location;
}
