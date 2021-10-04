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

class LeafError extends LeafWithValuation {

    private final String exceptionClass;
    private final String stackTrace;

    LeafError(DecisionNode parent, int pos, Valuation val, String exceptionClass, String stackTrace) {
        super(parent, NodeType.ERROR, pos, val);
        this.exceptionClass = exceptionClass;
        this.stackTrace = stackTrace;
    }

    void print(StringBuilder out, int indent) {
        indent(out, indent);
        out.append(NodeType.ERROR).append("[complete path:").append(complete()).append("]").
                append(" . ").append( values() ).append(" . ").
                append( exceptionClass ).append("\n");
    }
}
