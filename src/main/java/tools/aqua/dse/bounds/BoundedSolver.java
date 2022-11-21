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
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.*;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.redistribution.org.smtlib.sexpr.Sexpr;

import java.math.BigInteger;

public class BoundedSolver extends ConstraintSolver {

	private final ConstraintSolver back;

	private final int itr;

	private final int bound;

	private final int fibonacci[] =
			{1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765};

	public enum BoundType {
		linear, fibonacci
	}

	BoundType type;

	public BoundedSolver(ConstraintSolver back, int bound, int itr, BoundType type) {
		this.back = back;
		this.bound = bound;
		this.itr = itr;
		this.type = type;
	}

  @Override
  public Result solve(Expression<Boolean> exprsn, Valuation vltn) {

    Result res = null;
    if (isBoundable(exprsn)) {
      for (int i = 1; i <= itr; i++) {
        res = back.solve(ExpressionUtil.and(exprsn, getBound(exprsn, i)), vltn);
        if (res == Result.SAT) {
          return res;
        }
      }
    }
    return back.solve(exprsn, vltn);
  }

	@Override
	public BoundedSolverContext createContext() {
		SolverContext ctx = back.createContext();
		return new BoundedSolverContext(ctx, bound, itr, this);
	}

	public Expression<Boolean> getBound(Expression<Boolean> expr, int index) {
		int resultingBound = index * bound;

		if (type == BoundType.fibonacci) {
			resultingBound = fibonacci[index];
		}

		return getBoundExpression(expr, resultingBound);
	}

	private static Expression<Boolean> getBoundExpression(Expression<Boolean> e, int bound) {

		Constant low = new Constant(BuiltinTypes.SINT32, -bound);
		Constant high = new Constant(BuiltinTypes.SINT32, bound);
		Expression<Boolean> ret = ExpressionUtil.TRUE;
		for (Variable v : ExpressionUtil.freeVariables(e)) {
			if (v.getType().equals(BuiltinTypes.SINT32)) {
				Expression<Boolean> lower = new NumericBooleanExpression(low, NumericComparator.LE, v);
				Expression<Boolean> upper = new NumericBooleanExpression(v, NumericComparator.LE, high);
				ret = ExpressionUtil.and(ret, lower, upper);
			}
			if(v.getType().equals(BuiltinTypes.STRING)){
				Expression<BigInteger> strLen = StringIntegerExpression.createLength(v);
				Constant<BigInteger> upperBound = Constant.create(BuiltinTypes.INTEGER, BigInteger.valueOf(bound*10));
				Expression<Boolean> upper = new NumericBooleanExpression(strLen, NumericComparator.LE, upperBound);
				ret = ExpressionUtil.and(ret, upper);
			}
			if(v.getType().equals(BuiltinTypes.DOUBLE)){
				Expression<Boolean> bound1 = new Negation(new FloatingPointBooleanExpression(FPComparator.FP_IS_NAN, v));
				Expression<Boolean> bound2 = new Negation(new FloatingPointBooleanExpression(FPComparator.FP_IS_INFINITE, v));
				ret = ExpressionUtil.and(ret, bound1, bound2);
			}
		}
		return ret;
	}

	public static boolean isBoundable(Expression<Boolean> e){
		for (Variable v : ExpressionUtil.freeVariables(e)) {
			if (v.getType().equals(BuiltinTypes.SINT32)) {
				return true;
			}
			if(v.getType().equals(BuiltinTypes.STRING)){
				return true;
			}
			if(v.getType().equals(BuiltinTypes.DOUBLE)){
				return true;
			}
		}
		return false;
	}
}
