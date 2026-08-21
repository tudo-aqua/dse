package tools.aqua.dse.preprocessing;

import org.apache.commons.io.IOUtils;
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

import java.io.*;
import java.lang.Deprecated;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bridge between the OPAL framework and the DSE preprocessing pipeline.
 *
 * <p>On construction the class loads the bytecode project at {@code classPath} together with the
 * Java standard library ({@code JavaBase}) into an OPAL {@link Project}. From this, three static
 * SMT artefacts are produced:
 *
 * <ol>
 *   <li>Constructor signatures for all subtypes reachable at {@code Verifier.nondetObject}
 *       call-sites ({@link #generateSignaturesOfPossibleConstructorCallsFromNondetObject}).</li>
 *   <li>SMT definition {@code obj.extends} of the inheritance hierarchy
 *       ({@link #generateExtendsSummary(Set)}).</li>
 *   <li>SMT definition {@code obj.method.of} of the polymorphism table
 *       ({@link #generatePolymorphismSummary(Set)}).</li>
 * </ol>
 *
 * <p>The type set relevant for the summaries is computed constructor-driven via
 * {@link #reachableTypesFromConstructors(int)} and includes both project and library classes.
 */
public class Opal {
    private final Project project;

    /**
     * Loads the bytecode project at {@code classPath} together with the Java standard library into
     * an OPAL {@link Project}.
     *
     * <p>The RTA call graph and class hierarchy are built lazily by OPAL on first access.
     *
     * @param classPath path to the directory or JAR containing the project classes.
     */
    public Opal(String classPath){
        File tmpFile;
        try {
            Path tmpPath = Files.createTempFile("java.base", ".jmods");
            tmpFile = tmpPath.toFile();
            //tmpFile.deleteOnExit();
            OutputStream outStream= new FileOutputStream(tmpFile);
            InputStream inStream = Opal.class.getClassLoader().getResourceAsStream("jmods/java.base.jmod");
            IOUtils.copy(inStream, outStream);
            System.out.println("tmpFile: " + tmpFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Cannot read java.base.jmods due to IOException: " + e.toString());
        }
        this.project = Project.apply(
                new File(classPath),
//                new File("/Users/mlazar/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home/jmods/java.base.jmod") //todo: more general
                tmpFile
        );
        System.out.println("[Opal] Loaded " + this.project.projectClassFilesCount()
                + " project classes, " + this.project.libraryClassFilesCount() + " library classes");
    }

    /**
     * Returns {@code true} if {@code t} is an internal factory helper class that should be
     * excluded from all summaries ({@code LFactories;} or {@code LFactories$*Factory;}).
     *
     * @param t type to check.
     * @return {@code true} if the type should be skipped.
     */
    private static boolean isExcludedFactoryClass(ClassType t) {
        String n = t.toJVMTypeName();
        return n.equals("LFactories;") || (n.startsWith("LFactories$") && n.endsWith("Factory;"));
    }

    /**
     * Returns a sorted list of all project classes, excluding {@code Main} and factory helpers.
     *
     * <p>Used among others by the legacy overload {@link #generatePolymorphismSummary(List)}.
     * For the constructor-driven analysis use {@link #reachableTypesFromConstructors(int)} instead.
     *
     * @return sorted list of {@link KlassIdentifier} entries.
     */
    public List<KlassIdentifier> extractKlassesFromClassPath() {
        //todo: Is there a simpler way to get all classes?
        ClassHierarchy classHierarchy = this.project.classHierarchy();

        List<KlassIdentifier> classNames = new ArrayList<>();

        this.project.allProjectClassFiles().foreach(x -> {
            ClassType type = ((ClassFile) x).thisType();

            String subTypeName = type.toJVMTypeName();
            if(!subTypeName.equals("LMain;") && !isExcludedFactoryClass(type)) {
                classNames.add(new KlassIdentifier(type.toJava().replace(".", "/"), type.toJVMTypeName()));
            }
            return null;
        });
        classNames.sort(KlassIdentifier.COMPARATOR);
        if (classNames.size() <= 50) {
            System.out.println("[Opal] extractKlassesFromClassPath: " + classNames.size() + " classes: "
                    + classNames.stream().map(KlassIdentifier::shortIdentifier).collect(Collectors.joining(", ")));
        } else {
            System.out.println("[Opal] extractKlassesFromClassPath: " + classNames.size() + " classes");
        }
        return classNames;
    }




    /**
     * Generates the SMT definition {@code obj.extends} restricted to the given reachable type set.
     *
     * <p>Each type in {@code reachable} receives a {@code null} edge as well as edges to all its
     * transitive supertypes, provided those supertypes are also contained in {@code reachable}.
     * {@code java.lang.Object}, {@code Main}, and factory helpers are excluded.
     *
     * <p>Example output (x!0 = subtype, x!1 = supertype):
     * <pre>{@code
     * (define-fun obj.extends ((x!0 String) (x!1 String)) Bool
     * (ite (or
     *   (and (= x!0 "null") (= x!1 "LA;"))
     *   (and (= x!0 "null") (= x!1 "LB;"))
     *   (and (= x!0 "LA;")  (= x!1 "LA;"))
     *   (and (= x!0 "LB;")  (= x!1 "LB;"))
     *   (and (= x!0 "LB;")  (= x!1 "LA;"))
     * ) true false)
     * )
     * }</pre>
     *
     * @param reachable type set to restrict the output to (e.g. from
     *                  {@link #reachableTypesFromConstructors(int)}).
     * @return SMT-LibS string of the {@code obj.extends} definition.
     */
    public String generateExtendsSummary(Set<ClassType> reachable) {
        StringBuilder result = new StringBuilder();
        int[] relationCount = {0};

        result.append(
                """
                (declare-fun obj.extends (String String) Bool)
                (assert (forall ((x!0 String) (x!1 String))
                (= (obj.extends x!0 x!1)
                (ite (or"""
        );

        ClassHierarchy ch = this.project.classHierarchy();
        for (ClassType subType : reachable) {
            String subTypeName = subType.toJVMTypeName();
            if (subTypeName.equals("LMain;") || subTypeName.equals("Ljava/lang/Object;") || isExcludedFactoryClass(subType)) {
                continue;
            }

            // Add null relation
            result.append(String.format("\n  (and (= x!0 \"null\")  (= x!1 \"%s\"))", subTypeName));
            relationCount[0]++;

            ch.allSupertypes(subType, true).foreach(superType -> {
                String superTypeName = superType.toJVMTypeName();
                if (!superTypeName.equals("LMain;") && !superTypeName.equals("Ljava/lang/Object;")
                        && !isExcludedFactoryClass(superType) && reachable.contains(superType)) {
                    result.append(String.format("\n  (and (= x!0 \"%s\")  (= x!1 \"%s\"))", subTypeName, superTypeName));
                    relationCount[0]++;
                }
                return null;
            });
        }

        result.append(
                """

                ) true false)
                )))
                """
        );

        System.out.println("[Opal] generateExtendsSummary: " + relationCount[0] + " extends relations");
        return result.toString();
    }

    /**
     * Generates the SMT definition {@code obj.extends} based on all project classes.
     *
     * @return SMT-LibS string of the {@code obj.extends} definition.
     * @deprecated Uses all project classes without filtering. Use
     *             {@link #generateExtendsSummary(Set)} with the type set computed by
     *             {@link #reachableTypesFromConstructors(int)} instead.
     */
    @Deprecated
    public String generateExtendsSummary() {
        StringBuilder result = new StringBuilder();
        int[] relationCount = {0};

        result.append(
                """
                (declare-fun obj.extends (String String) Bool)
                (assert (forall ((x!0 String) (x!1 String))
                (= (obj.extends x!0 x!1)
                (ite (or"""
        );

        this.project.allProjectClassFiles().foreach(x -> {
            ClassType subType = ((ClassFile) x).thisType();
            ClassHierarchy ch = this.project.classHierarchy();

            String subTypeName = subType.toJVMTypeName();
            //filter 1: subtype is not allowed to be from java/* and subtype is not allowed to be Main or a Factory helper
            if(!subType.packageName().startsWith("java/") && !subTypeName.equals("LMain;") && !isExcludedFactoryClass(subType)) { //todo: delete Main (just for testing purposes)

                // Add null relation
                result.append(String.format("\n  (and (= x!0 \"null\")  (= x!1 \"%s\"))", subTypeName));
                relationCount[0]++;

                ch.allSupertypes(subType, true).foreach(superType -> {
                    String superTypeName = superType.toJVMTypeName();

                    // filter 2: Supertype is not allowed to be Object, Main, or a Factory helper
                    if (!superTypeName.equals("LMain;") && !superTypeName.equals("Ljava/lang/Object;") && !isExcludedFactoryClass(superType)) {
                        result.append(String.format("\n  (and (= x!0 \"%s\")  (= x!1 \"%s\"))", subTypeName, superTypeName));
                        relationCount[0]++;
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

        System.out.println("[Opal] generateExtendsSummary: " + relationCount[0] + " extends relations");
        return result.toString();

//
//        ClassHierarchy ch = this.project.classHierarchy();
//
//
//        ch.allSubtypes(ClassType.Object(), false).foreach(subType -> {
//            String subTypeName = subType.toJVMTypeName();
//
//            //filter 1: subtype is not allowed to be from java/* and subtype is not allowed to be Main
//            if(!subType.packageName().startsWith("java/") && !subTypeName.equals("LMain;")) { //todo: delete Main (just for testing purposes)
//
//                // Add null relation
//                result.append(String.format("\n  (and (= x!0 \"null\")  (= x!1 \"%s\"))", subTypeName));
//
//                ch.allSupertypes(subType, true).foreach(superType -> {
//                    String superTypeName = superType.toJVMTypeName();
//
//                    // filter 2: Supertype is not allowed to be Object or Main
//                    if (!superTypeName.equals("LMain;")) {
//                        result.append(String.format("\n  (and (= x!0 \"%s\")  (= x!1 \"%s\"))", subTypeName, superTypeName));
//                    }
//                    return null;
//                });
//            }
//            return null;
//        });
//
//        result.append(
//                """
//
//                ) true false)
//                )))
//                """
//        );
//
//        return result.toString();
    }

    /**
     * Collects all declared (non-inherited, non-initializer) methods for each type in {@code types}
     * and returns them as a list of {@link PolymorphyInformation}.
     *
     * <p>{@code java.lang.Object} and factory helpers are skipped.
     *
     * @param types array of types for which method declarations are collected.
     * @return list of polymorphism entries found.
     */
    public List<PolymorphyInformation> collectPolymorphyInformation(ClassType[] types) {
        List<PolymorphyInformation> polymorphicInfos = new ArrayList<>();
        DeclaredMethods methods = (DeclaredMethods) this.project.get(DeclaredMethodsKey$.MODULE$);

        for (ClassType type : types) {
            if (isExcludedFactoryClass(type)) continue;
            if (type.toJVMTypeName().equals("Ljava/lang/Object;")) continue;

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
        System.out.println("[Opal] collectPolymorphyInformation: " + types.length + " types -> "
                + polymorphicInfos.size() + " methods");
        return polymorphicInfos;
    }


    /**
     * Identifier of a branch node in the polymorphism dispatch decision tree.
     *
     * @param branchId    running ID of this branch among all polymorphic methods.
     * @param branchCount total number of branches (= number of distinct polymorphic methods).
     */
    public record BranchData(int branchId,
                             int branchCount) {}

    /**
     * Unique key of a polymorphic method definition consisting of name, JVM descriptor, and
     * declaring class.
     *
     * @param methodName       simple method name.
     * @param methodDescriptor JVM method descriptor, e.g. {@code (I)V}.
     * @param declaringClass   JVM type name of the declaring class, e.g. {@code LA;}.
     */
    public record PolymorphicMethodDefinition(String methodName,
                                       String methodDescriptor,
                                       String declaringClass) {}

    /**
     * Builds a map from polymorphic method definitions to their branch data.
     *
     * <p>Each distinct {@link PolymorphicMethodDefinition} in {@code rawInfos} receives a unique
     * {@link BranchData#branchId()} and the total branch count as {@link BranchData#branchCount()}.
     *
     * @param rawInfos raw data from {@link #collectPolymorphyInformation(ClassType[])}.
     * @return map from method definition to branch metadata.
     */
    public Map<PolymorphicMethodDefinition, BranchData> createBranchInformationMap(List<PolymorphyInformation> rawInfos) {
        Map<PolymorphicMethodDefinition, Integer> countsMap = rawInfos.stream()
                .collect(Collectors.groupingBy(
                        info -> new PolymorphicMethodDefinition(info.methodName(), info.methodDescriptor(), info.declaringClass()),
                        Collectors.reducing(0, info -> 1, Integer::sum)
                ));

        int i = 0;
        HashMap<PolymorphicMethodDefinition, BranchData> result = new HashMap<>();

        for (PolymorphicMethodDefinition polymorphicMethodDefinition :countsMap.keySet()) {
            result.put(polymorphicMethodDefinition,new BranchData(i++, countsMap.size()));
        }

        return result;

    }

    /**
     * Delegates to {@link #collectPolymorphyInformation(ClassType[])} after converting the
     * {@link KlassIdentifier} list to a {@code ClassType[]} array.
     *
     * @param types list of class identifiers.
     * @return list of polymorphism entries found.
     */
    public List<PolymorphyInformation> collectPolymorphyInformation(List<KlassIdentifier> types) {
        ClassType[] classType = types.stream()
                .map(KlassIdentifier::shortIdentifier)
                .map(ClassType::apply)
                .toArray(ClassType[]::new);

        return collectPolymorphyInformation(classType);
    }
    
    /**
     * Generates the SMT definition {@code obj.method.of} restricted to the given reachable type
     * set.
     *
     * <p>Example output (x!0 = accessing class, x!1 = method name, x!2 = JVM descriptor,
     * x!3 = declaring class):
     * <pre>{@code
     * (define-fun obj.method.of ((x!0 String) (x!1 String) (x!2 String) (x!3 String)) Bool
     * (ite (or
     *   (and (= x!0 "LA;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     *   (and (= x!0 "LB;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     *   (and (= x!0 "LC;") (= x!1 "foo") (= x!2 "()V") (= x!3 "LA;"))
     * ) true false)
     * )
     * }</pre>
     *
     * @param reachable type set to restrict the output to (e.g. from
     *                  {@link #reachableTypesFromConstructors(int)}).
     * @return SMT-LibS string of the {@code obj.method.of} definition.
     */
    public String generatePolymorphismSummary(Set<ClassType> reachable) {
        ClassType[] types = reachable.toArray(new ClassType[0]);
        return generatePolymorphismSummaryInternal(collectPolymorphyInformation(types));
    }

    /**
     * Generates the SMT definition {@code obj.method.of} from a {@link KlassIdentifier} list.
     *
     * @param types list of class identifiers.
     * @return SMT-LibS string of the {@code obj.method.of} definition.
     */
    public String generatePolymorphismSummary(List<KlassIdentifier> types) {
        return generatePolymorphismSummaryInternal(collectPolymorphyInformation(types));
    }

    /**
     * Shared rendering logic for all {@code generatePolymorphismSummary} overloads.
     *
     * @param infos prepared polymorphism entries.
     * @return SMT-LibS string of the {@code obj.method.of} definition.
     */
    private String generatePolymorphismSummaryInternal(List<PolymorphyInformation> infos) {
        StringBuilder result = new StringBuilder();

        result.append("""
            (declare-fun obj.method.of (String String String String) Bool)
            (assert (forall ((x!0 String) (x!1 String) (x!2 String) (x!3 String))
            (= (obj.method.of x!0 x!1 x!2 x!3)
            (ite (or
            """);

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


    /**
     * Generates all constructor signature strings for the given types up to nesting depth
     * {@code depth}.
     *
     * <p>Signature format: {@code Class|Descriptor|{P1}{P2}...{Pn}}.
     * Primitive parameters are encoded as empty braces {@code {}}. Reference parameters are
     * expanded recursively. {@code null|NULL} represents the null value.
     *
     * <p>{@code java.lang.Object} is handled as a special case: exactly the default constructor
     * entry {@code Ljava/lang/Object;|()V|} is added.
     *
     * <p>Example output for types {@code {B, C}}, depths 1–3:
     * <pre>{@code
     * -- Depth 1 --
     * LB;|()V|
     * LB;|(I)V|{}
     * LC;|(I)V|{}
     * LC;|(LA;)V|{null|NULL}
     *
     * -- Depth 2 --
     * LC;|(LA;)V|{LA;|(I)V|{}}
     * LC;|(LA;)V|{LB;|()V|}
     * LC;|(LA;)V|{LB;|(I)V|{}}
     * LC;|(LA;)V|{LC;|(LA;)V|{null|NULL}}
     *
     * -- Depth 3 --
     * LC;|(LA;)V|{LC;|(LA;)V|{LC;|(LA;)V|{null|NULL}}}
     * }</pre>
     *
     * @param p     OPAL project for access to class files and the class hierarchy.
     * @param types set of types to consider (typically subtypes of a seed type).
     * @param depth maximum nesting depth for reference parameters.
     * @return list of generated constructor signature strings including {@code null|NULL}.
     */
    private static List<String> generatedAllConstructors(Project p, scala.collection.Set<ClassType> types, int depth) {
        System.out.println("[Opal] generatedAllConstructors: " + types.size() + " types, depth=" + depth);
        List<String> result = new ArrayList<>();
        result.add("null|NULL");
        types.foreach(tpe -> {
            if (tpe.toJVMTypeName().equals("Ljava/lang/Object;")) {
                result.add("Ljava/lang/Object;|()V|");
                return null;
            }
//            if (tpe.packageName().startsWith("jdk") ||
//                    tpe.packageName().startsWith("java") ||
//                    tpe.packageName().startsWith("tools/aqua") ||
//                    tpe.packageName().startsWith("sun")) {
//                System.out.println("[Opal] generatedAllConstructors: skipping " + tpe.packageName());
//                return null;
//            }
            if (isExcludedFactoryClass(tpe)) {
                return null;
            }
            if (!p.isProjectType(tpe)) {
                System.out.println("[Opal] generatedAllConstructors: skipping non-project type "
                        + tpe.toJVMTypeName());
                return null;
            }
            if (p.classFile(tpe).isEmpty()) {
                System.out.println("[Opal] generatedAllConstructors: skipping " + tpe.toJVMTypeName()
                        + " (no class file)");
                return null;
            }
            ClassFile cf = (ClassFile) p.classFile(tpe).get();
            if (cf.isAbstract()) {
                return null;
            }
            cf.constructors().foreach(constructor -> {
                if (constructor.isPublic()) {
                    String base = String.format("%s|%s|", tpe.toJVMTypeName(), constructor.descriptor().toJVMDescriptor());
                    result.addAll(generateParametersString(p, constructor, base, depth));
                }
                return null;
            });
            return null;
        });
        return result;
    }

    private static final int MAX_CONSTRUCTOR_SIGNATURES =
            Integer.parseInt(System.getProperty("dse.opal.maxConstructorSignatures", "1000000"));

    /**
     * Expands the parameters of a constructor into a Cartesian product of signature suffixes.
     *
     * <p>For each parameter, depending on {@code depth} and parameter type, either
     * {@code {null|NULL}} (depth == 1 and reference type), the Cartesian product of recursively
     * generated parameter signatures (depth &gt; 1 and reference type), or {@code {}} (primitive)
     * is appended. At depth &gt; 1 with purely primitive parameters an empty list is returned to
     * avoid redundant deep expansions.
     *
     * <p>If the Cartesian product exceeds {@link #MAX_CONSTRUCTOR_SIGNATURES}, an
     * {@link IllegalStateException} is thrown.
     *
     * @param p           OPAL project.
     * @param constructor the constructor to expand.
     * @param base        already built prefix of the form {@code Class|Descriptor|}.
     * @param depth       remaining nesting depth.
     * @return list of complete signature strings with expanded parameters.
     */
    private static List<String> generateParametersString(Project p, Method constructor, String base, int depth) {
        if(depth > 1 && !constructor.descriptor().parameterTypes().exists(Type::isClassType))
            return List.of();

        List<String> prefixes = new ArrayList<>();
        prefixes.add(base);

        constructor.descriptor().parameterTypes().foreach(type -> {
            if(type.isClassType()) { /* TODO What about array types? */
                if (depth == 1) {
                    prefixes.replaceAll(p2 -> p2 + "{null|NULL}");
                } else {
                    scala.collection.Set<ClassType> subclasses = p.classHierarchy().allSubtypes(type.asClassType(), true);
                    List<String> paramConstructors = generatedAllConstructors(p, subclasses, depth - 1);
                    long product = (long) prefixes.size() * (long) paramConstructors.size();
                    if (product > MAX_CONSTRUCTOR_SIGNATURES) {
                        throw new IllegalStateException(String.format(
                            "Cartesian explosion in constructor signature generation: %d × %d = %d exceeds limit %d. "
                                + "Reduce depth or increase -Ddse.opal.maxConstructorSignatures=<n>.",
                            prefixes.size(), paramConstructors.size(), product, MAX_CONSTRUCTOR_SIGNATURES));
                    }
                    List<String> next = new ArrayList<>((int) product);
                    for (String prefix : prefixes) {
                        for (String pc : paramConstructors) {
                            next.add(prefix + "{" + pc + "}");
                        }
                    }
                    prefixes.clear();
                    prefixes.addAll(next);
                }
            } else {
                prefixes.replaceAll(p2 -> p2 + "{}");
            }
            return null;
        });
        return prefixes;
    }


    /**
     * Computes all constructor signatures for a concrete nesting depth {@code depth}.
     *
     * <p>Every call-site of {@code Verifier.nondetObject(Class, ObjectFactory)} is located via the
     * RTA call graph. TAC dataflow analysis resolves the {@code Class<?>} literal of the first
     * argument; all subtypes of the resolved type are then expanded into signatures via
     * {@link #generatedAllConstructors}.
     *
     * @param depth nesting depth for constructor expansion (must be &gt;= 1).
     * @return list of generated constructor signature strings for this depth.
     */
    private List<String> possibleObjectsFromNondetObjectWithDepth(int depth) {
        System.out.println("[Opal] possibleObjectsFromNondetObjectWithDepth: depth=" + depth);

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

        int[] callSiteIndex = {0};

        /*
         * Iterate over all callsites of nondetObject(...)
         */
        cg.callersPropertyOf(nondetMethod).callContexts(nondetMethod, cp).iterator().foreach(
                callEdge -> {

                    Context callerContext = callEdge._2();
                    int pc = (Integer) callEdge._3();
                    callSiteIndex[0]++;

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
                                    : callsite.asAssignmentLike().expr().asFunctionCall();

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

                                System.out.println("[Opal]   call-site #" + callSiteIndex[0]
                                        + ": param=" + parameterType.toJVMTypeName()
                                        + ", subtypes=" + subTypes.size()
                                        + ", depth=" + depth);

                                /*
                                 * Generate constructor configurations for these types.
                                 */
                                List<String> constructors =
                                        generatedAllConstructors(this.project, subTypes, depth);

                                result.addAll(constructors);

                                return null;
                            },

                            ti,
                            tis,
                            ps
                    );

                    return null;
                });

        System.out.println("[Opal] nondetObject call-sites processed: " + callSiteIndex[0]);
        return result;
    }

    /**
     * Aggregates constructor signatures across all depths from 1 up to and including {@code depth}.
     *
     * <p>Calls {@link #possibleObjectsFromNondetObjectWithDepth(int)} once for each depth
     * {@code i} (1 &le; i &le; depth) and merges the results. Duplicates are removed by the caller
     * ({@link tools.aqua.dse.preprocessing.ConstructorSummaryManager}) via {@code distinct()}.
     *
     * @param depth maximum nesting depth.
     * @return combined list of all constructor signature strings.
     */
    public List<String> generateSignaturesOfPossibleConstructorCallsFromNondetObject(int depth) {
        System.out.println("[Opal] generateSignaturesOfPossibleConstructorCallsFromNondetObject: depth=" + depth);
        List<String> result = new ArrayList<>();
        for(int i = 1; i <= depth; i++) {
            result.addAll(possibleObjectsFromNondetObjectWithDepth(i));
        }
        System.out.println("[Opal] generateSignaturesOfPossibleConstructorCallsFromNondetObject: "
                + result.size() + " signatures total");
        return result;
    }

    /**
     * Raw datum of a polymorphic method use.
     *
     * @param accessingClass   JVM type name of the class in which the method is used (x!0).
     * @param methodName       simple method name (x!1).
     * @param methodDescriptor JVM method descriptor, e.g. {@code ()V} (x!2).
     * @param declaringClass   JVM type name of the class that declares the method (x!3).
     */
    public record PolymorphyInformation(
            String accessingClass,
            String methodName,
            String methodDescriptor,
            String declaringClass
    ) {}

    // --------------------------------------------------
    //  Reachability from constructor seeds
    // --------------------------------------------------

    /**
     * Computes the transitive sub- and supertype closure of all constructor seed types.
     *
     * <p>First, {@link #collectConstructorSeedTypes(int)} gathers all types reachable directly at
     * {@code nondetObject} call-sites or as constructor parameters. The closure is then expanded
     * via {@code allSubtypes} and {@code allSupertypes} (both inclusive) over the full OPAL class
     * hierarchy — i.e. both project and library classes are included.
     *
     * @param depth maximum nesting depth for seed collection.
     * @return set of all types reachable from the constructor seeds.
     */
    public Set<ClassType> reachableTypesFromConstructors(int depth) {
        Set<ClassType> seeds = collectConstructorSeedTypes(depth);
        ClassHierarchy ch = this.project.classHierarchy();
        Set<ClassType> reachable = new HashSet<>(seeds);
        for (ClassType s : seeds) {
            ch.allSubtypes(s, true).foreach(t -> { reachable.add((ClassType) t); return null; });
            ch.allSupertypes(s, true).foreach(t -> { reachable.add((ClassType) t); return null; });
        }
        System.out.println("[Opal] reachableTypesFromConstructors: " + seeds.size() + " seeds → " + reachable.size() + " reachable");
        return reachable;
    }

    /**
     * Collects all types reachable as direct subtypes at {@code nondetObject} call-sites or as
     * constructor parameters up to depth {@code depth}.
     *
     * <p>Uses the same TAC dataflow analysis as
     * {@link #possibleObjectsFromNondetObjectWithDepth(int)}, but builds only a
     * {@code ClassType} set instead of constructor strings. Cycle protection is provided by the
     * {@code visited} set in {@link #collectSeedTypesRecursive}.
     *
     * @param depth maximum nesting depth for constructor parameters.
     * @return set of all seed types.
     */
    private Set<ClassType> collectConstructorSeedTypes(int depth) {
        Set<ClassType> seeds = new HashSet<>();
        Set<ClassType> visited = new HashSet<>();

        CallGraph cg = (CallGraph) this.project.get(RTACallGraphKey$.MODULE$);
        DeclaredMethods methods = (DeclaredMethods) this.project.get(DeclaredMethodsKey$.MODULE$);
        PropertyStore ps = (PropertyStore) this.project.get(PropertyStoreKey$.MODULE$);
        TypeIterator ti = (TypeIterator) this.project.get(TypeIteratorKey$.MODULE$);
        ContextProvider cp = (ContextProvider) this.project.get(ContextProviderKey$.MODULE$);

        MethodDescriptor md = MethodDescriptor$.MODULE$
                .apply("(Ljava/lang/Class;Ltools/aqua/concolic/ObjectFactory;)Ljava/lang/Object;");
        ClassType ct = ClassType.apply("tools/aqua/concolic/Verifier");
        DeclaredMethod nondetMethod = methods.apply(ct, "", ct, "nondetObject", md);

        cg.callersPropertyOf(nondetMethod).callContexts(nondetMethod, cp).iterator().foreach(callEdge -> {
            Context callerContext = callEdge._2();
            int pc = (Integer) callEdge._3();

            EOptionP<Method, TACAI> tacProperty =
                    ps.apply(callerContext.method().definedMethod(), TACAI$.MODULE$.key());
            TACode<TACMethodParameter, DUVar<ValueInformation>> tac = tacProperty.ub().tac().get();
            Stmt<DUVar<ValueInformation>>[] code = tac.stmts();

            Stmt<DUVar<ValueInformation>> callsite = code[tac.pcToIndex()[pc]];
            Call<DUVar<ValueInformation>> call =
                    (callsite instanceof MethodCall)
                            ? callsite.asMethodCall()
                            : callsite.asAssignmentLike().expr().asFunctionCall();

            DUVar<ValueInformation> param = call.params().apply(0).asVar();
            TypeIteratorState tis = new CGState(callerContext);

            AllocationsUtil$.MODULE$.handleAllocations(param, callerContext, null, code,
                    t -> true, () -> null,
                    (allocationContext, allocationPC, allocationCode) -> {
                        ClassType parameterType = allocationCode[(int) allocationPC]
                                .asAssignment().expr().asClassConst().value().asClassType();
                        collectSeedTypesRecursive(parameterType, depth, seeds, visited);
                        return null;
                    }, ti, tis, ps);
            return null;
        });

        System.out.println("[Opal] collectConstructorSeedTypes: " + seeds.size() + " seed types at depth=" + depth);
        return seeds;
    }

    /**
     * Recursive helper for {@link #collectConstructorSeedTypes(int)}.
     *
     * <p>Adds all subtypes of {@code paramType} (including itself) to {@code seeds}.
     * At {@code depth > 1}, the public constructors of each concrete, non-excluded subtype are
     * inspected and their reference parameters are followed recursively with {@code depth - 1}.
     * Already visited types are skipped via {@code visited} to prevent cycles.
     *
     * @param paramType type whose subtype tree should be added to {@code seeds}.
     * @param depth     remaining depth.
     * @param seeds     accumulating result set (in/out parameter).
     * @param visited   already processed types for cycle protection (in/out parameter).
     */
    private void collectSeedTypesRecursive(ClassType paramType, int depth, Set<ClassType> seeds, Set<ClassType> visited) {
        if (!visited.add(paramType)) return;

        scala.collection.Set<ClassType> subtypes = this.project.classHierarchy().allSubtypes(paramType, true);
        subtypes.foreach(tpe -> { seeds.add((ClassType) tpe); return null; });

        if (depth <= 1) return;

        subtypes.foreach(tpe -> {
            ClassType tpeClassType = (ClassType) tpe;
            if (tpeClassType.toJVMTypeName().equals("Ljava/lang/Object;")) return null;
            if (isExcludedFactoryClass(tpeClassType)) return null;
            if (this.project.classFile(tpeClassType).isEmpty()) return null;
            ClassFile cf = (ClassFile) this.project.classFile(tpeClassType).get();
            if (cf.isAbstract()) return null;

            cf.constructors().foreach(constructor -> {
                if (constructor.isPublic()) {
                    constructor.descriptor().parameterTypes().foreach(paramTpe -> {
                        if (paramTpe.isClassType()) {
                            collectSeedTypesRecursive(paramTpe.asClassType(), depth - 1, seeds, visited);
                        }
                        return null;
                    });
                }
                return null;
            });
            return null;
        });
    }
}
