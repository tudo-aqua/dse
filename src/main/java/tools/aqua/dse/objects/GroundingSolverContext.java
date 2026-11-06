package tools.aqua.dse.objects;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.preprocessing.ObjectRelationGrounding;

import java.util.List;

/**
 * Replaces applications of {@code obj.extends} and {@code obj.method.of} in all added constraints by their
 * quantifier-free instantiation (see {@link ObjectRelationGrounding}). Without a grounding, constraints are passed
 * on unchanged (e.g., if the relations are defined by quantified formulas).
 */
public class GroundingSolverContext extends SolverContext {
    private final SolverContext context;
    private ObjectRelationGrounding grounding;

    public GroundingSolverContext(SolverContext context) {
        this.context = context;
    }

    public void setGrounding(ObjectRelationGrounding grounding) {
        this.grounding = grounding;
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
        context.add(grounding == null ? list : grounding.ground(list));
    }

    @Override
    public void dispose() {
        context.dispose();
    }
}
