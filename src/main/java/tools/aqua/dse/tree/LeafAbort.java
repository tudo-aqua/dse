package tools.aqua.dse.tree;

import gov.nasa.jpf.constraints.api.Valuation;

public class LeafAbort extends LeafWithValuation {

    private final String reason;

    LeafAbort(DecisionNode parent, int pos, Valuation val, String reason) {
        super(parent, NodeType.SKIPPED, pos, val);
        this.reason = reason;
    }

    void print(StringBuilder out, int indent) {
        indent(out, indent);
        out.append(NodeType.SKIPPED).append("[complete path:").append(complete()).append("]").
                append(" . ").append( values() ).append(" . ").
                append( reason ).append("\n");
    }
}
