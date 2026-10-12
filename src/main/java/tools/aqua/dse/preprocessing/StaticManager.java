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
        this.polymorphicInformation = this.opal.collectPolymorphyInformation(reachableTypes.toArray(new ClassType[0]));
        this.branchInformationMap = this.opal.createBranchInformationMap(polymorphicInformation);
    }

    public SolverContext setStaticSmtLibCode(SolverContext solverContext) {
        SMTProblem smtProblem = null;
        try {
            if (this.staticSMTLibCode == null) {
                this.staticSMTLibCode = generateStaticSmtLibCode();
            }
            smtProblem = SMTLIBParser.parseSMTProgram(this.staticSMTLibCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SMTLIBParserException e) {
            throw new RuntimeException(e);
        }
        return smtProblem.addProblemToContext(solverContext);
    }


    /** Adds only the {@code null} constant; for the relations, see {@link #getGrounding()}. */
    public void setNullConstant(SolverContext solverContext) {
        SmtProblemManager.addSmtProblemAsString(generateNullConstant(), solverContext);
    }

    /** The quantifier-free instantiation of the relations defined by {@link #generateStaticSmtLibCode()}. */
    public ObjectRelationGrounding getGrounding() {
        if (this.grounding == null) {
            this.grounding = ObjectRelationGrounding.of(getExtendsRelation(),
                    this.reachableTypes.isEmpty() ? List.of() : this.polymorphicInformation);
        }
        return this.grounding;
    }

    private ObjectRelationGrounding grounding;

    /** Adds a class that occurs in constructor summaries (e.g. a JDK class in a field) to the grounded relation. */
    public void registerClass(String jvmTypeName) {
        ObjectRelationGrounding g = getGrounding();
        if (!g.knowsClass(jvmTypeName)) {
            g.addExtends(this.opal.extendsRowsFor(jvmTypeName));
        }
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

    /** The {@code obj.extends} relation exactly as asserted by {@link #generateStaticSmtLibCode()}. */
    public List<String[]> getExtendsRelation() {
        return this.reachableTypes.isEmpty() ? List.of() : this.opal.extendsRelation(this.reachableTypes);
    }
}

