package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.opalj.br.ClassType;
import tools.aqua.dse.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Spare branches of every dispatch decision for definitions that are missing in the static polymorphism table
     * (e.g. methods of JDK classes in fields of objects), see {@link #dispatchBranch}.
     */
    public static final int UNKNOWN_DISPATCH_SLOTS = 16;

    /** per method name and descriptor: the definitions missing in the table, in the order in which they were met */
    private final Map<String, List<String>> unknownDefinitions = new HashMap<>();

    /** per method name and descriptor: the number of its definitions in the table */
    private Map<String, Integer> knownDefinitions;

    /**
     * Branch of a dispatch decision of a call of {@code name} with {@code descriptor} to the definition in
     * {@code declaringClass}. Every dispatch decision of a method has the same number of branches: one per
     * definition in the table and {@link #UNKNOWN_DISPATCH_SLOTS} spare branches, which are assigned to missing
     * definitions when they are met. Traces that dispatch to a missing definition therefore have the same decision
     * tree structure as traces that dispatch to a known one.
     *
     * @return the branch, {@code known} tells whether the definition is in the table; {@code null} if the spare
     *         branches are used up.
     */
    public synchronized DispatchBranch dispatchBranch(String name, String descriptor, String declaringClass) {
        if (knownDefinitions == null) {
            knownDefinitions = new HashMap<>();
            for (Map.Entry<Opal.PolymorphicMethodDefinition, Opal.BranchData> e : branchInformationMap.entrySet()) {
                knownDefinitions.put(e.getKey().methodName() + e.getKey().methodDescriptor(), e.getValue().branchCount());
            }
        }
        String signature = name + descriptor;
        int known = knownDefinitions.getOrDefault(signature, 0);
        int count = known + UNKNOWN_DISPATCH_SLOTS;
        Opal.BranchData data = branchInformationMap.get(
                new Opal.PolymorphicMethodDefinition(name, descriptor, declaringClass));
        if (data != null) {
            return new DispatchBranch(data.branchId(), count, true);
        }
        List<String> unknown = unknownDefinitions.computeIfAbsent(signature, k -> new ArrayList<>());
        int index = unknown.indexOf(declaringClass);
        if (index < 0) {
            if (unknown.size() >= UNKNOWN_DISPATCH_SLOTS) {
                return null;
            }
            unknown.add(declaringClass);
            index = unknown.size() - 1;
        }
        return new DispatchBranch(known + index, count, false);
    }

    /** super classes in {@code obj.extends}, see {@link #isKnownSuperclass} */
    private java.util.Set<String> superclasses;

    /** @return whether the class occurs as super class in {@code obj.extends} (so the relation can constrain it) */
    public synchronized boolean isKnownSuperclass(String jvmTypeName) {
        if (superclasses == null) {
            superclasses = new java.util.HashSet<>();
            for (String[] row : getExtendsRelation()) {
                superclasses.add(row[1]);
            }
        }
        return superclasses.contains(jvmTypeName);
    }

    /** a branch of a dispatch decision, see {@link #dispatchBranch} */
    public record DispatchBranch(int branchId, int branchCount, boolean known) {}

    public List<Opal.PolymorphyInformation> getPolymorphicInformation() {
        return polymorphicInformation;
    }

    /** The {@code obj.extends} relation exactly as asserted by {@link #generateStaticSmtLibCode()}. */
    public List<String[]> getExtendsRelation() {
        return this.reachableTypes.isEmpty() ? List.of() : this.opal.extendsRelation(this.reachableTypes);
    }
}

