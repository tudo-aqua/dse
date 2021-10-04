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

package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.tree.ConstraintsTree;
import tools.aqua.dse.tree.ConstraintsTreeAnalysis;

public class Explorer {

    private Valuation nextValuation = new Valuation();

    private final ConstraintsTree ctree;

    public Explorer(Config config) {
        this.ctree = new ConstraintsTree(config);
    }

    public void addTrace(Trace t) {
        if (t == null) {
            ctree.failCurrentTargetBuggy("not executed or failed");
        }
        else {
            for (Decision d : t.getDecisions()) {
                ConstraintsTree.BranchEffect effect = ctree.decision(d);
            }
            ctree.finish(t.getTraceState());
        }
        this.nextValuation = ctree.findNext();
    }

    public boolean hasNextValuation() {
        return nextValuation != null;
    }

    public Valuation getNextValuation() {
        return nextValuation;
    }

    public ConstraintsTreeAnalysis getAnalysis() {
        return new ConstraintsTreeAnalysis(this.ctree);
    }
}
