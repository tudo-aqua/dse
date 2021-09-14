package tools.aqua.dse.trace;

import com.sun.org.apache.xpath.internal.operations.Bool;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.paths.PostCondition;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class TraceParser {

    public static Trace parseTrace(List<String> lines, Valuation vals) throws IOException, SMTLIBParserException {
        List<Decision> decisions = new LinkedList<>();
        PathResult result = PathResult.ok(vals, new PostCondition());
        String decl = "";
        boolean traceComplete = false;
        for (String line : lines) {
            if (line.startsWith("[DECISION]")) {
                decisions.add(parseDecision( line.substring("[DECISION]".length()), decl));
            }
            if (line.startsWith("[DECLARE]")) {
                decl += line.substring("[DECLARE]".length());
            }
            else if (line.startsWith("[ERROR]")) {
                result = PathResult.error(vals, line.substring("[ERROR]".length()).trim(), "");
            }
            else if (line.startsWith("[ABORT]")) {
                result = PathResult.abort(vals, line.substring("[ABORT]".length()).trim());
            }
            else if (line.startsWith("[ASSUMPTION]")) {
                decisions.add(parseAssumption( line.substring("[ASSUMPTION]".length()), decl));
            }
            else if (line.startsWith("[ENDOFTRACE]")) {
                traceComplete = true;
            }
        }

        // TODO: maybe we could do better here if we have received half a trace?
        if (!traceComplete) {
            return null;
        }

        return new Trace(decisions, result);
    }



    public static Decision parseDecision(String decision, String decl) throws IOException, SMTLIBParserException {
        String[] parts = decision.split("\\/\\/ branchCount=|, branchId=");
        SMTProblem smt = null;
        try {
            smt = SMTLIBParser.parseSMTProgram(decl + parts[0]);
        } catch (Throwable e) {
            System.err.println("Could not parse: " + decl + parts[0]);
            throw e;
        }
        int branches = Integer.parseInt(parts[1]);
        int branchId = Integer.parseInt(parts[2]);
        return new Decision( ExpressionUtil.and(smt.assertions), branches, branchId);
    }


    public static Decision parseAssumption(String assumption, String decl) throws IOException, SMTLIBParserException {
        String[] parts = assumption.split("\\/\\/ sat=");
        SMTProblem smt = null;
        try {
            smt = SMTLIBParser.parseSMTProgram(decl + parts[0]);
        } catch (Throwable e) {
            System.err.println("Could not parse: " + decl + parts[0]);
            throw e;
        }
        boolean sat = Boolean.parseBoolean(parts[1]);
        return new Decision( ExpressionUtil.and(smt.assertions), 2, sat ? 1 : 0, true);
    }}
