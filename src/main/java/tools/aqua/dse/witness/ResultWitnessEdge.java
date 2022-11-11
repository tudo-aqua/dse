package tools.aqua.dse.witness;

import tools.aqua.dse.trace.ResultWitnessAssumption;
import tools.aqua.dse.trace.WitnessAssumption;

public class ResultWitnessEdge {

    private final WitnessNode source;

    private final WitnessNode dest;

    private final ResultWitnessAssumption witness;

    public ResultWitnessEdge(WitnessNode source, ResultWitnessAssumption witness, WitnessNode dest) {
        this.source = source;
        this.dest = dest;
        this.witness = witness;
    }

    public WitnessNode getSource() {
        return source;
    }

    public WitnessNode getDest() {
        return dest;
    }

    public ResultWitnessAssumption getWitness() {
        return witness;
    }

}
