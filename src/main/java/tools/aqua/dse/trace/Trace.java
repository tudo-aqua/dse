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

import tools.aqua.dse.paths.PathResult;

import java.util.Arrays;
import java.util.List;

public class Trace {

    private final List<Decision> decisions;

    private final PathResult traceState;

    public Trace(List<Decision> decisions, PathResult state) {
        this.decisions = decisions;
        this.traceState = state;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public PathResult getTraceState() {
        return traceState;
    }

    public void print() {
        for (Decision d : decisions) {
            System.out.println(d);
        }
        System.out.println(traceState);
    }

    @Override
    public String toString() {
        return "Trace{" +
                "decisions=" + Arrays.toString(decisions.toArray()) +
                ", traceState=" + traceState +
                '}';
    }
}
