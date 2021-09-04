package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class TraceParserTest {

    @Test
    public void testTraceParser() throws IOException, SMTLIBParserException {
        List<String> log = new LinkedList<>();
        log.add("======================== START PATH [BEGIN].");
        log.add("Seeded Int Values: [0]");
        log.add("======================== START PATH [END].");
        log.add("Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException");
        log.add("       at Test9.main(Test9.java:9)");
        log.add("======================== END PATH [BEGIN].");
        log.add("[DECLARE] (declare-fun __int_0 () Int)");
        log.add("[DECISION] (assert (<= 50 (+ 11 __int_0))) // branchCount=2, branchId=1");
        log.add("[ERROR] java.lang.ArrayIndexOutOfBoundsException");
        log.add("======================== END PATH [END].");

        Trace t = TraceParser.parseTrace(log, new Valuation());
        assert t != null;
        System.out.println(t);
    }

    @Test
    public void testDecisionParser() throws IOException, SMTLIBParserException {
        String decl = "(declare-fun __int_0 () Int)";
        String decision = "(assert (= __int_0 50)) // branchCount=2, branchId=0";
        Decision d = TraceParser.parseDecision(decision, decl);
        assert d != null;
        System.out.println(d);
    }
}
