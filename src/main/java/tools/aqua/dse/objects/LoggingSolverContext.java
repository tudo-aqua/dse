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
        return context.solve(valuation);
    }

    @Override
    public void add(List<Expression<Boolean>> list) {
        for (Expression<Boolean> booleanExpression : list) {
            System.out.println(booleanExpression);
        }
        context.add(list);
    }

    @Override
    public void dispose() {
        context.dispose();
    }
}
