package tools.aqua.dse.tree;

import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.paths.PostCondition;

public class LeafOK extends LeafWithValuation {

    final PostCondition postCondition;

    LeafOK(DecisionNode parent, int pos, Valuation val, PostCondition postCondition) {
        super(parent, NodeType.OK, pos, val);
        this.postCondition = postCondition;
    }

}
