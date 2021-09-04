package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Expression;

public class Decision {

    private final Expression<Boolean> condition;

    private final int branches;

    private final int branchId;

    public Decision(Expression<Boolean> condition, int branches, int branchId) {
        this.condition = condition;
        this.branches = branches;
        this.branchId= branchId;
    }

    public Expression<Boolean> getCondition() {
        return condition;
    }

    public int getBranches() {
        return branches;
    }

    public int getBranchId() {
        return branchId;
    }

    @Override
    public String toString() {
        return "Decision{" +
                "condition=" + condition +
                ", branches=" + branches +
                ", branchId=" + branchId +
                '}';
    }
}

