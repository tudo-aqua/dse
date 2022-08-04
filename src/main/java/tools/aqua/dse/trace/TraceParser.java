/*
Copyright [yyyy] [name of copyright owner]

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
*/

package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.paths.PathResult;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class TraceParser {

    public static Trace parseTrace(List<String> lines, Valuation vals) throws IOException, SMTLIBParserException {
        List<Decision> decisions = new LinkedList<>();
        List<WitnessAssumption> witness = new LinkedList<>();
        List<String> taintViolations = new LinkedList<>();
        List<String> flows = new LinkedList<>();
        PathResult result = PathResult.ok(vals);
        String decl = "";
        boolean traceComplete = false;
        for (String line : lines) {
            if (line.startsWith("[DECISION]")) {
                decisions.add(parseDecision( line.substring("[DECISION]".length()), decl));
            }
            else if (line.startsWith("[DECLARE]")) {
                decl += line.substring("[DECLARE]".length());
            }
            else if (line.startsWith("[ERROR]")) {
                result = PathResult.error(vals, line.substring("[ERROR]".length()).trim(), "");
            }
            else if (line.startsWith("[TAINT VIOLATION]")) {
                //result = PathResult.error(vals, line.trim(), "");
                //TODO: not sure what we should do in this case?
                System.out.println(line.trim());
                taintViolations.add(line.substring("[TAINT VIOLATION]".length()).trim());
            }
            else if (line.startsWith("[ABORT]")) {
                result = PathResult.abort(vals, line.substring("[ABORT]".length()).trim());
            }
            else if (line.startsWith("[ASSUMPTION]")) {
                decisions.add(parseAssumption( line.substring("[ASSUMPTION]".length()), decl));
            }
            else if (line.startsWith("[WITNESS]")) {
                witness.add(parseWitnessAssumption( line.substring("[WITNESS]".length()).trim() ));
            }
            else if (line.startsWith("[FLOW]")) {
                flows.add( line.substring("[FLOW]".length()).trim() );
            }
            else if (line.startsWith("[TAINTCHECK]")) {
                flows.add( line.substring("[TAINTCHECK]".length()).trim() );
            }
            else if (line.startsWith("[ENDOFTRACE]")) {
                traceComplete = true;
            }
        }

        // TODO: maybe we could do better here if we have received half a trace?
        if (!traceComplete) {
            return null;
        }

        result.setTaintViolations(taintViolations);
        return new Trace(decisions, witness, flows, result);
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
    }

    private static WitnessAssumption parseWitnessAssumption(String data) {
        String[] parts = data.split("\\:", 4);
        return new WitnessAssumption(parts[3].trim(), parts[0].trim(), parts[1].trim(), Integer.parseInt(parts[2].trim()));
    }

}
