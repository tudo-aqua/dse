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

import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import java.util.ArrayList;
import java.util.List;


public class BoundedSolverContext extends SolverContext {

	private static class StackElement {
		final ArrayList<Expression<Boolean>> exprsn = new ArrayList<>();
	}

	private final SolverContext ctx;

	private final int bound;

	private final int itr;

	private final ArrayList<StackElement> dkStack = new ArrayList<>();

	private StackElement current;
	private BoundedSolver solver;

	public BoundedSolverContext(SolverContext ctx, int bound, int itr, BoundedSolver solver) {
		this.ctx = ctx;
		this.bound = bound;
		this.itr = itr;
		this.solver = solver;
	}

	@Override
	public void push() {
		ctx.push();
		current = new StackElement();
		dkStack.add(current);
	}

	@Override
	public void pop(int n) {
		for (int i = 0; i < n; i++) {
			current = dkStack.remove(dkStack.size() - 1);
		}
		ctx.pop(n);
	}

	@Override
	public Result solve(Valuation vltn) {
		return solveWithBound(vltn);
	}

	@Override
	public void add(List<Expression<Boolean>> list) {
		try {
			for (Expression<Boolean> e : list) {
				ctx.add(e);
				current.exprsn.add(e);
			}
		}catch (Throwable t){
			t.printStackTrace();
		}
	}

	@Override
	public void dispose() {
		ctx.dispose();
	}


	private Result solveWithBound(Valuation vals) {

		Expression all = ExpressionUtil.and(current.exprsn);
		for (StackElement s : dkStack) {
			all = ExpressionUtil.and(all, ExpressionUtil.and(s.exprsn));
		}

		Result res = null;
    if (BoundedSolver.isBoundable(all)) {
      for (int i = 1; i <= itr; i++) {
        Expression<Boolean> boundExpr = solver.getBound(all, i);
        ArrayList<Expression<Boolean>> bounds = new ArrayList<>();
        bounds.add(boundExpr);
        push();
        add(bounds);
        res = ctx.solve(vals);
		if(res == Result.ERROR){
			return res;
		}
        pop();
        if (res == Result.SAT) {
          return res;
        }
      }
    }
		return ctx.solve(vals);
	}

}
