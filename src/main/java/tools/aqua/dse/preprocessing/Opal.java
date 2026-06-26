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
import java.lang.Deprecated;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bindeglied zwischen dem OPAL-Framework und dem DSE-Preprocessing.
 *
 * <p>Die Klasse lädt beim Erzeugen das Bytecode-Projekt unter {@code classPath} zusammen mit der
 * Java-Standardbibliothek ({@code JavaBase}) in ein OPAL-{@link Project}. Daraus werden drei
 * statische SMT-Artefakte erzeugt:
 *
 * <ol>
 *   <li>Konstruktor-Signaturen für alle Subtypen, die an {@code Verifier.nondetObject}-Callsites
 *       erreichbar sind ({@link #generateSignaturesOfPossibleConstructorCallsFromNondetObject}).</li>
 *   <li>SMT-Definition {@code obj.extends} der Vererbungshierarchie
 *       ({@link #generateExtendsSummary(Set)}).</li>
 *   <li>SMT-Definition {@code obj.method.of} der Polymorphie-Tabelle
 *       ({@link #generatePolymorphismSummary(Set)}).</li>
 * </ol>
 *
 * <p>Die für die Summaries relevante Typmenge wird über
 * {@link #reachableTypesFromConstructors(int)} konstruktor-getrieben berechnet und schließt
 * sowohl Project- als auch Library-Klassen ein.
 */
public class Opal {
    private final Project project;

    /**
     * Lädt das Bytecode-Projekt unter {@code classPath} und die Java-Standardbibliothek in ein
     * OPAL-{@link Project}.
     *
     * <p>RTA-Callgraph und Klassenhierarchie werden lazy beim ersten Zugriff von OPAL aufgebaut.
     *
     * @param classPath Pfad zum Verzeichnis oder JAR, das die Project-Klassen enthält.
     */
    public Opal(String classPath) {
        System.out.println("[Opal] Loading project from: " + classPath);
        this.project = Project.apply(
                new File(classPath),
//                new File("/Users/mlazar/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home/jmods/java.base.jmod") //todo: more general
                org.opalj.bytecode.package$.MODULE$.JavaBase()
        );
        System.out.println("[Opal] Loaded " + this.project.projectClassFilesCount()
                + " project classes, " + this.project.libraryClassFilesCount() + " library classes");
    }

    /**
     * Prüft, ob {@code t} eine interne Factory-Hilfsklasse ist, die von Summaries ausgeschlossen
     * werden soll ({@code LFactories;} oder {@code LFactories$*Factory;}).
     *
     * @param t zu prüfender Typ.
     * @return {@code true}, wenn der Typ übersprungen werden soll.
     */
    private static boolean isExcludedFactoryClass(ClassType t) {
        String n = t.toJVMTypeName();
        return n.equals("LFactories;") || (n.startsWith("LFactories$") && n.endsWith("Factory;"));
    }

    /**
     * Liefert eine sortierte Liste aller Project-Klassen (ohne {@code Main} und Factory-Hilfsklassen).
     *
     * <p>Wird u.a. von der Legacy-Überladung {@link #generatePolymorphismSummary(List)} verwendet.
     * Für die konstruktor-getriebene Analyse steht {@link #reachableTypesFromConstructors(int)}
     * zur Verfügung.
     *
     * @return sortierte Liste von {@link KlassIdentifier}-Einträgen.
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
     * Erzeugt die SMT-Definition {@code obj.extends} eingeschränkt auf die übergebene erreichbare
     * Typmenge.
     *
     * <p>Jeder Typ in {@code reachable} erhält eine {@code null}-Kante sowie Kanten zu all seinen
     * transitiven Supertypen, sofern diese ebenfalls in {@code reachable} enthalten sind.
     * {@code java.lang.Object}, {@code Main} und Factory-Hilfsklassen werden ausgeschlossen.
     *
     * <p>Beispiel-Ausgabe (x!0 = Subtyp, x!1 = Supertyp):
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
     * @param reachable Typmenge, auf die die Ausgabe beschränkt wird (z.B. aus
     *                  {@link #reachableTypesFromConstructors(int)}).
     * @return SMT-LibS-String der {@code obj.extends}-Definition.
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
     * Erzeugt die SMT-Definition {@code obj.extends} auf Basis aller Project-Klassen.
     *
     * @return SMT-LibS-String der {@code obj.extends}-Definition.
     * @deprecated Verwendet ungefiltert alle Project-Klassen. Stattdessen
     *             {@link #generateExtendsSummary(Set)} mit der über
     *             {@link #reachableTypesFromConstructors(int)} berechneten Typmenge verwenden.
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
     * Sammelt für jeden Typ in {@code types} alle deklarierten (nicht geerbten, nicht
     * initialisierenden) Methoden und liefert sie als {@link PolymorphyInformation}-Liste.
     *
     * <p>{@code java.lang.Object} und Factory-Hilfsklassen werden übersprungen.
     *
     * @param types Array von Typen, für die Methodendeklarationen gesammelt werden.
     * @return Liste der gefundenen Polymorphie-Einträge.
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
     * Identifikator eines Branch-Knotens im Entscheidungsbaum des Polymorphie-Dispatches.
     *
     * @param branchId    laufende ID dieses Branches innerhalb aller polymorphen Methoden.
     * @param branchCount Gesamtanzahl der Branches (= Anzahl distinker polymorphen Methoden).
     */
    public record BranchData(int branchId,
                             int branchCount) {}

    /**
     * Eindeutiger Schlüssel einer polymorphen Methodendefinition bestehend aus Name,
     * JVM-Deskriptor und deklarierender Klasse.
     *
     * @param methodName       einfacher Methodenname.
     * @param methodDescriptor JVM-Methodendeskriptor, z.B. {@code (I)V}.
     * @param declaringClass   JVM-Typname der deklarierenden Klasse, z.B. {@code LA;}.
     */
    public record PolymorphicMethodDefinition(String methodName,
                                       String methodDescriptor,
                                       String declaringClass) {}

    /**
     * Erzeugt eine Map von polymorphen Methodendefinitionen auf ihre Branch-Daten.
     *
     * <p>Jede distinkte {@link PolymorphicMethodDefinition} in {@code rawInfos} erhält eine
     * eindeutige {@link BranchData#branchId()} sowie die Gesamtanzahl aller Branches als
     * {@link BranchData#branchCount()}.
     *
     * @param rawInfos Rohdaten aus {@link #collectPolymorphyInformation(ClassType[])}.
     * @return Map von Methodendefinition auf Branch-Metadaten.
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
     * Delegiert an {@link #collectPolymorphyInformation(ClassType[])} nach Konvertierung der
     * {@link KlassIdentifier}-Liste in ein {@code ClassType[]}-Array.
     *
     * @param types Liste von Klassen-Identifikatoren.
     * @return Liste der gefundenen Polymorphie-Einträge.
     */
    public List<PolymorphyInformation> collectPolymorphyInformation(List<KlassIdentifier> types) {
        ClassType[] classType = types.stream()
                .map(KlassIdentifier::shortIdentifier)
                .map(ClassType::apply)
                .toArray(ClassType[]::new);

        return collectPolymorphyInformation(classType);
    }
    
    /**
     * Erzeugt die SMT-Definition {@code obj.method.of} eingeschränkt auf die übergebene
     * erreichbare Typmenge.
     *
     * <p>Beispiel-Ausgabe (x!0 = aufrufende Klasse, x!1 = Methodenname,
     * x!2 = JVM-Deskriptor, x!3 = definierende Klasse):
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
     * @param reachable Typmenge, auf die die Ausgabe beschränkt wird (z.B. aus
     *                  {@link #reachableTypesFromConstructors(int)}).
     * @return SMT-LibS-String der {@code obj.method.of}-Definition.
     */
    public String generatePolymorphismSummary(Set<ClassType> reachable) {
        ClassType[] types = reachable.toArray(new ClassType[0]);
        return generatePolymorphismSummaryInternal(collectPolymorphyInformation(types));
    }

    /**
     * Erzeugt die SMT-Definition {@code obj.method.of} auf Basis einer {@link KlassIdentifier}-Liste.
     *
     * @param types Liste von Klassen-Identifikatoren.
     * @return SMT-LibS-String der {@code obj.method.of}-Definition.
     */
    public String generatePolymorphismSummary(List<KlassIdentifier> types) {
        return generatePolymorphismSummaryInternal(collectPolymorphyInformation(types));
    }

    /**
     * Gemeinsame Render-Logik für alle {@code generatePolymorphismSummary}-Überladungen.
     *
     * @param infos aufbereitete Polymorphie-Einträge.
     * @return SMT-LibS-String der {@code obj.method.of}-Definition.
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
     * Erzeugt alle Konstruktor-Signatur-Strings für die angegebenen Typen bis zur Nesting-Tiefe
     * {@code depth}.
     *
     * <p>Format einer Signatur: {@code Klasse|Deskriptor|{P1}{P2}...{Pn}}.
     * Primitive Parameter werden als leere Klammern {@code {}} kodiert. Referenz-Parameter werden
     * rekursiv expandiert. {@code null|NULL} steht für den Nullwert.
     *
     * <p>{@code java.lang.Object} wird als Sonderfall behandelt: es wird genau der
     * Default-Konstruktor {@code Ljava/lang/Object;|()V|} eingetragen.
     *
     * <p>Beispiel-Ausgabe für Typen {@code {B, C}}, Tiefe 1–3:
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
     * @param p     OPAL-Projekt für den Zugriff auf Klassendateien und Klassenhierarchie.
     * @param types Menge der zu betrachtenden Typen (typischerweise Subtypen eines Seed-Typs).
     * @param depth maximale Nesting-Tiefe für Referenz-Parameter.
     * @return Liste der generierten Konstruktor-Signatur-Strings inkl. {@code null|NULL}.
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
            if (tpe.packageName().startsWith("jdk") ||
                    tpe.packageName().startsWith("java") ||
                    tpe.packageName().startsWith("tools/aqua") ||
                    tpe.packageName().startsWith("sun")) {
                System.out.println("[Opal] generatedAllConstructors: skipping " + tpe.packageName());
                return null;
            }
            if (isExcludedFactoryClass(tpe)) {
                return null;
            }
            if (p.classFile(tpe).isEmpty()) {
                System.out.println("[Opal] generatedAllConstructors: skipping " + tpe.toJVMTypeName()
                        + " (not in project class files)");
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
     * Expandiert die Parameter eines Konstruktors zu einem Kreuzprodukt von Signatur-Suffixen.
     *
     * <p>Für jeden Parameter wird, abhängig von {@code depth} und Parametertyp, entweder
     * {@code {null|NULL}} (bei depth == 1 und Referenztyp), das Kreuzprodukt rekursiv erzeugter
     * Parameter-Signaturen (bei depth &gt; 1 und Referenztyp) oder {@code {}} (Primitiv) angehängt.
     * Bei depth &gt; 1 und rein primitiven Parametern wird eine leere Liste zurückgegeben, um
     * redundante Tiefenexpansionen zu vermeiden.
     *
     * <p>Überschreitet das Kreuzprodukt {@link #MAX_CONSTRUCTOR_SIGNATURES}, wird eine
     * {@link IllegalStateException} geworfen.
     *
     * @param p           OPAL-Projekt.
     * @param constructor der zu expandierende Konstruktor.
     * @param base        bereits aufgebautes Präfix der Form {@code Klasse|Deskriptor|}.
     * @param depth       verbleibende Nesting-Tiefe.
     * @return Liste von vollständigen Signatur-Strings mit expandierten Parametern.
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
     * Berechnet alle Konstruktor-Signaturen für eine konkrete Nesting-Tiefe {@code depth}.
     *
     * <p>Dazu wird über den RTA-Callgraph jede Callsite von
     * {@code Verifier.nondetObject(Class, ObjectFactory)} gesucht. Per TAC-Dataflow-Analyse wird
     * das {@code Class<?>}-Literal des ersten Arguments aufgelöst; anschließend werden alle
     * Subtypen des aufgelösten Typs ermittelt und via {@link #generatedAllConstructors} zu
     * Signaturen expandiert.
     *
     * @param depth Nesting-Tiefe für die Konstruktor-Expansion (muss &gt;= 1 sein).
     * @return Liste der generierten Konstruktor-Signatur-Strings für diese Tiefe.
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
     * Aggregiert Konstruktor-Signaturen über alle Tiefen von 1 bis einschließlich {@code depth}.
     *
     * <p>Ruft für jede Tiefe {@code i} (1 ≤ i ≤ depth) einmal
     * {@link #possibleObjectsFromNondetObjectWithDepth(int)} auf und vereinigt die Ergebnisse.
     * Duplikate werden vom Aufrufer ({@link tools.aqua.dse.preprocessing.ConstructorSummaryManager})
     * per {@code distinct()} entfernt.
     *
     * @param depth maximale Nesting-Tiefe.
     * @return kombinierte Liste aller Konstruktor-Signatur-Strings.
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
     * Rohdatum einer polymorphen Methodenverwendung.
     *
     * @param accessingClass   JVM-Typname der Klasse, in der die Methode verwendet wird (x!0).
     * @param methodName       einfacher Methodenname (x!1).
     * @param methodDescriptor JVM-Methodendeskriptor, z.B. {@code ()V} (x!2).
     * @param declaringClass   JVM-Typname der Klasse, die die Methode deklariert (x!3).
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
     * Berechnet die transitive Sub- und Supertyp-Hülle aller Konstruktor-Seed-Typen.
     *
     * <p>Zuerst werden via {@link #collectConstructorSeedTypes(int)} alle Typen gesammelt, die
     * an {@code nondetObject}-Callsites direkt oder als Konstruktor-Parameter erreichbar sind.
     * Danach wird die Hülle durch {@code allSubtypes} und {@code allSupertypes} (je inkl.
     * Selbst-Referenz) über die vollständige Opal-Klassenhierarchie expandiert — d.h. sowohl
     * Project- als auch Library-Klassen werden berücksichtigt.
     *
     * @param depth maximale Nesting-Tiefe für die Seed-Berechnung.
     * @return Menge aller von Konstruktor-Seeds aus erreichbarer Typen.
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
     * Sammelt alle Typen, die als direkte Subtypen an {@code nondetObject}-Callsites oder
     * als Konstruktor-Parameter bis Tiefe {@code depth} erreichbar sind.
     *
     * <p>Die Implementierung verwendet dieselbe TAC-Dataflow-Analyse wie
     * {@link #possibleObjectsFromNondetObjectWithDepth(int)}, erzeugt aber nur eine
     * {@code ClassType}-Menge statt Konstruktor-Strings. Der Cycle-Schutz wird durch das
     * {@code visited}-Set in {@link #collectSeedTypesRecursive} sichergestellt.
     *
     * @param depth maximale Nesting-Tiefe für Konstruktor-Parameter.
     * @return Menge aller Seed-Typen.
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
     * Rekursionshelfer für {@link #collectConstructorSeedTypes(int)}.
     *
     * <p>Fügt alle Subtypen von {@code paramType} (inkl. sich selbst) zu {@code seeds} hinzu.
     * Bei {@code depth > 1} werden für jeden konkreten, nicht ausgeschlossenen Subtyp dessen
     * öffentliche Konstruktoren inspiziert und deren Referenz-Parameter rekursiv mit
     * {@code depth - 1} weiterverfolgt. Bereits besuchte Typen werden via {@code visited}
     * übersprungen, um Zyklen zu verhindern.
     *
     * @param paramType Typ, dessen Subtyp-Baum zu {@code seeds} hinzugefügt werden soll.
     * @param depth     verbleibende Tiefe.
     * @param seeds     akkumulierende Ergebnismenge (In-/Out-Parameter).
     * @param visited   bereits verarbeitete Typen zum Cycle-Schutz (In-/Out-Parameter).
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
