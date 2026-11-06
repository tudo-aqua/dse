package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tools.aqua.dse.objects.GroundingSolverContext;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static tools.aqua.dse.preprocessing.SummaryRelevanceTest.*;

/** The grounded encoding of obj.extends / obj.method.of agrees with the quantified one (Fig. 2 hierarchy). */
public class ObjectRelationGroundingTest {

    private static final ObjectRelationGrounding GROUNDING = ObjectRelationGrounding.of(EXTENDS, METHODS);
    /** kept: if the solver is garbage-collected, it closes the Z3 context */
    private ConstraintSolver solver;
    private SolverContext quantified;
    private GroundingSolverContext grounded;

    @BeforeClass
    void createSolvers() {
        solver = ConstraintSolverFactory.createSolver("z3", new Properties());
        quantified = solver.createContext();
        SmtProblemManager.addSmtProblemAsString("(declare-fun null () Int)\n(assert (= null 0))\n"
                + Opal.renderExtendsSummary(EXTENDS) + "\n" + Opal.generatePolymorphismSummaryInternal(METHODS), quantified);
        grounded = new GroundingSolverContext(solver.createContext());
        grounded.setGrounding(GROUNDING);
        SmtProblemManager.addSmtProblemAsString("(declare-fun null () Int)\n(assert (= null 0))", grounded);
    }

    private Result solve(SolverContext ctx, List<Expression<Boolean>> constraints) {
        ctx.push();
        try {
            ctx.add(constraints);
            return ctx.solve(new Valuation());
        } finally {
            ctx.pop();
        }
    }

    private void assertSame(Result expected, String... constraints) throws Exception {
        List<Expression<Boolean>> path = path(constraints);
        assertThat(solve(quantified, path)).as("quantified").isEqualTo(expected);
        assertThat(solve(grounded, path)).as("grounded").isEqualTo(expected);
    }

    @Test
    void groundsExtendsToTheSubtypes() throws Exception {
        Expression<Boolean> e = GROUNDING.ground(path(BOUND_A).get(0));
        assertThat(e.toString()).doesNotContain("obj.extends").contains("LD;").contains("null");
        assertSame(Result.SAT, BOUND_A, "(= __object_0.cls \"LC;\")");
        assertSame(Result.UNSAT, "(obj.extends __object_0.cls \"LB;\")", "(= __object_0.cls \"LA;\")");
        assertSame(Result.UNSAT, BOUND_A, INSTANCEOF_B, NOT_INSTANCEOF_B);
        assertSame(Result.SAT, BOUND_A, NOT_INSTANCEOF_B, "(not (= __object_0 null))");
    }

    @Test
    void groundsDispatch() throws Exception {
        assertSame(Result.SAT, FOO_IS_D);
        assertSame(Result.UNSAT, FOO_IS_D, "(= __object_0.cls \"LC;\")");
        assertSame(Result.SAT, "(obj.method.of __object_0.cls \"foo\" \"()V\" \"LB;\")", "(= __object_0.cls \"LC;\")");
        assertSame(Result.UNSAT, "(obj.method.of __object_0.cls \"bar\" \"()V\" \"LB;\")");
    }

    @Test
    void groundsConstantAndSymbolicArguments() throws Exception {
        assertSame(Result.SAT, "(obj.extends \"LC;\" \"LA;\")");
        assertSame(Result.UNSAT, "(obj.extends \"LA;\" \"LC;\")");
        // both arguments symbolic: one disjunct per pair
        assertSame(Result.SAT, "(obj.extends __object_0.cls __object_1.cls)", "(= __object_1.cls \"LB;\")",
                "(not (= __object_0.cls __object_1.cls))", "(not (= __object_0.cls \"null\"))");
        assertSame(Result.UNSAT, "(obj.extends __object_0.cls __object_1.cls)", "(= __object_0.cls \"LA;\")",
                "(= __object_1.cls \"LB;\")");
    }

    @Test
    void classesOnlyInSummariesCanBeAdded() throws Exception {
        ObjectRelationGrounding g = ObjectRelationGrounding.of(new java.util.ArrayList<>(EXTENDS), METHODS);
        assertThat(g.knowsClass("LE;")).isFalse();
        assertThat(g.ground(path("(obj.extends __object_0.cls \"LA;\")").get(0)).toString()).doesNotContain("LE;");
        g.addExtends(List.of(new String[] {"LE;", "LE;"}, new String[] {"LE;", "LA;"}, new String[] {"null", "LE;"}));
        assertThat(g.knowsClass("LE;")).isTrue();
        assertThat(g.ground(path("(obj.extends __object_0.cls \"LA;\")").get(0)).toString()).contains("LE;");
    }
}
