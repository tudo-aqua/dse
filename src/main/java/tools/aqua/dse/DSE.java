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

import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.trace.Trace;

public class DSE {

    private final Config config;

    public DSE(Config config) {
        this.config = config;
    }

    public void executeAnalysis() {
        Explorer explorer = new Explorer(config);
        Executor executor = new Executor(config);

        while (explorer.hasNextValuation()) {
            Valuation val = explorer.getNextValuation();
            Trace trace = executor.execute(val);
            if (trace != null) {
                trace.print();
            } else {
                System.out.println("== no trace obtained.");
            }
            explorer.addTrace(trace);
        }

        System.out.println(explorer.getAnalysis());
        System.out.println("[END OF OUTPUT]");
        System.exit(0);
    }
}
