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

import gov.nasa.jpf.constraints.api.Valuation;

class LeafWithValuation extends LeafNode {

    private Valuation values;

    LeafWithValuation(DecisionNode parent, LeafNode.NodeType type, int pos, Valuation val) {
        super(parent, type, pos);
        this.values = val;
    }

    Valuation values() {
        return values;
    }

    void updateValues(Valuation v) {
        values = v;
    }

    void print(StringBuilder out, int indent) {
        indent(out, indent);
        out.append(nodeType()).append("[complete path:").append(complete()).append("]").
                append(" . ").append( values() ).append("\n");
    }
}
