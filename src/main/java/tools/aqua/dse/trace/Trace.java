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
import java.util.Set;

public class Trace {

    private final List<Decision> decisions;

    private final List<String> summaries;

    private final List<String> declarations; //todo: change to Set to avoid duplicates

    private final List<WitnessAssumption> witness;

    private final List<String> flows;

    private final PathResult traceState;

    private final int objectCount;

    private final Set<String> objectIdentifiers;

    public Trace(List<Decision> decisions, PathResult state) {
        this(decisions, null, null, null, null, state, 0, null);
    }

    public Trace(List<Decision> decisions,
                 List<String> summaries,
                 List<String> declaration,
                 List<WitnessAssumption> witness,
                 List<String> flows,
                 PathResult state,
                 int objectCount,
                 Set<String> objectIdentifiers) {
        this.decisions = decisions;
        this.summaries = summaries;
        this.declarations = declaration;
        this.witness = witness;
        this.flows = flows;
        this.traceState = state;
        this.objectCount = objectCount;
        this.objectIdentifiers = objectIdentifiers;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public boolean hasWitness() {
        return witness != null;
    }

    public List<WitnessAssumption> getWitness() {
        return witness;
    }

    public List<String> getFlows() {
        return flows;
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

    public int getObjectCount() {
        return objectCount;
    }

    public List<String> getDeclarations() {
        return declarations;
    }

    public Set<String> getObjectIdentifiers() {
        return objectIdentifiers;
    }

    public List<String> getSummaries() {
        return summaries;
    }

    @Override
    public String toString() {
        return "Trace{" +
                "decisions=" + Arrays.toString(decisions.toArray()) +
                ", traceState=" + traceState +
                ", flows=" + flows +
                '}';
    }
}
