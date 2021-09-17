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
			bound = Integer.parseInt(config.getProperty("bounded.bound"));
		}
		if (config.containsKey("dse.bounds.iter")) {
			iter = Integer.parseInt(config.getProperty("bounded.iter"));
		}
		if (config.containsKey("dse.bounds.type") && config.getProperty("dse.bound.type").equals("fibonacci")) {
			type = BoundedSolver.BoundType.fibonacci;
		}

		ConstraintSolver solver = ConstraintSolverFactory.createSolver(dp, config);
		return new BoundedSolver(solver, bound, iter, type);
	}
}
