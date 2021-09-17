package tools.aqua.dse;


import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.*;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import org.testng.TestNG;
import org.testng.annotations.Test;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.paths.PostCondition;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.sql.Types;
import java.util.Collections;
import java.util.Properties;

public class ExplorerTest {

    @Test
    public void testExplorerTwoMinimalTraces() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        Config config = Config.fromProperties(props);
        Explorer e = new Explorer(config);

        Variable v = Variable.create(BuiltinTypes.SINT32, "x");
        Constant c5 = Constant.create(BuiltinTypes.SINT32, 5);

        Expression<Boolean> cond1 = new NumericBooleanExpression(v, NumericComparator.LE, c5);
        Expression<Boolean> cond2 = new NumericBooleanExpression(v, NumericComparator.GT, c5);
        Decision d1 = new Decision(cond1, 2, 0);
        Decision d2 = new Decision(cond2, 2, 1);

        assert e.hasNextValuation();

        Valuation v1 = e.getNextValuation();
        PathResult pr1 = new PathResult.OkResult(v1, new PostCondition());
        Trace t1 = new Trace(Collections.singletonList(d1), pr1);
        e.addTrace(t1);

        assert e.hasNextValuation();

        Valuation v2 = e.getNextValuation();
        PathResult pr2 = new PathResult.OkResult(v2, new PostCondition());
        Trace t2 = new Trace(Collections.singletonList(d2), pr2);
        e.addTrace(t2);

        assert !e.hasNextValuation();

        System.out.println(e.getAnalysis());
    }

    @Test
    public void testExplorerTwoMinimalTracesWithError() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        Config config = Config.fromProperties(props);

        Explorer e = new Explorer(config);

        Variable v = Variable.create(BuiltinTypes.SINT32, "x");
        Constant c5 = Constant.create(BuiltinTypes.SINT32, 5);

        Expression<Boolean> cond1 = new NumericBooleanExpression(v, NumericComparator.LE, c5);
        Expression<Boolean> cond2 = new NumericBooleanExpression(v, NumericComparator.GT, c5);
        Decision d1 = new Decision(cond1, 2, 0);
        Decision d2 = new Decision(cond2, 2, 1);

        assert e.hasNextValuation();

        Valuation v1 = e.getNextValuation();
        PathResult pr1 = new PathResult.OkResult(v1, new PostCondition());
        Trace t1 = new Trace(Collections.singletonList(d1), pr1);
        e.addTrace(t1);

        assert e.hasNextValuation();

        Valuation v2 = e.getNextValuation();
        PathResult pr2 = new PathResult.ErrorResult(v2, AssertionError.class.getCanonicalName(), "dummy");
        Trace t2 = new Trace(Collections.singletonList(d2), pr2);
        e.addTrace(t2);

        assert !e.hasNextValuation();

        System.out.println(e.getAnalysis());
    }

    @Test
    public void testExplorerThreeBranches() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        Config config = Config.fromProperties(props);

        Explorer e = new Explorer(config);

        Variable v = Variable.create(BuiltinTypes.SINT32, "x");
        Constant c1 = Constant.create(BuiltinTypes.SINT32, 1);
        Constant c2 = Constant.create(BuiltinTypes.SINT32, 2);

        Expression<Boolean> cond1 = new NumericBooleanExpression(v, NumericComparator.EQ, c1);
        Expression<Boolean> cond2 = new NumericBooleanExpression(v, NumericComparator.EQ, c2);
        Expression<Boolean> cond3 = new Negation(ExpressionUtil.or(cond1, cond2));

        Decision d1 = new Decision(cond1, 3, 0);
        Decision d2 = new Decision(cond3, 3, 2);
        Decision d3 = new Decision(cond2, 3, 1);

        assert e.hasNextValuation();

        Valuation v1 = e.getNextValuation();
        PathResult pr1 = new PathResult.OkResult(v1, new PostCondition());
        Trace t1 = new Trace(Collections.singletonList(d1), pr1);
        e.addTrace(t1);

        assert e.hasNextValuation();

        Valuation v2 = e.getNextValuation();
        assert v2.getValue(v).equals(0);
        PathResult pr2 = new PathResult.OkResult(v2, new PostCondition());
        Trace t2 = new Trace(Collections.singletonList(d2), pr2);
        e.addTrace(t2);

        assert e.hasNextValuation();

        Valuation v3 = e.getNextValuation();
        PathResult pr3 = new PathResult.OkResult(v3, new PostCondition());
        Trace t3 = new Trace(Collections.singletonList(d3), pr3);
        e.addTrace(t3);

        assert !e.hasNextValuation();

        System.out.println(e.getAnalysis());
    }


}
