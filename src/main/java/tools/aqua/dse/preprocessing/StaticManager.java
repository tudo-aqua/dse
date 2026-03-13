package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import tools.aqua.dse.Config;

import java.io.IOException;
import java.util.List;

public class StaticManager {
    private final Opal opal;
    /**
     * Identifiers of all classes in the class path.
     */
    private final List<KlassIdentifier> klassIdentifiers;

    private final String staticSMTLibCode;


    public StaticManager(String classPath) {
        this.opal = new Opal(classPath);
        this.klassIdentifiers = this.opal.extractKlassesFromClassPath();
        this.staticSMTLibCode = generateStaticSmtLibCode();
    }

    public SolverContext setStaticSmtLibCode(SolverContext solverContext) {
        SMTProblem smtProblem = null;
        try {
            smtProblem = SMTLIBParser.parseSMTProgram(this.staticSMTLibCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SMTLIBParserException e) {
            throw new RuntimeException(e);
        }
        return smtProblem.addProblemToContext(solverContext);
    }


    public String generateStaticSmtLibCode() {
        return generateNullConstant() + "\n" +
               this.opal.generateExtendsSummary() + "\n" +
               this.opal.generatePolymorphismSummary(this.klassIdentifiers) + "\n";
    }

    private String generateNullConstant() {
        return "(declare-fun null () Object)";
    }



}
