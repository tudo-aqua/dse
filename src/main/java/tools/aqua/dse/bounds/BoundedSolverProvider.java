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

package tools.aqua.dse.bounds;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverProvider;

import java.util.Properties;

public class BoundedSolverProvider implements ConstraintSolverProvider {

	@Override
	public String[] getNames() {
		return new String[]{"tools/aqua/dse/bounds"};
	}

	@Override
	public ConstraintSolver createSolver(Properties config) {
		String dp = config.getProperty("dse.dp");

		int bound = 200;
		int iter = 1;

		BoundedSolver.BoundType type = BoundedSolver.BoundType.linear;

		if (config.containsKey("dse.bounds.step")) {
			bound = Integer.parseInt(config.getProperty("dse.bounds.step"));
		}
		if (config.containsKey("dse.bounds.iter")) {
			iter = Integer.parseInt(config.getProperty("dse.bounds.iter"));
		}
		if (config.containsKey("dse.bounds.type") && config.getProperty("dse.bounds.type").equals("fibonacci")) {
			type = BoundedSolver.BoundType.fibonacci;
		}

		ConstraintSolver solver = ConstraintSolverFactory.createSolver(dp, config);
		return new BoundedSolver(solver, bound, iter, type);
	}
}
