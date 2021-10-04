/*
 * Copyright (C) 2021, Automated Quality Assurance Group,
 * TU Dortmund University, Germany. All rights reserved.
 *
 * DSE (dynamic symbolic execution) is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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

