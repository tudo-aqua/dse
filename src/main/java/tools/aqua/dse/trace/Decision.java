package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Expression;

public class Decision {

    private final Expression<Boolean> condition;

    private final int branches;

    private final int branchId;

    private final boolean assumption;

    public Decision(Expression<Boolean> condition, int branches, int branchId) {
        this(condition, branches, branchId, false);
    }

    public Decision(Expression<Boolean> condition, int branches, int branchId, boolean assumption) {
        this.condition = condition;
        this.branches = branches;
        this.branchId= branchId;
        this.assumption = assumption;
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

    public boolean isSatBranchOfAssumption() {
        return assumption && (branchId == 1);
    }

    @Override
    public String toString() {
        return "Decision{" +
                "condition=" + condition +
                ", branches=" + branches +
                ", branchId=" + branchId +
                ", assumption=" + assumption +
                '}';
    }
}

