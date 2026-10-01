package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static tools.aqua.dse.preprocessing.SummaryRelevanceTest.*;

/**
 * The refinement loop of incremental summaries ({@link ConstructorSummaryManager#solve}) with Z3, on the class
 * hierarchy of Fig. 2. Summaries are given (as the nested DSE on constructors would produce them), so that the test
 * runs without SPouT; the generator records which signatures are summarized.
 */
public class IncrementalSummariesTest {

    private static final String DECLS = String.join("\n",
            "(declare-fun __object_0 () Int)",
            "(declare-fun __object_0.cls () String)",
            "(declare-fun __object_0.err () String)",
            "(declare-fun __object_0.x () (_ BitVec 32))");

    private static final Map<String, String> SUMMARIES = Map.of(
            "null|NULL", "(and (= __object_0.init \"null|NULL\") (= __object_0 null) (= __object_0.cls \"null\") (= __object_0.err \"\"))",
            "LA;|()V|", "(and (= __object_0.init \"<>LA;|()V|\") (not (= __object_0 null)) (= __object_0.cls \"LA;\") (= __object_0.err \"\"))",
            "LB;|()V|", "(and (= __object_0.init \"<java/lang/AssertionError>LB;|()V|\") (not (= __object_0 null)) (= __object_0.cls \"LB;\") (= __object_0.err \"java/lang/AssertionError\"))",
            "LB;|(I)V|{}", "(and (= __object_0.init \"<>LB;|(I)V|{__object_0__int_0}\") (not (= __object_0 null)) (= __object_0.cls \"LB;\") (= __object_0.x __object_0__int_0) (= __object_0.err \"\"))",
            "LC;|()V|", "(and (= __object_0.init \"<>LC;|()V|\") (not (= __object_0 null)) (= __object_0.cls \"LC;\") (= __object_0.x #x00000000) (= __object_0.err \"\"))",
            "LD;|(I)V|{}", "(and (= __object_0.init \"<>LD;|(I)V|{__object_0__int_0}\") (not (= __object_0 null)) (= __object_0.cls \"LD;\") (= __object_0.x __object_0__int_0) (= __object_0.err \"\"))");

    private final List<String> generated = new ArrayList<>();
    private ConstructorSummaryManager manager;
    private SolverContext ctx;

    @BeforeMethod
    void setUp() {
        generated.clear();
        SummaryStore store = new SummaryStore(SIGNATURES, sig -> {
            generated.add(sig);
            return new GeneratedSummary(sig, List.of(SUMMARIES.get(sig)),
                    Set.of(DECLS, "(declare-fun __object_0__int_0 () (_ BitVec 32))"), List.of());
        });
        manager = new ConstructorSummaryManager(store, new SummaryRelevance(facts(ERRORS)),
                List.of("java/lang/AssertionError"), 1);

        ConstraintSolver solver = ConstraintSolverFactory.createSolver("z3", new Properties());
        ctx = solver.createContext();
        ctx.push();
        SmtProblemManager.addSmtProblemAsString("(declare-fun null () Int)\n(assert (= null 0))\n"
                + Opal.renderExtendsSummary(EXTENDS) + "\n"
                + Opal.generatePolymorphismSummaryInternal(METHODS), ctx);
    }

    private Result solve(Valuation val, String... decisions) throws Exception {
        return manager.solve(ctx, path(decisions), val);
    }

    @Test
    void startsWithTheNullSummaryOnly() throws Exception {
        assertThat(generated).containsExactly("null|NULL");
        Valuation val = new Valuation();
        assertThat(solve(val, BOUND_A, NO_ERROR, NOT_INSTANCEOF_B)).isEqualTo(Result.SAT);
        assertThat(val.getValue("__object_0.init")).isEqualTo("null|NULL");
        assertThat(generated).containsExactly("null|NULL");
    }

    @Test
    void summarizesUntilSat() throws Exception {
        // B() only raises an assertion error -> UNSAT with it; C() is next in order and satisfies the path
        Valuation val = new Valuation();
        assertThat(solve(val, BOUND_A, NO_ERROR, INSTANCEOF_B)).isEqualTo(Result.SAT);
        assertThat(generated).containsExactly("null|NULL", "LB;|()V|", "LC;|()V|");
        assertThat(val.getValue("__object_0.init")).isEqualTo("<>LC;|()V|");
    }

    @Test
    void summariesAreReused() throws Exception {
        assertThat(solve(new Valuation(), BOUND_A, NO_ERROR, INSTANCEOF_B)).isEqualTo(Result.SAT);
        // field constraint: C() sets x = 0, so B(int) is needed next
        Valuation val = new Valuation();
        assertThat(solve(val, BOUND_A, NO_ERROR, INSTANCEOF_B, X_POSITIVE)).isEqualTo(Result.SAT);
        assertThat(generated).containsExactly("null|NULL", "LB;|()V|", "LC;|()V|", "LB;|(I)V|{}");
        assertThat(val.getValue("__object_0.init")).isEqualTo("<>LB;|(I)V|{__object_0__int_0}");
    }

    @Test
    void errorBranchSummarizesOnlyConstructorsRaisingTheError() throws Exception {
        Valuation val = new Valuation();
        assertThat(solve(val, BOUND_A, ASSERTION)).isEqualTo(Result.SAT);
        assertThat(generated).containsExactly("null|NULL", "LB;|()V|");
        assertThat(val.getValue("__object_0.init")).isEqualTo("<java/lang/AssertionError>LB;|()V|");
    }

    @Test
    void staticallyExcludedPathsAreUnsatWithoutSummaries() throws Exception {
        assertThat(solve(new Valuation(), BOUND_A, NO_ERROR, INSTANCEOF_B, NOT_INSTANCEOF_B)).isEqualTo(Result.UNSAT);
        assertThat(generated).containsExactly("null|NULL");
    }

    @Test
    void unsatIsReportedOnlyAfterAllNeededSummaries() throws Exception {
        // only D(int) dispatches foo to D::foo; x == 5 and x == 6 is unsatisfiable for it
        assertThat(solve(new Valuation(), BOUND_A, NO_ERROR, FOO_IS_D,
                "(= __object_0.x #x00000005)", "(= __object_0.x #x00000006)")).isEqualTo(Result.UNSAT);
        assertThat(generated).containsExactly("null|NULL", "LD;|(I)V|{}");
    }

    @Test
    void generatedSmtIsParsable() throws Exception {
        manager.solve(ctx, path(BOUND_A, NO_ERROR, INSTANCEOF_B), new Valuation());
        ConstructorSummaryManager.Query query = manager.startQuery(path(BOUND_A, NO_ERROR, INSTANCEOF_B));
        String smt = manager.generateIncrementalSMTLibCode(query);
        assertThat(smt).contains("(declare-fun __object_0.init () String)");
        // only relevant summaries are asserted: B() and C() are summarized and relevant, null is not relevant
        assertThat(smt).contains("<>LC;|()V|").contains("<java/lang/AssertionError>LB;|()V|").doesNotContain("null|NULL");
        assertThat(SMTLIBParser.parseSMTProgram(smt).assertions).isNotEmpty();
    }
}
