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
import java.util.regex.Pattern;

public class BoundedSolver extends ConstraintSolver {

	private final ConstraintSolver back;

	private final int itr;

	private final int bound;

	private final int fibonacci[] =
			{1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765};

	public enum BoundType {
		linear, fibonacci
	}

	/**
	 * Variables that are bounded: all, or only the parameters of constructors of concolic objects
	 * ({@code __object_<k>__<type>_<i>}, e.g. capacities of collections, which otherwise are often chosen huge).
	 */
	public enum Scope {
		all, constructors
	}

	private static final Pattern CONSTRUCTOR_PARAMETER = Pattern.compile("__object_\\d+__.*");

	BoundType type;

	private final Scope scope;

	public BoundedSolver(ConstraintSolver back, int bound, int itr, BoundType type) {
		this(back, bound, itr, type, Scope.all);
	}

	public BoundedSolver(ConstraintSolver back, int bound, int itr, BoundType type, Scope scope) {
		this.back = back;
		this.bound = bound;
		this.itr = itr;
		this.type = type;
		this.scope = scope;
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

	/** the bounds of the {@code index}-th attempt (widening with the index, linearly or by the Fibonacci sequence) */
	public Expression<Boolean> getBound(Expression<Boolean> expr, int index) {
		int resultingBound = index * bound;

		if (type == BoundType.fibonacci) {
			resultingBound = fibonacci[Math.min(index, fibonacci.length - 1)];
		}

		return getBoundExpression(expr, resultingBound);
	}

	private boolean inScope(Variable<?> v) {
		return scope == Scope.all || CONSTRUCTOR_PARAMETER.matcher(v.getName()).matches();
	}

	/**
	 * Bounds for the variables of {@code e}: integers of all widths in {@code [-bound, bound]} (limited to the range
	 * of their type), lengths of strings up to {@code 10 * bound}, finite floating-point values.
	 */
	private Expression<Boolean> getBoundExpression(Expression<Boolean> e, int bound) {
		Expression<Boolean> ret = ExpressionUtil.TRUE;
		for (Variable v : ExpressionUtil.freeVariables(e)) {
			if (!inScope(v)) {
				continue;
			}
			Expression<Boolean> b = boundOf(v, bound);
			if (b != null) {
				ret = ExpressionUtil.and(ret, b);
			}
		}
		return ret;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Expression<Boolean> boundOf(Variable v, int bound) {
		Constant low = null;
		Constant high = null;
		if (v.getType().equals(BuiltinTypes.SINT8)) {
			int b = Math.min(bound, Byte.MAX_VALUE);
			low = Constant.create(BuiltinTypes.SINT8, (byte) -b);
			high = Constant.create(BuiltinTypes.SINT8, (byte) b);
		} else if (v.getType().equals(BuiltinTypes.SINT16)) {
			int b = Math.min(bound, Short.MAX_VALUE);
			low = Constant.create(BuiltinTypes.SINT16, (short) -b);
			high = Constant.create(BuiltinTypes.SINT16, (short) b);
		} else if (v.getType().equals(BuiltinTypes.UINT16)) {
			// chars: non-negative
			low = Constant.create(BuiltinTypes.UINT16, (char) 0);
			high = Constant.create(BuiltinTypes.UINT16, (char) Math.min(bound, Character.MAX_VALUE));
		} else if (v.getType().equals(BuiltinTypes.SINT32)) {
			low = Constant.create(BuiltinTypes.SINT32, -bound);
			high = Constant.create(BuiltinTypes.SINT32, bound);
		} else if (v.getType().equals(BuiltinTypes.SINT64)) {
			low = Constant.create(BuiltinTypes.SINT64, (long) -bound);
			high = Constant.create(BuiltinTypes.SINT64, (long) bound);
		}
		if (low != null) {
			return ExpressionUtil.and(new NumericBooleanExpression(low, NumericComparator.LE, v),
					new NumericBooleanExpression(v, NumericComparator.LE, high));
		}
		if (v.getType().equals(BuiltinTypes.STRING)) {
			Expression<BigInteger> strLen = StringIntegerExpression.createLength(v);
			Constant<BigInteger> upperBound = Constant.create(BuiltinTypes.INTEGER, BigInteger.valueOf(bound * 10L));
			return new NumericBooleanExpression(strLen, NumericComparator.LE, upperBound);
		}
		if (v.getType().equals(BuiltinTypes.DOUBLE) || v.getType().equals(BuiltinTypes.FLOAT)) {
			Expression<Boolean> bound1 = new Negation(new FloatingPointBooleanExpression(FPComparator.FP_IS_NAN, v));
			Expression<Boolean> bound2 = new Negation(new FloatingPointBooleanExpression(FPComparator.FP_IS_INFINITE, v));
			return ExpressionUtil.and(bound1, bound2);
		}
		return null;
	}

	/** @return whether some variable of {@code e} is bounded */
	public boolean isBoundable(Expression<Boolean> e){
		for (Variable v : ExpressionUtil.freeVariables(e)) {
			if (inScope(v) && boundOf(v, 1) != null) {
				return true;
			}
		}
		return false;
	}
}
