package tools.aqua.dse.preprocessing;

import org.opalj.br.*;
import org.opalj.br.analyses.DeclaredMethods;
import org.opalj.br.analyses.DeclaredMethodsKey$;
import org.opalj.br.analyses.Project;
import org.opalj.br.fpcf.ContextProviderKey$;
import org.opalj.br.fpcf.analyses.ContextProvider;
import org.opalj.br.fpcf.properties.Context;
import org.opalj.fpcf.EOptionP;
import org.opalj.fpcf.PropertyStore;
import org.opalj.fpcf.PropertyStoreKey$;
import org.opalj.tac.*;
import org.opalj.tac.cg.CallGraph;
import org.opalj.tac.cg.RTACallGraphKey$;
import org.opalj.tac.cg.TypeIteratorKey$;
import org.opalj.tac.fpcf.analyses.cg.AllocationsUtil$;
import org.opalj.tac.fpcf.analyses.cg.CGState;
import org.opalj.tac.fpcf.analyses.cg.TypeIterator;
import org.opalj.tac.fpcf.analyses.cg.TypeIteratorState;
import org.opalj.tac.fpcf.properties.TACAI;
import org.opalj.tac.fpcf.properties.TACAI$;
import org.opalj.value.ValueInformation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Opal {
    private final Project project;
    
    public Opal(String classPath) {
        this.project = Project.apply(new File(classPath));
    }

    public List<KlassIdentifier> extractKlassesFromClassPath() {
        //todo: Is there a simpler way to get all classes?
        ClassHierarchy classHierarchy = this.project.classHierarchy();

        List<KlassIdentifier> classNames = new ArrayList<>();

        classHierarchy.allSubtypes(ClassType.Object(), false).foreach(type -> {
            String subTypeName = type.toJVMTypeName();
            if(!type.packageName().startsWith("java/") && !subTypeName.equals("LMain;")) {
                classNames.add(new KlassIdentifier(type.toJava().replace(".", "/"), type.toJVMTypeName()));
            }
            return null;
        });
        classNames.sort(KlassIdentifier.COMPARATOR);
        return classNames;
    }




    /*
     * should generate extends information for the whole project
     * not only the types in the nondetObject typebound as
     * we may use the constucted objects in casts and checks
     * to/against arbitrary types.
     *
     * (define-fun obj.extends ((x!0 String) (x!1 String)) Bool
     * (ite (or
     *   (and (= x!0 "null") (= x!1 "LA;"))
     *   (and (= x!0 "null") (= x!1 "LB;"))
     *   (and (= x!0 "null") (= x!1 "LC;"))
     *   (and (= x!0 "null") (= x!1 "Ltest/D;"))
     *   (and (= x!0 "LA;")  (= x!1 "LA;"))
     *   (and (= x!0 "LB;")  (= x!1 "LB;"))
     *   (and (= x!0 "LB;")  (= x!1 "LA;"))
     *   (and (= x!0 "LC;")  (= x!1 "LC;"))
     *   (and (= x!0 "LC;")  (= x!1 "LB;"))
     *   (and (= x!0 "LC;")  (= x!1 "LA;"))
     *   (and (= x!0 "Ltest/D;") (= x!1 "Ltest/D;"))
     * ) true false)
     * )
     *
     * X!0 = child/ subclass
     * X!1 = parent/ superklass
     *
     */
    public String generateExtendsSummary() {
        StringBuilder result = new StringBuilder();

        result.append(
                """
                (declare-fun obj.extends (String String) Bool)
                (assert (forall ((x!0 String) (x!1 String))
                (= (obj.extends x!0 x!1)
                (ite (or"""
        );

        ClassHierarchy ch = this.project.classHierarchy();

        ch.allSubtypes(ClassType.Object(), false).foreach(subType -> {
            String subTypeName = subType.toJVMTypeName();

            //filter 1: subtype is not allowed to be from java/* and subtype is not allowed to be Main
            if(!subType.packageName().startsWith("java/") && !subTypeName.equals("LMain;")) { //todo: delete Main (just for testing purposes)

                // Add null relation
                result.append(String.format("\n  (and (= x!0 \"null\")  (= x!1 \"%s\"))", subTypeName));

                ch.allSupertypes(subType, true).foreach(superType -> {
                    String superTypeName = superType.toJVMTypeName();

                    // filter 2: Supertype is not allowed to be Object or Main
                    if (!superTypeName.equals("Ljava/lang/Object;") && !superTypeName.equals("LMain;")) {
                        result.append(String.format("\n  (and (= x!0 \"%s\")  (= x!1 \"%s\"))", subTypeName, superTypeName));
                    }
                    return null;
                });
            }
            return null;
        });

        result.append(
                """
                
                ) true false)
                )))
                """
        );

        return result.toString();
    }

    public List<PolymorphyInformation> collectPolymorphyInformation(ClassType[] types) {
        List<PolymorphyInformation> polymorphicInfos = new ArrayList<>();
        DeclaredMethods methods = (DeclaredMethods) this.project.get(DeclaredMethodsKey$.MODULE$);

        for (ClassType type : types) {
            // Hinweis: classFile wird im Originalcode zwar abgerufen, aber nicht genutzt.
            // Falls du es nicht brauchst, kann die Zeile entfallen.

            methods.declaredMethods().filter(m ->
                    m.declaringClassType().equals(type) &&
                            m.hasSingleDefinedMethod() &&
                            !m.asDefinedMethod().definedMethod().isInitializer()
            ).foreach(method -> {
                polymorphicInfos.add(new PolymorphyInformation(
                        type.toJVMTypeName(),
                        method.name(),
                        method.descriptor().toJVMDescriptor(),
                        method.asDefinedMethod().definedMethod().declaringClassFile().thisType().toJVMTypeName()
                ));
                return null;
            });
        }
        return polymorphicInfos;
    }


    public record BranchData(int branchId,
                             int branchCount) {}

    public record PolymorphicMethodDefinition(String methodName,
                                       String methodDescriptor,
                                       String declaringClass) {}

    public Map<PolymorphicMethodDefinition, BranchData> createBranchInformationMap(List<PolymorphyInformation> rawInfos) {
        // Step 1: Count occurrences per logical group
//        int branchCount = (int) rawInfos.stream()
//                .map(info -> new PolymorphicMethodDefinition(info.methodName(), info.methodDescriptor(), info.declaringClass()))
//                .distinct()
//                .count();

        Map<PolymorphicMethodDefinition, Integer> countsMap = rawInfos.stream()
                .collect(Collectors.groupingBy(
                        info -> new PolymorphicMethodDefinition(info.methodName(), info.methodDescriptor(), info.declaringClass()),
                        Collectors.reducing(0, info -> 1, Integer::sum)
                ));

        HashMap<PolymorphicMethodDefinition, BranchData> result = new HashMap<>();
        countsMap.forEach((key, value) -> result.put(key, new BranchData(value-1, countsMap.size())));

        return result;








//
//        // Step 2: Tracker for assigning incremental IDs within each group
//        Map<PolymorphicMethodDefinition, Integer> idTracker = new HashMap<>();
//        Map<PolymorphicMethodDefinition, BranchData> resultMap = new HashMap<>();
//
//        // Step 3: Populate the result map
//        for (PolymorphyInformation info : rawInfos) {
//            PolymorphicMethodDefinition key = new PolymorphicMethodDefinition(info.methodName(), info.methodDescriptor(), info.declaringClass());
//
//            int totalCount = countsMap.get(key).intValue()+1;
//            int currentId = idTracker.getOrDefault(key, 0);
//
//            // Insert entry into the result map
//            resultMap.put(key, new BranchData(currentId, totalCount));
//
//            // Increment ID for the next element in the same group
//            idTracker.put(key, currentId + 1);
//        }
//
//        return resultMap;
    }

    public List<PolymorphyInformation> collectPolymorphyInformation(List<KlassIdentifier> types) {
        ClassType[] classType = types.stream()
                .map(KlassIdentifier::shortIdentifier)
                .map(ClassType::apply)
                .toArray(ClassType[]::new);

        return collectPolymorphyInformation(classType);
    }
    
    /*
     * should generate polymorphism information (i.e., in which
     * classes are methods defined). This only needs to be known
     * for methods of types related to the typebound (sub and super)
     * that are called
     *
     * In the example:
     *
     * (define-fun obj.method.of ((x!0 String) (x!1 String) (x!2 String) (x!3 String)) Bool
     * (ite (or
     * (and (= x!0 "LA;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     * (and (= x!0 "LB;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     * (and (= x!0 "LC;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     * ) true false)
     * )
     *
     * x!0 = calling klass
     * x!1 = method name
     * x!2 = method signature
     * x!3 = defining klass
     */
    public String generatePolymorphismSummary(List<KlassIdentifier> types) {
        StringBuilder result = new StringBuilder();

        result.append("""
            (declare-fun obj.method.of (String String String String) Bool)
            (assert (forall ((x!0 String) (x!1 String) (x!2 String) (x!3 String))
            (= (obj.method.of x!0 x!1 x!2 x!3)
            (ite (or
            """);

        List<PolymorphyInformation> infos = collectPolymorphyInformation(types);

        for (PolymorphyInformation info : infos) {
            result.append(String.format(
                    "\n  (and (= x!0 \"%s\") (= x!1 \"%s\") (= x!2 \"%s\") (= x!3 \"%s\"))",
                    info.accessingClass(),
                    info.methodName(),
                    info.methodDescriptor(),
                    info.declaringClass()
            ));
        }

        result.append("""
            
            ) true false)
            )))
            """);

        return result.toString();
    }

    
    
    
    
    // --------------------------------------------------------------------------------------------------------------
    //                              generation of constructor signatures
    // --------------------------------------------------------------------------------------------------------------


    /*
     * should generate all configurations for constructor
     * summary analyses in the specified format for the
     * given types upto nesting depth.
     *
     * null|NULL represents the null value
     *
     * Format:
     *
     * class|signature|{p1}{p2}..{pn}
     *
     * for primitive values the values are disregarded
     * and do not have to be provided.
     *
     * In the example, we would expect the following output
     * for types {B,C}
     *
     * Depth 1:
     *
     * LB;|()V|{}
     * LB;|(I)V|{}
     * LC;|(I)V|{}
     * LC;|(LA;)V|{null|NULL}
     *
     * Depth 2:
     *
     * LC;|(LA;)V|{LA;|(I)V|{}}
     * LC;|(LA;)V|{LB;|()V|{}}
     * LC;|(LA;)V|{LB;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{null|NULL}}
     *
     * Depth 3:
     *
     * LC;|(LA;)V|{LC;|(LA;)V|{LA;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{{LB;|()V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{LB;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{LC;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{LC;|(LA;)V|{null|NULL}}
     *
     */
    private static String generatedAllConstructors(Project p, scala.collection.Set<ClassType> types, int depth) {
        StringBuilder result = new StringBuilder();
        types.foreach(tpe -> {
            ClassFile cf = (ClassFile) p.classFile(tpe).get();
            cf.constructors().foreach(constructor -> {
                String base = String.format("%s|%s|", tpe.toJVMTypeName(), constructor.descriptor().toJVMDescriptor());
                String parametersString = generateParametersString(p, constructor, base, depth);
                result.append(parametersString);
                return null;
            });
            return null;
        });
        return result.toString();
    }

    private static String generateParametersString(Project p, Method constructor, String base, int depth) {
        if(depth > 1 && !constructor.descriptor().parameterTypes().exists(type -> type.isClassType()))
            return "";

        StringBuilder result = new StringBuilder(base);
        constructor.descriptor().parameterTypes().foreach(type -> {
            if(type.isClassType()) { /* TODO What about array types? */
                if (depth == 1) {
                    result.append("{null|NULL}");
                } else {
                    scala.collection.Set<ClassType> subclasses = p.classHierarchy().allSubtypes(type.asClassType(), true);
                    String paramConstructors = generatedAllConstructors(p, subclasses, depth - 1);
                    Stream<String> prefixes = result.toString().lines();
                    result.setLength(0);
                    prefixes.forEach(prefix -> {
                        paramConstructors.lines().forEach(paramConstructor -> {
                            result.append(prefix).append("{").append(paramConstructor).append("}\n");
                        });
                    });
                }
            } else
                result.append("{}");
            return null;
        });
        if(depth == 1)
            result.append('\n');
        return result.toString();
    }


    private List<String> possibleObjectsFromNondetObjectWithDepth(int depth) {

        List<String> result = new ArrayList<>();

        CallGraph cg = (CallGraph) this.project.get(RTACallGraphKey$.MODULE$);
        DeclaredMethods methods = (DeclaredMethods) this.project.get(DeclaredMethodsKey$.MODULE$);

        MethodDescriptor md = MethodDescriptor$.MODULE$
                .apply("(Ljava/lang/Class;Ltools/aqua/concolic/ObjectFactory;)Ljava/lang/Object;");

        ClassType ct = ClassType.apply("tools/aqua/concolic/Verifier");

        DeclaredMethod nondetMethod =
                methods.apply(ct, "", ct, "nondetObject", md);

        PropertyStore ps = (PropertyStore) this.project.get(PropertyStoreKey$.MODULE$);
        TypeIterator ti = (TypeIterator) this.project.get(TypeIteratorKey$.MODULE$);
        ContextProvider cp = (ContextProvider) this.project.get(ContextProviderKey$.MODULE$);

        /*
         * Iterate over all callsites of nondetObject(...)
         */
        cg.callersPropertyOf(nondetMethod).callContexts(nondetMethod, cp).iterator().foreach(
                callEdge -> {

                    Context callerContext = callEdge._2();
                    int pc = (Integer) callEdge._3();

                    /*
                     * Retrieve TAC for the caller method.
                     */
                    EOptionP<Method, TACAI> tacProperty =
                            ps.apply(callerContext.method().definedMethod(), TACAI$.MODULE$.key());

                    TACode<TACMethodParameter, DUVar<ValueInformation>> tac =
                            tacProperty.ub().tac().get();

                    Stmt<DUVar<ValueInformation>>[] code = tac.stmts();

                    /*
                     * Resolve the callsite instruction in TAC.
                     */
                    Stmt<DUVar<ValueInformation>> callsite = code[tac.pcToIndex()[pc]];

                    Call<DUVar<ValueInformation>> call =
                            (callsite instanceof MethodCall)
                                    ? callsite.asMethodCall()
                                    : callsite.asAssignment().expr().asFunctionCall();

                    /*
                     * First parameter of nondetObject -> Class parameter.
                     */
                    DUVar<ValueInformation> param = call.params().apply(0).asVar();

                    TypeIteratorState tis = new CGState(callerContext);

                    /*
                     * Follow the dataflow to find where the Class constant originates.
                     */
                    AllocationsUtil$.MODULE$.handleAllocations(
                            param,
                            callerContext,
                            null,
                            code,
                            t -> true,
                            () -> { return null; },

                            (allocationContext, allocationPC, allocationCode) -> {

                                /*
                                 * Extract the class constant passed to nondetObject.
                                 */
                                ClassType parameterType =
                                        allocationCode[(int) allocationPC]
                                                .asAssignment()
                                                .expr()
                                                .asClassConst()
                                                .value()
                                                .asClassType();

                                /*
                                 * Determine all subtypes of the given class.
                                 */
                                scala.collection.Set<ClassType> subTypes =
                                        this.project.classHierarchy().allSubtypes(parameterType, true);

                                /*
                                 * Generate constructor configurations for these types.
                                 */
                                String constructors =
                                        generatedAllConstructors(this.project, subTypes, depth);

                                /*
                                 * Convert the result string into individual lines.
                                 */
                                constructors.lines().forEach(result::add);

                                return null;
                            },

                            ti,
                            tis,
                            ps
                    );

                    return null;
                });

        return result;
    }

    //generateSignaturesOfPossibleObjectsFromNondetObject
    public List<String> generateSignaturesOfPossibleConstructorCallsFromNondetObject(int depth) {
        List<String> result = new ArrayList<>();
        for(int i = 1; i <= depth; i++) {
            result.addAll(possibleObjectsFromNondetObjectWithDepth(i));
        }

        return result;
    }

    public record PolymorphyInformation(
            String accessingClass,
            String methodName,
            String methodDescriptor,
            String declaringClass
    ) {}
}
