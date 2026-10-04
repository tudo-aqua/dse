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
        this(classPath, depth, false);
    }

    public SmtProblemManager(String classPath, int depth, boolean useCache) {
        long start = System.currentTimeMillis();
        this.staticManager = new StaticManager(classPath, depth);
        constructorSummaryManager = new ConstructorSummaryManager(classPath, depth, useCache);
        long end = System.currentTimeMillis();
        System.out.println("set-up-time: " + (end - start) + "ms");
    }

    /**
     * Incremental mode: constructor summaries are computed on demand (see
     * {@link ConstructorSummaryManager#startQuery(java.util.List)}).
     */
    public SmtProblemManager(String classPath, int depth, ConstructorSummaryManager.IncrementalOptions options) {
        long start = System.currentTimeMillis();
        this.staticManager = new StaticManager(classPath, depth);
        constructorSummaryManager = new ConstructorSummaryManager(classPath, depth, staticManager, options);
        long end = System.currentTimeMillis();
        System.out.println("set-up-time: " + (end - start) + "ms");
    }

    public StaticManager getStaticManager() {
        return staticManager;
    }

    public ConstructorSummaryManager getConstructorSummaryManager() {
        return constructorSummaryManager;
    }

    /** queries larger than this are printed truncated (eager summaries of large libraries reach gigabytes of output) */
    private static final int MAX_PRINTED_SMT = 20_000;

    public static void printSmtProblem(String smtLibCode) {
        System.out.println("SMT-Problem-Start");
        String printed = smtLibCode.length() <= MAX_PRINTED_SMT ? smtLibCode
                : smtLibCode.substring(0, MAX_PRINTED_SMT) + "\n... (truncated, " + smtLibCode.length() + " characters)";
        System.out.printf("\u001B[38;5;208m %s \u001B[0m%n", printed);
        System.out.println("SMT-Problem-End");
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
