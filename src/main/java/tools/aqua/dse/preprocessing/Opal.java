package tools.aqua.dse.preprocessing;

import org.apache.commons.io.IOUtils;
import org.opalj.br.*;
import org.opalj.br.analyses.DeclaredMethods;
import org.opalj.br.analyses.DeclaredMethodsKey$;
import org.opalj.br.analyses.Project;
import org.opalj.br.analyses.Project$;
import org.opalj.log.ConsoleOPALLogger;
import org.opalj.log.GlobalLogContext$;
import org.opalj.log.OPALLogger$;
import org.opalj.log.Warn$;
import org.opalj.br.reader.Java17Framework$;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** a line of the log with the time since the start of the JVM (the phases of OPAL can take long) */
    static void log(String message) {
        System.out.printf("[Opal +%.1fs] %s%n",
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0, message);
    }
    static {
        // OPAL logs on info level also from finalizers ("[info][project] finalized (LogContext(...))"), i.e., from
        // another thread at arbitrary times. These lines get interleaved with DSE's output, e.g. with the printed
        // decision tree that the tests and benchmarks parse. Only warnings and errors are relevant here.
        OPALLogger$.MODULE$.updateLogger(GlobalLogContext$.MODULE$, new ConsoleOPALLogger(true, Warn$.MODULE$));
    }

    private final Project project;
    private final File classPath;
    private final File javaBaseJmod;
    /** Same classes, but the library is loaded with method bodies; created on demand for the error analysis. */
    private Project<?> projectWithLibraryBodies;

    /**
     * Loads the bytecode project at {@code classPath} together with the Java standard library into
     * an OPAL {@link Project}.
     *
     * <p>The RTA call graph and class hierarchy are built lazily by OPAL on first access.
     *
     * @param classPath path to the directory or JAR containing the project classes.
     */
    public Opal(String classPath){
        this(classPath, extractBundledJavaBase());
    }

    /**
     * Loads the bytecode project at {@code classPath} with the given {@code java.base.jmod} as library.
     *
     * @param classPath    path to the directory or JAR containing the project classes.
     * @param javaBaseJmod the {@code java.base.jmod} file of a JDK.
     */
    public Opal(String classPath, File javaBaseJmod) {
        this.classPath = new File(classPath);
        this.javaBaseJmod = javaBaseJmod;
        log("loading " + classPath + " with the JDK classes of " + javaBaseJmod.getName());
        this.project = Project.apply(
                new File(classPath),
                javaBaseJmod
        );
        log("Loaded " + this.project.projectClassFilesCount()
                + " project classes, " + this.project.libraryClassFilesCount() + " library classes");
    }

    /** The bundled java.base.jmod, extracted once per JVM. */
    private static File bundledJavaBase;

    /**
     * Extracts the bundled {@code jmods/java.base.jmod} to the temporary directory, once for all runs: the file is
     * named by its size and reused if it exists (writing 20 MB per run, and leaving them behind when a run is killed,
     * fills small temporary directories). The file must keep the extension {@code .jmod}: OPAL selects the reader by
     * extension and silently ignores other files ("... is empty"), which leaves the project without library classes.
     */
    private static synchronized File extractBundledJavaBase() {
        if (bundledJavaBase != null && bundledJavaBase.length() > 0) {
            return bundledJavaBase;
        }
        java.net.URL resource = Opal.class.getClassLoader().getResource("jmods/java.base.jmod");
        if (resource == null) {
            throw new IllegalStateException("Resource jmods/java.base.jmod not found (see compile-dse.sh)");
        }
        try {
            long size = resource.openConnection().getContentLengthLong();
            File target = new File(System.getProperty("java.io.tmpdir"), "dse-java.base-" + size + ".jmod");
            if (size <= 0 || target.length() != size) {
                // write a private copy and move it into place (concurrent runs may extract at the same time)
                File tmpFile = Files.createTempFile("java.base", ".jmod").toFile();
                try (InputStream inStream = resource.openStream(); OutputStream outStream = new FileOutputStream(tmpFile)) {
                    IOUtils.copy(inStream, outStream);
                }
                if (size <= 0) {
                    tmpFile.deleteOnExit();
                    target = tmpFile;
                } else {
                    Files.move(tmpFile.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
            }
            log("java.base.jmod: " + target.getAbsolutePath());
            bundledJavaBase = target;
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Cannot read java.base.jmod due to IOException: " + e.toString());
        }
    }

    /**
     * Infers the errors that may escape each constructor occurring in the given signatures (root constructors and
     * nested constructor calls of arguments), see {@link ConstructorErrorAnalysis}.
     *
     * <p>The analysis runs on a second OPAL project in which the library ({@code java.base}) is loaded with method
     * bodies, so that language exceptions in reachable library code are found. The main project keeps loading the
     * library as interfaces only, which keeps the RTA call graph used elsewhere small.
     *
     * @param signatures constructor signatures as generated by
     *                   {@link #generateSignaturesOfPossibleConstructorCallsFromNondetObject(int)}.
     * @param mode       treatment of the places where the analysis cannot be precise.
     * @return errors per single constructor call, keyed by {@link StaticTypeFacts#constructorKey}.
     */
    public Map<String, ConstructorErrors> inferConstructorErrors(java.util.Collection<String> signatures,
                                                                 ConstructorErrorAnalysis.Mode mode) {
        return inferConstructorErrors(signatures, mode,
                ConstructorErrorAnalysis.DEFAULT_MAX_CALL_TARGETS, ConstructorErrorAnalysis.DEFAULT_MAX_METHODS);
    }

    /**
     * As {@link #inferConstructorErrors(java.util.Collection, ConstructorErrorAnalysis.Mode)}, with explicit bounds
     * on the number of call targets per call site and on the number of analyzed methods.
     */
    public Map<String, ConstructorErrors> inferConstructorErrors(java.util.Collection<String> signatures,
                                                                 ConstructorErrorAnalysis.Mode mode,
                                                                 int maxCallTargets, int maxMethods) {
        long start = System.currentTimeMillis();
        ConstructorErrorAnalysis analysis =
                new ConstructorErrorAnalysis(projectWithLibraryBodies(), mode, maxCallTargets, maxMethods);
        Map<String, ConstructorErrors> result = new HashMap<>();
        for (String raw : signatures) {
            ConstructorSignature signature = ConstructorSignature.parse(raw);
            if (signature.isNull()) {
                continue;
            }
            List<ConstructorSignature> all = new ArrayList<>(signature.nestedConstructors());
            all.add(signature);
            for (ConstructorSignature s : all) {
                result.computeIfAbsent(StaticTypeFacts.constructorKey(s),
                        k -> analysis.errorsOfConstructor(s.rootClass(), s.descriptor()));
            }
        }
        log("inferConstructorErrors: " + result.size() + " constructors, "
                + analysis.analyzedMethodCount() + " methods analyzed, " + (System.currentTimeMillis() - start) + "ms");
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Project<?> projectWithLibraryBodies() {
        if (this.projectWithLibraryBodies == null) {
            log("loading the project with the bodies of the JDK classes");
            Java17Framework$ reader = Java17Framework$.MODULE$;
            scala.collection.Iterable projectClassFiles = reader.ClassFiles(this.classPath, reader.defaultExceptionHandler());
            scala.collection.Iterable libraryClassFiles = reader.ClassFiles(this.javaBaseJmod, reader.defaultExceptionHandler());
            this.projectWithLibraryBodies = Project$.MODULE$.apply(projectClassFiles, libraryClassFiles, false);
            log("Loaded project with library bodies: "
                    + this.projectWithLibraryBodies.libraryClassFilesCount() + " library classes");
        }
        return this.projectWithLibraryBodies;
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
            log("extractKlassesFromClassPath: " + classNames.size() + " classes: "
                    + classNames.stream().map(KlassIdentifier::shortIdentifier).collect(Collectors.joining(", ")));
        } else {
            log("extractKlassesFromClassPath: " + classNames.size() + " classes");
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
        if(reachable.isEmpty()) {return "";}
        List<String[]> relation = extendsRelation(reachable);
        log("generateExtendsSummary: " + relation.size() + " extends relations");
        return renderExtendsSummary(relation);
    }

    /** Renders the {@code obj.extends} relation (pairs {@code {sub, super}}) as SMT-LIB definition. */
    public static String renderExtendsSummary(List<String[]> relation) {
        if (relation.isEmpty()) {
            // an empty (or) does not parse, the relation is just false
            return """
                (declare-fun obj.extends (String String) Bool)
                (assert (forall ((x!0 String) (x!1 String)) (not (obj.extends x!0 x!1))))
                """;
        }
        List<String> terms = new ArrayList<>();
        for (String[] pair : relation) {
            terms.add(String.format("(and (= x!0 \"%s\")  (= x!1 \"%s\"))", pair[0], pair[1]));
        }
        return "(declare-fun obj.extends (String String) Bool)\n"
                + "(assert (forall ((x!0 String) (x!1 String))\n"
                + "(= (obj.extends x!0 x!1)\n"
                + "(ite " + balancedOr(terms) + " true false)\n"
                + ")))\n";
    }

    /**
     * Disjunction of {@code terms} as a balanced tree of binary {@code or}s. The SMT-LIB parser turns an n-ary
     * {@code (or t1 ... tn)} into a chain of nested binary disjunctions of depth n, which overflows the stack of
     * the recursive translation to Z3 for large class hierarchies; a balanced tree has depth log n.
     */
    static String balancedOr(List<String> terms) {
        if (terms.size() == 1) {
            return terms.get(0);
        }
        int mid = terms.size() / 2;
        return "(or " + balancedOr(terms.subList(0, mid)) + "\n " + balancedOr(terms.subList(mid, terms.size())) + ")";
    }

    /**
     * Computes the pairs {@code (sub, super)} of the {@code obj.extends} relation restricted to {@code reachable}.
     *
     * <p>This is the single source of truth for the relation: {@link #generateExtendsSummary(Set)} renders it as
     * SMT, and the static exclusion of constructor summaries ({@link SummaryRelevance}) evaluates it directly, so
     * both always agree. Every type receives a {@code null} edge as well as edges to all its transitive supertypes
     * that are contained in {@code reachable}. {@code java.lang.Object}, {@code Main}, and factory helpers are
     * excluded.
     *
     * @param reachable type set to restrict the relation to.
     * @return list of {@code {subTypeName, superTypeName}} pairs (JVM type names, {@code "null"} for null).
     */
    public List<String[]> extendsRelation(Set<ClassType> reachable) {
        List<String[]> relation = new ArrayList<>();
        ClassHierarchy ch = this.project.classHierarchy();
        // java.lang.Object is a supertype of every class (and of null): it occurs as type bound (nondetObject(
        // Object.class)), and Object() is a candidate constructor (see generatedAllConstructors). Main has a public
        // constructor and is a candidate as well.
        ClassType object = ClassType.Object();
        for (ClassType subType : reachable) {
            String subTypeName = subType.toJVMTypeName();
            if (isExcludedFactoryClass(subType)) {
                continue;
            }

            // Add null relation
            relation.add(new String[] {"null", subTypeName});

            ch.allSupertypes(subType, true).foreach(superType -> {
                String superTypeName = superType.toJVMTypeName();
                if (!isExcludedFactoryClass(superType) && reachable.contains(superType) && !superType.equals(object)) {
                    relation.add(new String[] {subTypeName, superTypeName});
                }
                return null;
            });
            relation.add(new String[] {subTypeName, object.toJVMTypeName()});
        }
        if (!reachable.contains(object)) {
            relation.add(new String[] {"null", object.toJVMTypeName()});
        }
        return relation;
    }

    /**
     * Rows of {@code obj.extends} for a class that is not in the static relation, e.g. a JDK class assigned to a
     * field by a constructor ({@code java.util.HashSet} in a field of type {@code Collection}): the class extends all
     * its supertypes (known from the class hierarchy, which includes the JDK), and {@code null} extends them, too.
     *
     * @param jvmTypeName e.g. {@code Ljava/util/HashSet;}.
     */
    public List<String[]> extendsRowsFor(String jvmTypeName) {
        List<String[]> rows = new ArrayList<>();
        String object = ClassType.Object().toJVMTypeName();
        rows.add(new String[] {jvmTypeName, jvmTypeName});
        rows.add(new String[] {jvmTypeName, object});
        if (!jvmTypeName.startsWith("L") || !jvmTypeName.endsWith(";")) {
            return rows;
        }
        ClassType type = ClassType.apply(jvmTypeName.substring(1, jvmTypeName.length() - 1));
        ClassHierarchy ch = this.project.classHierarchy();
        if (!ch.isKnown(type)) {
            return rows;
        }
        ch.allSupertypes(type, true).foreach(superType -> {
            String superTypeName = superType.toJVMTypeName();
            rows.add(new String[] {jvmTypeName, superTypeName});
            rows.add(new String[] {"null", superTypeName});
            return null;
        });
        return rows;
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

        log("generateExtendsSummary: " + relationCount[0] + " extends relations");
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

        // group the declared methods by class once (instead of filtering all of them for every type)
        Map<ClassType, List<DeclaredMethod>> methodsByClass = new HashMap<>();
        methods.declaredMethods().foreach(m -> {
            if (m.hasSingleDefinedMethod() && !m.asDefinedMethod().definedMethod().isInitializer()) {
                methodsByClass.computeIfAbsent(m.declaringClassType(), k -> new ArrayList<>()).add(m);
            }
            return null;
        });

        for (ClassType type : types) {
            if (isExcludedFactoryClass(type)) continue;
            if (type.equals(ClassType.Object())) {
                // objects of class Object can be created (constructor Object()): its own methods
                addObjectMethods(polymorphicInfos);
                continue;
            }
            // only project classes can be the class of a concolic object (library supertypes are in the types for
            // obj.extends); their entries include the methods they inherit from library classes
            if (!this.project.isProjectType(type)) continue;

            for (DeclaredMethod method : methodsByClass.getOrDefault(type, List.of())) {
                polymorphicInfos.add(new PolymorphyInformation(
                        type.toJVMTypeName(),
                        method.name(),
                        method.descriptor().toJVMDescriptor(),
                        method.asDefinedMethod().definedMethod().declaringClassFile().thisType().toJVMTypeName()
                ));
            }
            addInheritedLibraryMethods(type, polymorphicInfos);
        }
        log("collectPolymorphyInformation: " + types.length + " types -> "
                + polymorphicInfos.size() + " methods");
        return polymorphicInfos;
    }


    /** Adds the entries of class {@code java.lang.Object}: its non-static, non-private methods, declared in Object. */
    private void addObjectMethods(List<PolymorphyInformation> infos) {
        scala.Option<ClassFile> objectFile = this.project.classFile(ClassType.Object());
        if (objectFile.isEmpty()) {
            return;
        }
        String name = ClassType.Object().toJVMTypeName();
        objectFile.get().methods().foreach(m -> {
            if (!m.isStatic() && !m.isPrivate() && !m.isInitializer()) {
                infos.add(new PolymorphyInformation(name, m.name(), m.descriptor().toJVMDescriptor(), name));
            }
            return null;
        });
    }

    /**
     * Adds the methods that {@code type} inherits from library supertypes (in particular {@code java.lang.Object},
     * e.g. {@code toString}, {@code hashCode}, {@code equals}) and that are not already listed for {@code type}.
     * {@link DeclaredMethods} does not contain these, but SPouT records {@code obj.method.of} decisions for them as
     * for any other virtual call (e.g. {@code (obj.method.of o.cls "toString" "()Ljava/lang/String;"
     * "Ljava/lang/Object;")} when an object is printed).
     */
    private void addInheritedLibraryMethods(ClassType type, List<PolymorphyInformation> infos) {
        String typeName = type.toJVMTypeName();
        Set<String> listed = new HashSet<>();
        for (PolymorphyInformation info : infos) {
            if (info.accessingClass().equals(typeName)) {
                listed.add(info.methodName() + info.methodDescriptor());
            }
        }
        this.project.classHierarchy().allSupertypes(type, false).foreach(superType -> {
            if (this.project.isProjectType(superType) || this.project.classFile(superType).isEmpty()) {
                return null;
            }
            ClassFile superFile = (ClassFile) this.project.classFile(superType).get();
            superFile.methods().foreach(m -> {
                if (m.isStatic() || m.isPrivate() || m.isInitializer()) {
                    return null;
                }
                String key = m.name() + m.descriptor().toJVMDescriptor();
                if (!listed.add(key)) {
                    return null;
                }
                scala.Option<Method> resolved = this.project.resolveMethodReference(
                        type, m.name(), m.descriptor(), superFile.isInterfaceDeclaration());
                if (resolved.isDefined()) {
                    infos.add(new PolymorphyInformation(typeName, m.name(), m.descriptor().toJVMDescriptor(),
                            resolved.get().classFile().thisType().toJVMTypeName()));
                }
                return null;
            });
            return null;
        });
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

        // A call of m with descriptor d dispatches to one of the definitions of m/d, so the dispatch decision has one
        // branch per definition of m/d (not per polymorphic method of the program: with loose type bounds, these are
        // thousands, and every sibling becomes an open target that needs its own solver query).
        Map<String, List<PolymorphicMethodDefinition>> bySignature = countsMap.keySet().stream()
                .sorted(Comparator.comparing(PolymorphicMethodDefinition::declaringClass))
                .collect(Collectors.groupingBy(def -> def.methodName() + def.methodDescriptor(),
                        LinkedHashMap::new, Collectors.toList()));

        HashMap<PolymorphicMethodDefinition, BranchData> result = new HashMap<>();
        for (List<PolymorphicMethodDefinition> definitions : bySignature.values()) {
            for (int i = 0; i < definitions.size(); i++) {
                result.put(definitions.get(i), new BranchData(i, definitions.size()));
            }
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
    static String generatePolymorphismSummaryInternal(List<PolymorphyInformation> infos) {
        if (infos.isEmpty()) {
            // e.g. classes without methods: an empty (or) does not parse, the relation is just false
            return """
                (declare-fun obj.method.of (String String String String) Bool)
                (assert (forall ((x!0 String) (x!1 String) (x!2 String) (x!3 String))
                (not (obj.method.of x!0 x!1 x!2 x!3))))
                """;
        }
        List<String> terms = new ArrayList<>();
        for (PolymorphyInformation info : infos) {
            terms.add(String.format("(and (= x!0 \"%s\") (= x!1 \"%s\") (= x!2 \"%s\") (= x!3 \"%s\"))",
                    info.accessingClass(), info.methodName(), info.methodDescriptor(), info.declaringClass()));
        }
        return "(declare-fun obj.method.of (String String String String) Bool)\n"
                + "(assert (forall ((x!0 String) (x!1 String) (x!2 String) (x!3 String))\n"
                + "(= (obj.method.of x!0 x!1 x!2 x!3)\n"
                + "(ite " + balancedOr(terms) + " true false)\n"
                + ")))\n";
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
        log("generatedAllConstructors: " + types.size() + " types, depth=" + depth);
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
//                log("generatedAllConstructors: skipping " + tpe.packageName());
//                return null;
//            }
            if (isExcludedFactoryClass(tpe)) {
                return null;
            }
            if (!p.isProjectType(tpe)) {
                log("generatedAllConstructors: skipping non-project type "
                        + tpe.toJVMTypeName());
                return null;
            }
            if (p.classFile(tpe).isEmpty()) {
                log("generatedAllConstructors: skipping " + tpe.toJVMTypeName()
                        + " (no class file)");
                return null;
            }
            ClassFile cf = (ClassFile) p.classFile(tpe).get();
            if (cf.isAbstract() || isSyntheticLambdaClass(cf)) {
                return null;
            }
            cf.constructors().foreach(constructor -> {
                if (constructor.isPublic() && !hasStringParameter(constructor) && !hasArrayParameter(constructor)) {
                    String base = String.format("%s|%s|", tpe.toJVMTypeName(), constructor.descriptor().toJVMDescriptor());
                    result.addAll(generateParametersString(p, constructor, base, depth));
                }
                return null;
            });
            return null;
        });
        return result;
    }

    private static final ClassType STRING = ClassType.apply("java/lang/String");

    /**
     * Constructors with {@code String} parameters are not used for concolic objects: SPouT treats strings like
     * primitive values and does not support them as symbolic objects, so such a parameter could only be
     * {@code null} (and, e.g., {@code Integer.parseInt(null)} in SPouT raises a host exception that aborts the
     * analyzed program).
     */
    private static boolean hasStringParameter(Method constructor) {
        return constructor.descriptor().parameterTypes().exists(t -> t.equals(STRING));
    }

    /**
     * Constructors with array parameters are not used for concolic objects: SPouT cannot pass arrays as constructor
     * arguments (and the signature format has no encoding for them).
     */
    private static boolean hasArrayParameter(Method constructor) {
        return constructor.descriptor().parameterTypes().exists(Type::isArrayType);
    }

    /**
     * OPAL rewrites invokedynamic (lambdas) into synthetic classes (e.g. {@code Foo$bar()...$Lambda}) that do not
     * exist at run time and cannot be instantiated by SPouT.
     */
    private static boolean isSyntheticLambdaClass(ClassFile cf) {
        return cf.isSynthetic() || cf.thisType().toJVMTypeName().contains("$Lambda");
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
        log("possibleObjectsFromNondetObjectWithDepth: depth=" + depth);

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

                                log("  call-site #" + callSiteIndex[0]
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

        log("nondetObject call-sites processed: " + callSiteIndex[0]);
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
        log("generateSignaturesOfPossibleConstructorCallsFromNondetObject: depth=" + depth);
        List<String> result = new ArrayList<>();
        for(int i = 1; i <= depth; i++) {
            result.addAll(possibleObjectsFromNondetObjectWithDepth(i));
        }
        log("generateSignaturesOfPossibleConstructorCallsFromNondetObject: "
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
            // concolic objects are only created with constructors of project classes: library subtypes (e.g. all JDK
            // classes for the bound Object) cannot occur, library supertypes are needed for type tests
            ch.allSubtypes(s, true).foreach(t -> {
                if (this.project.isProjectType((ClassType) t)) reachable.add((ClassType) t);
                return null;
            });
            ch.allSupertypes(s, true).foreach(t -> { reachable.add((ClassType) t); return null; });
        }
        log("reachableTypesFromConstructors: " + seeds.size() + " seeds → " + reachable.size() + " reachable");
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

        log("collectConstructorSeedTypes: " + seeds.size() + " seed types at depth=" + depth);
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

        // only project types can be instantiated (see generatedAllConstructors); the bound itself is kept for type tests
        scala.collection.Set<ClassType> subtypes = this.project.classHierarchy().allSubtypes(paramType, true);
        seeds.add(paramType);
        subtypes.foreach(tpe -> {
            if (this.project.isProjectType((ClassType) tpe)) seeds.add((ClassType) tpe);
            return null;
        });

        if (depth <= 1) return;

        subtypes.foreach(tpe -> {
            ClassType tpeClassType = (ClassType) tpe;
            if (!this.project.isProjectType(tpeClassType)) return null;
            if (tpeClassType.toJVMTypeName().equals("Ljava/lang/Object;")) return null;
            if (isExcludedFactoryClass(tpeClassType)) return null;
            if (this.project.classFile(tpeClassType).isEmpty()) return null;
            ClassFile cf = (ClassFile) this.project.classFile(tpeClassType).get();
            if (cf.isAbstract() || isSyntheticLambdaClass(cf)) return null;

            cf.constructors().foreach(constructor -> {
                if (constructor.isPublic() && !hasStringParameter(constructor) && !hasArrayParameter(constructor)) {
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
