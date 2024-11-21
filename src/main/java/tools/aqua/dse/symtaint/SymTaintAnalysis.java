package tools.aqua.dse.symtaint;


import com.google.common.base.Function;
import com.sun.org.apache.xpath.internal.operations.Bool;
import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.*;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import tools.aqua.dse.Config;
import tools.aqua.dse.trace.Decision;

import java.util.*;

public class SymTaintAnalysis {

    private boolean valid = true;

    private final List<Expression<Boolean>> paths = new LinkedList<>();

    private final SolverContext solverCtx;

    private HashSet<Variable<?> >refCheckVars = new HashSet<>();

    public SymTaintAnalysis(Config config) {
        this.solverCtx = config.getSolverContext();
    }

    public void addPath(List<Decision> pc, Expression<Boolean> check) {
        if (check == null || !hasTaintCheck(check)) {
            invalidate();
        } else {
            Expression<Boolean> expr = ExpressionUtil.TRUE;
            for (Decision d : pc) {
                expr = ExpressionUtil.and(expr, d.getCondition());
            }
            paths.add(ExpressionUtil.and(expr, check));
        }
    }

    private boolean hasTaintCheck(Expression<Boolean> check) {
        List<Variable<?>> v = new ArrayList<>();
        for (Variable<?> it : ExpressionUtil.freeVariables(check)) {
            if (it.getName().contains("taint")) {
                v.add(it);
            }
        }

        refCheckVars.addAll(v);
        return !v.isEmpty();
    }

    public void invalidate() {
        this.valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public void analyze(boolean ni) {
        if (paths.isEmpty()) {
            System.out.println("No paths to analyze");
            return;
        }

        for (Variable<?> v : refCheckVars) {
            if (!v.getType().equals(BuiltinTypes.STRING) &&
                    !v.getType().equals(BuiltinTypes.BOOL) &&
                    !v.getType().equals(BuiltinTypes.DOUBLE) &&
                    !v.getType().equals(BuiltinTypes.FLOAT) &&
                    !v.getType().equals(BuiltinTypes.SINT8) &&
                    !v.getType().equals(BuiltinTypes.SINT16) &&
                    !v.getType().equals(BuiltinTypes.SINT32) &&
                    !v.getType().equals(BuiltinTypes.SINT64)) {
                System.out.println("Unsupported type for symbolic taint: " + v.getType());
                return;
            }
        }

        Expression<Boolean> values = uniqueVars(paths.get(0), 0);
        Expression<Boolean> eqs = ExpressionUtil.FALSE;
        int idx = 1;

        for (Expression<Boolean> p : paths) {
            values = ExpressionUtil.and(values, uniqueVars(p, idx));
            // todo: check the type and create correct expression!
            for (Variable<?> v : refCheckVars) {
                eqs = ExpressionUtil.or(eqs, new Negation(check(
                        new Variable(v.getType(), "p0" + v.getName()),
                        new Variable(v.getType(), "p" + idx + v.getName()))));
            }
            idx++;
        }
        Expression<Boolean> test = ExpressionUtil.and(values, eqs);
        System.out.println("Symbolic Interference Check:");
        System.out.println(test);
        ConstraintSolver.Result res = solverCtx.isSatisfiable(test);
        switch (res) {
            case SAT:
                System.out.println("SYMBOLIC IF: found interference");
                break;
            case UNSAT:
                if (!ni) {
                    System.out.println("SYMBOLIC IF: did not find interference");
                } else {
                    System.out.println("SYMBOLIC IF: proved NI");
                }
                break;
            default:
                System.out.println("SYMBOLIC IF: no verdict from SMT solver");
        }
    }

    private Expression<Boolean> check(Variable v1, Variable v2) {
        if (v1.getType().equals(BuiltinTypes.STRING)) {
            return new StringBooleanExpression(v1, StringBooleanOperator.EQUALS, v2);
        } else if (v1.getType().equals(BuiltinTypes.BOOL)) {
            return new PropositionalCompound(v1, LogicalOperator.EQUIV, v2);
        } else {
            return new NumericBooleanExpression(v1, NumericComparator.EQ, v2);
        }
    }

    private Expression<Boolean> uniqueVars(Expression<Boolean> e, final int idx) {
        Function<String,String> rename = new Function<String,String>() {
            @Override
            public @Nullable String apply(@Nullable String s) {
                return "p" + idx + s;
            }
        };
        return ExpressionUtil.renameVars(e, rename);
    }
}
