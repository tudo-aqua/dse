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


import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.*;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import org.testng.annotations.Test;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.util.Collections;
import java.util.Properties;

public class ExplorerTest {

    @Test
    public void testExplorerTwoMinimalTraces() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "dummy");
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
        PathResult pr1 = new PathResult.OkResult(v1);
        Trace t1 = new Trace(Collections.singletonList(d1), pr1);
        e.addTrace(t1);

        assert e.hasNextValuation();

        Valuation v2 = e.getNextValuation();
        PathResult pr2 = new PathResult.OkResult(v2);
        Trace t2 = new Trace(Collections.singletonList(d2), pr2);
        e.addTrace(t2);

        assert !e.hasNextValuation();

        System.out.println(e.getAnalysis());
    }

    @Test
    public void testExplorerTwoMinimalTracesWithError() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "dummy");
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
        PathResult pr1 = new PathResult.OkResult(v1);
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
        props.setProperty("dse.executor", "dummy");
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
        PathResult pr1 = new PathResult.OkResult(v1);
        Trace t1 = new Trace(Collections.singletonList(d1), pr1);
        e.addTrace(t1);

        assert e.hasNextValuation();

        Valuation v2 = e.getNextValuation();
        assert v2.getValue(v).equals(0);
        PathResult pr2 = new PathResult.OkResult(v2);
        Trace t2 = new Trace(Collections.singletonList(d2), pr2);
        e.addTrace(t2);

        assert e.hasNextValuation();

        Valuation v3 = e.getNextValuation();
        PathResult pr3 = new PathResult.OkResult(v3);
        Trace t3 = new Trace(Collections.singletonList(d3), pr3);
        e.addTrace(t3);

        assert !e.hasNextValuation();

        System.out.println(e.getAnalysis());
    }


}
