package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.opalj.br.ClassType;
import tools.aqua.dse.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticManager {
    private final Opal opal;
    private final Set<ClassType> reachableTypes;

    private String staticSMTLibCode;

    private final List<Opal.PolymorphyInformation> polymorphicInformation;
    private final Map<Opal.PolymorphicMethodDefinition, Opal.BranchData> branchInformationMap;


    public StaticManager(String classPath, int depth) {
        this.opal = new Opal(classPath);
        this.reachableTypes = this.opal.reachableTypesFromConstructors(depth);
        this.staticSMTLibCode = generateStaticSmtLibCode();
        this.polymorphicInformation = this.opal.collectPolymorphyInformation(reachableTypes.toArray(new ClassType[0]));
        this.branchInformationMap = this.opal.createBranchInformationMap(polymorphicInformation);
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
        String staticCode = generateNullConstant();
        if(!this.reachableTypes.isEmpty()) {
            staticCode += this.opal.generateExtendsSummary(this.reachableTypes) + "\n";
            staticCode +=this.opal.generatePolymorphismSummary(this.reachableTypes) + "\n";
        }
        return staticCode;

    }

    private String generateNullConstant() {
        return "(declare-fun null () Int)\n(assert (= null 0))";
    }


    public Map<Opal.PolymorphicMethodDefinition, Opal.BranchData> getBranchInformationMap() {
        return branchInformationMap;
    }

    public List<Opal.PolymorphyInformation> getPolymorphicInformation() {
        return polymorphicInformation;
    }
}

