package tools.aqua.dse.objects;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;

import java.util.List;

public class LoggingSolverContext extends SolverContext {
    private SolverContext context;

    public LoggingSolverContext(SolverContext context) {
        this.context = context;
    }

    @Override
    public void push() {
        context.push();
    }

    @Override
    public void pop(int i) {
        context.pop(i);
    }

    @Override
    public ConstraintSolver.Result solve(Valuation valuation) {
        long start = System.nanoTime();
        ConstraintSolver.Result result = context.solve(valuation);
        System.out.println("[solve] " + result + " in " + (System.nanoTime() - start) / 1_000_000 + "ms");
        return result;
    }

    @Override
    public void add(List<Expression<Boolean>> list) {
        for (Expression<Boolean> booleanExpression : list) {
            try {
                System.out.println("\u001B[33m" + truncate(booleanExpression.toString()) + "\u001B[0m");
            } catch (StackOverflowError e) {
                System.out.println("\u001B[33m(expression too deep to print)\u001B[0m");
            }
        }
        context.add(list);
    }

    /** constraints with all constructor summaries (eager mode) are huge; the log only needs their beginning */
    public static String truncate(String s) {
        int max = 2000;
        return s.length() <= max ? s : s.substring(0, max) + " ... (truncated, " + s.length() + " characters)";
    }

    @Override
    public void dispose() {
        context.dispose();
    }
}
