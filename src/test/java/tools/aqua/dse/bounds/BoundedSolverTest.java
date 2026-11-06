package tools.aqua.dse.bounds;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.NumericBooleanExpression;
import gov.nasa.jpf.constraints.expressions.NumericComparator;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import org.testng.annotations.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundedSolverTest {

    private static ConstraintSolver solver(String scope, String step, String iter) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.bounds", "true");
        props.setProperty("dse.bounds.step", step);
        props.setProperty("dse.bounds.iter", iter);
        props.setProperty("dse.bounds.scope", scope);
        return new BoundedSolverProvider().createSolver(props);
    }

    private static Valuation solve(ConstraintSolver solver, Expression<Boolean>... constraints) {
        SolverContext ctx = solver.createContext();
        ctx.push();
        for (Expression<Boolean> c : constraints) {
            ctx.add(c);
        }
        Valuation val = new Valuation();
        assertThat(ctx.solve(val)).isEqualTo(ConstraintSolver.Result.SAT);
        ctx.dispose();
        return val;
    }

    /** longs are bounded as well: a model within the bound is preferred */
    @Test
    public void boundsLongs() {
        Variable<Long> x = Variable.create(BuiltinTypes.SINT64, "__long_0");
        Valuation val = solve(solver("all", "100", "1"),
                new NumericBooleanExpression(x, NumericComparator.GT, Constant.create(BuiltinTypes.SINT64, 5L)));
        assertThat(val.getValue(x)).isBetween(6L, 100L);
    }

    /** bounds are widened in steps, and dropped if no bounded model exists */
    @Test
    public void widensAndFallsBack() {
        Variable<Integer> x = Variable.create(BuiltinTypes.SINT32, "__int_0");
        Valuation widened = solve(solver("all", "100", "3"),
                new NumericBooleanExpression(x, NumericComparator.GT, Constant.create(BuiltinTypes.SINT32, 150)));
        assertThat((Integer) widened.getValue(x)).isBetween(151, 200);
        Valuation unbounded = solve(solver("all", "100", "3"),
                new NumericBooleanExpression(x, NumericComparator.GT, Constant.create(BuiltinTypes.SINT32, 100000)));
        assertThat((Integer) unbounded.getValue(x)).isGreaterThan(100000);
    }

    /** scope constructors: only parameters of constructors of concolic objects are bounded */
    @Test
    public void boundsOnlyConstructorParameters() {
        Variable<Integer> param = Variable.create(BuiltinTypes.SINT32, "__object_1__int_0");
        Variable<Integer> input = Variable.create(BuiltinTypes.SINT32, "__int_0");
        ConstraintSolver solver = solver("constructors", "10", "1");
        Valuation val = solve(solver,
                new NumericBooleanExpression(param, NumericComparator.GT, Constant.create(BuiltinTypes.SINT32, 5)),
                new NumericBooleanExpression(input, NumericComparator.GT, Constant.create(BuiltinTypes.SINT32, 1000)));
        assertThat((Integer) val.getValue(param)).isBetween(6, 10);
        assertThat((Integer) val.getValue(input)).isGreaterThan(1000);
    }
}
