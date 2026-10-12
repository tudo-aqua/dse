package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisions on {@code o.err} with the static error list of incremental mode. */
public class StaticErrorDecisionTest {

    private static final String DECL = "(declare-fun __object_3.err () String)";
    private static final List<String> ERRORS = List.of("java/lang/ArithmeticException", "java/lang/AssertionError");

    private static Decision decision(String error) throws Exception {
        String constraint = "(assert (= __object_3.err \"" + error + "\"))";
        SMTProblem smt = SMTLIBParser.parseSMTProgram(DECL + constraint);
        return TraceParser.parseErrorDecisionWithStaticErrors("__object_3", error, ERRORS, DECL, smt);
    }

    private static boolean holdsFor(Decision d, String error) {
        Valuation val = new Valuation();
        val.setValue(Variable.create(BuiltinTypes.STRING, "__object_3.err"), error);
        return d.getCondition().evaluate(val);
    }

    @Test
    void branchCountIsStatic() throws Exception {
        for (String error : List.of("", "java/lang/AssertionError", "java/lang/IllegalStateException")) {
            assertThat(decision(error).getBranches()).isEqualTo(ERRORS.size() + 2);
        }
    }

    @Test
    void knownErrorsAndNoErrorHaveFixedIds() throws Exception {
        assertThat(decision("java/lang/ArithmeticException").getBranchId()).isEqualTo(0);
        assertThat(decision("java/lang/AssertionError").getBranchId()).isEqualTo(1);
        assertThat(decision("").getBranchId()).isEqualTo(3);
    }

    @Test
    void unpredictedErrorsUseTheOtherBranch() throws Exception {
        Decision other = decision("java/lang/IllegalStateException");
        assertThat(other.getBranchId()).isEqualTo(2);
        // the condition means "none of the known outcomes", not the observed error
        assertThat(holdsFor(other, "java/lang/IllegalStateException")).isTrue();
        assertThat(holdsFor(other, "java/lang/UnsupportedOperationException")).isTrue();
        assertThat(holdsFor(other, "java/lang/AssertionError")).isFalse();
        assertThat(holdsFor(other, "")).isFalse();
    }
}
