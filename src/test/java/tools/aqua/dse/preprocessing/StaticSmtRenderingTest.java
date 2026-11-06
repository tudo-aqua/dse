package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** SMT rendering of the static class information, in particular for empty relations. */
public class StaticSmtRenderingTest {

    private static Result solve(String smt) throws Exception {
        SolverContext ctx = ConstraintSolverFactory.createSolver("z3", new Properties()).createContext();
        SmtProblemManager.addSmtProblemAsString(smt, ctx);
        return ctx.solve(new Valuation());
    }

    @Test
    void emptyPolymorphismRelationIsFalse() throws Exception {
        // classes without methods (e.g. the generated scaling benchmarks) yield an empty table
        String smt = Opal.generatePolymorphismSummaryInternal(List.of());
        assertThat(SMTLIBParser.parseSMTProgram(smt).assertions).isNotEmpty();
        assertThat(solve(smt + "(assert (obj.method.of \"LA;\" \"m\" \"()V\" \"LA;\"))")).isEqualTo(Result.UNSAT);
    }

    @Test
    void emptyExtendsRelationIsFalse() throws Exception {
        String smt = Opal.renderExtendsSummary(List.of());
        assertThat(SMTLIBParser.parseSMTProgram(smt).assertions).isNotEmpty();
        assertThat(solve(smt + "(assert (obj.extends \"LA;\" \"LA;\"))")).isEqualTo(Result.UNSAT);
    }

    @Test
    void nonEmptyRelationsAreUnchanged() throws Exception {
        String smt = Opal.renderExtendsSummary(List.<String[]>of(new String[] {"LB;", "LA;"}))
                + Opal.generatePolymorphismSummaryInternal(
                        List.of(new Opal.PolymorphyInformation("LB;", "m", "()V", "LA;")));
        assertThat(solve(smt + "(assert (obj.extends \"LB;\" \"LA;\")) (assert (obj.method.of \"LB;\" \"m\" \"()V\" \"LA;\"))"))
                .isEqualTo(Result.SAT);
        assertThat(solve(smt + "(assert (obj.extends \"LA;\" \"LB;\"))")).isEqualTo(Result.UNSAT);
    }
}
