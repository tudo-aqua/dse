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

package tools.aqua.dse.tree;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.expressions.Negation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.trace.Decision;

class DecisionNode extends Node {

    private final Expression<Boolean>[] constraints;
    private final Node[] children;

    DecisionNode(DecisionNode parent, Decision d, int pos,
                 boolean explore, ExplorationStrategy strategy) {
        super(parent, pos);

        this.constraints = new Expression[d.getBranches()];
        this.constraints[d.getBranchId()] = d.getCondition();
        this.children = new Node[constraints.length];

        if (!explore) {
            for (int i = 0; i < constraints.length; i++) {
                this.children[i] = LeafNode.skipped(this, i);
            }
        } else {
            for (int i = 0; i < constraints.length; i++) {
                if (d.isSatBranchOfAssumption() && i != d.getBranchId()) {
                    this.children[i] = LeafNode.skipped(this, i);
                }
                else {
                    this.children[i] = LeafNode.open(this, i);
                    strategy.newOpen( (LeafNode) this.children[i] );
                }
            }
        }
    }

    Expression<Boolean> getConstraint(int idx) {
        if (constraints[idx] != null) {
            return constraints[idx];
        }
        else {
            Expression<Boolean> knownOthers = null;
            for (Expression<Boolean> cond : constraints) {
                if (cond != null) {
                    knownOthers = (knownOthers == null) ? cond : ExpressionUtil.or(knownOthers, cond);
                }
            }
            return (knownOthers == null) ? ExpressionUtil.TRUE : new Negation(knownOthers);
        }
    }

    void update(Decision d) {
        if (constraints[d.getBranchId()] == null) {
            constraints[d.getBranchId()] = d.getCondition();
        }
    }

    void useUnexploredConstraint(int idx) {
        constraints[idx] = getConstraint(idx);
    }

    int missingConstraints() {
        int i = 0;
        for (Expression<Boolean> e : constraints) {
            if (e == null) {
                i++;
            }
        }
        return i;
    }

    Node getChild(int idx) {
        return children[idx];
    }

    Node[] children() {
        return this.children;
    }

    @Override
    boolean isDecisionNode() {
        return true;
    }

    void expand(LeafNode leaf, DecisionNode newChild) {
        children[leaf.childId()] = newChild;
    }

    void replace(LeafNode oldLeaf, LeafNode newLeaf) {
        children[oldLeaf.childId()] = newLeaf;
    }

    /**
     *
     * @param values
     * @return -1 if no constraint is satisfied
     * @throws RuntimeException e.g. due to function with undefined semantics
     */
    int evaluate(Valuation values) throws RuntimeException {
        for (int i = 0; i < constraints.length; i++) {
            Expression<Boolean> constraint = constraints[i];
            if (constraint.evaluate(values)) {
                return i;
            }
        }
        return -1;
    }

    String validateDecision(Decision d) {
        if (constraints != null && constraints.length != d.getBranches()) {
            return "Same decision, but different number of constraints!";
        }
        return null;
    }

    void print(StringBuilder out, int indent) {
        for (int i=0; i< children.length; i++) {
            indent(out, indent);
            out.append(i).append(" : ").append(constraints[i]).append("\n");
            children[i].print(out, indent + 1);
        }
    }
}
