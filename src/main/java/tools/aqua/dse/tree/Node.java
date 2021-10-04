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

abstract class Node {

    private final DecisionNode parent;

    /**
     * depth in tree
     */
    private final int depth;

    /**
     *
     */
    private final int position;

    Node(DecisionNode parent, int childId) {
        this.parent = parent;
        this.depth = (parent != null) ? parent.depth() + 1 : 0;
        this.position = childId;
    }

    DecisionNode parent() {
        return parent;
    }

    int depth() {
        return depth;
    }

    int childId() {
        return position;
    }

    abstract boolean isDecisionNode();

    abstract void print(StringBuilder out, int indent);

    void indent(StringBuilder out, int indent) {
        for (int i=0; i<indent; i++) {
            out.append("  ");
        }
        out.append("+ ");
    }
}
