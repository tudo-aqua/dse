package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;

import java.io.IOException;
import java.util.Set;

public class SmtProblemManager {
    private final StaticManager staticManager;
    private final ConstructorSummaryManager constructorSummaryManager;

    public SmtProblemManager(String classPath, int depth) {
        this.staticManager = new StaticManager(classPath);
        constructorSummaryManager = new ConstructorSummaryManager(classPath, depth);
    }

    public StaticManager getStaticManager() {
        return staticManager;
    }

    public ConstructorSummaryManager getConstructorSummaryManager() {
        return constructorSummaryManager;
    }

    public static void addSmtProblemAsString(String smtLibCode, SolverContext solverContext) {
        SMTProblem smtProblem = null;
        try {
            smtProblem = SMTLIBParser.parseSMTProgram(smtLibCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SMTLIBParserException e) {
            throw new RuntimeException(e);
        }
        smtProblem.addProblemToContext(solverContext);

    }



}
