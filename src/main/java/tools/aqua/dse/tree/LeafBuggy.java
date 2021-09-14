package tools.aqua.dse.tree;

import gov.nasa.jpf.constraints.api.Valuation;

class LeafBuggy extends LeafWithValuation {

    private final String cause;

    LeafBuggy(DecisionNode parent, int pos, Valuation val, String cause) {
        super(parent, NodeType.BUGGY, pos, val);
        this.cause = cause;
        this.setComplete(false);
    }

}
