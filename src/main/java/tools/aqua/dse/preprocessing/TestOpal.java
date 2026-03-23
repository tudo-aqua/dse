package tools.aqua.dse.preprocessing;

import org.opalj.br.*;
import org.opalj.br.analyses.DeclaredMethods;
import org.opalj.br.analyses.DeclaredMethodsKey$;
import org.opalj.br.analyses.Project;
import org.opalj.br.fpcf.ContextProviderKey$;
import org.opalj.br.fpcf.analyses.ContextProvider;
import org.opalj.br.fpcf.properties.Context;
import org.opalj.fpcf.*;
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
import scala.collection.Set;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class TestOpal {


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
    private static String generatedAllConstructors(Project p, Set<ClassType> types, int depth) {
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
                    Set<ClassType> subclasses = p.classHierarchy().allSubtypes(type.asClassType(), true);
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
     */private static String generateExtendsSummary(Project p) {
        StringBuilder result = new StringBuilder();

        result.append(
                """
                (define-fun obj.extends ((x!0 String) (x!1 String)) Bool
                (ite (or"""
        );

        ClassHierarchy ch = p.classHierarchy();

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
                )
                """
        );

        return result.toString();
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
     * x!0 = owner klass
     * x!1 = method name
     * x!2 = method signature
     * x!3 = declaring klass
     */
    private static void generatePolymorphismSummary(Project p, ClassType[] types) {

        StringBuilder result = new StringBuilder();

        result.append(
                """
                (define-fun obj.method.of ((x!0 String) (x!1 String) (x!2 String) (x!3 String)) Bool
                (ite (or"""
        );

        DeclaredMethods methods = ((DeclaredMethods) p.get(DeclaredMethodsKey$.MODULE$));

        for(ClassType type : types) {
            ClassFile classFile = (ClassFile) p.classFile(type).get();
            methods.declaredMethods().filter(
                    m -> m.declaringClassType() == type && m.hasSingleDefinedMethod() && !m.asDefinedMethod().definedMethod().isInitializer()
            ).foreach(method -> {
                result.append(
                        String.format(
                                "\n  (and (= x!0 \"%s\") (= x!1 \"%s\") (= x!2 \"%s\") (= x!3 \"%s\"))",
                                type.toJVMTypeName(),
                                method.name()+"XXX",
                                method.descriptor().toJVMDescriptor(),
                                method.asDefinedMethod().definedMethod().declaringClassFile().thisType().toJVMTypeName()
                        )
                );
                return null;
            });
        }

        result.append(
                """
                
                ) true false)
                )
                """
        );

        System.out.println(result);
    }


    /* relevant types are all subtypes (including T itself)
     * of the T specified in nondetObject(T, _) calls
     *
     * We will use the relevant types for three things
     *
     * 1) generate all constructors of those types
     * 2) generate an extends specification
     * 3) generate a polymorphism specification
     *
     * Probably we need multiple methods as we need
     * different sets of classes for all three
     * purposes.
     *
     */
    private static scala.collection.immutable.Set<ClassType> relevantTypes(Project p) {
        return null;
    };


    public static List<String> possibleObjectsFromNondetObject(Project p, int depth) {

        List<String> result = new ArrayList<>();

        CallGraph cg = (CallGraph) p.get(RTACallGraphKey$.MODULE$);
        DeclaredMethods methods = (DeclaredMethods) p.get(DeclaredMethodsKey$.MODULE$);

        MethodDescriptor md = MethodDescriptor$.MODULE$
                .apply("(Ljava/lang/Class;Ltools/aqua/concolic/ObjectFactory;)Ljava/lang/Object;");

        ClassType ct = ClassType.apply("tools/aqua/concolic/Verifier");

        DeclaredMethod nondetMethod =
                methods.apply(ct, "", ct, "nondetObject", md);

        PropertyStore ps = (PropertyStore) p.get(PropertyStoreKey$.MODULE$);
        TypeIterator ti = (TypeIterator) p.get(TypeIteratorKey$.MODULE$);
        ContextProvider cp = (ContextProvider) p.get(ContextProviderKey$.MODULE$);

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
                                Set<ClassType> subTypes =
                                        p.classHierarchy().allSubtypes(parameterType, true);

                                /*
                                 * Generate constructor configurations for these types.
                                 */
                                String constructors =
                                        generatedAllConstructors(p, subTypes, depth);

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

    public static void main(String[] args) {

        Project p = Project.apply(new File("src/test/resources/example"));

//        printPurple("generateExtendsSummary: ");
//        System.out.println(generateExtendsSummary(p));
//
//        printPurple("generatePolymorphismSummary: ");
//        generatePolymorphismSummary(p, new ClassType[]{ClassType.apply("A"), ClassType.apply("B"), ClassType.apply("C"), ClassType.apply("test/D")});

        CallGraph cg = (CallGraph) p.get(RTACallGraphKey$.MODULE$);

        // The set of all methods declared in all classes in the classpath
        DeclaredMethods methods = (DeclaredMethods) p.get(DeclaredMethodsKey$.MODULE$);

        // method siganture of Object nondetObject(Class, ObjectFactory)
        MethodDescriptor md = MethodDescriptor$.MODULE$.apply("(Ljava/lang/Class;Ltools/aqua/concolic/ObjectFactory;)Ljava/lang/Object;");

        // class in which Object nondetObject(Class, ObjectFactory) is defined
        ClassType ct = ClassType.apply("tools/aqua/concolic/Verifier");

        // Get method Object nondetObject(Class, ObjectFactory)
        DeclaredMethod m = methods.apply(ct, "", ct, "nondetObject", md);

        // Opal stuff
        PropertyStore ps = (PropertyStore) p.get(PropertyStoreKey$.MODULE$);
        TypeIterator ti = (TypeIterator) p.get(TypeIteratorKey$.MODULE$);
        ContextProvider cp = (ContextProvider) p.get(ContextProviderKey$.MODULE$);

        // Search for all calls of Object nondetObject(Class, ObjectFactory) and iterate over them
        cg.callersPropertyOf(m).callContexts(m, cp).iterator().foreach(
                callEdge -> {
                    Context callerContext = callEdge._2();
                    int pc = (Integer) callEdge._3();
                    EOptionP<Method, TACAI> tacProperty = ps.apply(callerContext.method().definedMethod(), TACAI$.MODULE$.key());
                    TACode<TACMethodParameter, DUVar<ValueInformation>> tac = tacProperty.ub().tac().get();
                    Stmt<DUVar<ValueInformation>>[] code = tac.stmts();
                    Stmt<DUVar<ValueInformation>> callsite = code[tac.pcToIndex()[pc]];
                    Call<DUVar<ValueInformation>> call = (callsite instanceof MethodCall) ? callsite.asMethodCall() : callsite.asAssignment().expr().asFunctionCall();
                    DUVar<ValueInformation> param = call.params().apply(0).asVar();
                    TypeIteratorState tis = new CGState(callerContext);
                    AllocationsUtil$.MODULE$.handleAllocations(
                            param,
                            callerContext,
                            null,
                            code,
                            t -> true, () -> {return null;},
                            (allocationContext, allocationPC, allocationCode) -> {
                                // This is the type of the class constant passed to nondetObject
                                ClassType parameterType = allocationCode[(int)allocationPC].asAssignment().expr().asClassConst().value().asClassType();
                                Set<ClassType> subTypes = p.classHierarchy().allSubtypes(parameterType, true);
                                System.out.print(generatedAllConstructors(p, subTypes, 1));
                                System.out.println("##");
                                System.out.print(generatedAllConstructors(p, subTypes, 2));
                                System.out.println("##");;
                                System.out.print(generatedAllConstructors(p, subTypes, 3));
                                System.out.println("##");
                                return null;
                            },
                            ti,
                            tis,
                            ps
                    );
                    return null;
                });

        System.out.println("-----------------");

        List<String> objects = possibleObjectsFromNondetObject(p, 3);

        objects.forEach(System.out::println);


    }

    public static void printPurple(String text) {
        final String PURPLE = "\u001B[35m";
        final String RESET = "\u001B[0m";
        System.out.println(PURPLE + text + RESET);
    }
}



