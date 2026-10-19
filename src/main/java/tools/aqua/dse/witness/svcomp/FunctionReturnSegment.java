package tools.aqua.dse.witness.svcomp;

public class FunctionReturnSegment extends WitnessSegment {

    public String constraint;
    public FunctionReturnSegment(String constraint, WitnessLocation loc ) {
        super.type = "function_return";
        super.action = "follow";
        this.constraint = constraint;
        super.location = loc;
    }


}
