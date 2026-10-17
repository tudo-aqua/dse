package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.smtlibUtility.smtconverter.SMTLibExportGenContext;
import gov.nasa.jpf.constraints.smtlibUtility.smtconverter.SMTLibExportVisitor;
import gov.nasa.jpf.constraints.types.BitLimitedBVIntegerType;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstructorSummaryManager {
    private final String classPath;
    private final int depth;
    private final Opal opal;
    private final Set<String> declarationsOfBluePrint = new HashSet<>();
    /** eager mode: disjunction of all summaries; {@code null} in incremental mode */
    private final String bluePrintConstructorSummaries;
    /** eager mode, not loaded from the cache: the summaries of all signatures */
    private List<GeneratedSummary> eagerSummaries;
    private final List<String> possibleErrorsWithInConstructors = new ArrayList<>();
    private final List<String> objectsWithinAllTraces = new ArrayList<>();

    /** incremental mode only (otherwise {@code null}) */
    private final SummaryStore summaryStore;
    private final SummaryRelevance relevance;
    private final int batchSize;

    /**
     * Options for incremental summaries.
     *
     * @param batchSize      number of signatures summarized per refinement step.
     * @param errorMode      mode of the static constructor error inference.
     * @param maxCallTargets bound for the error inference, see {@link ConstructorErrorAnalysis}.
     * @param maxMethods     bound for the error inference, see {@link ConstructorErrorAnalysis}.
     */
    public record IncrementalOptions(int batchSize,
                                     ConstructorErrorAnalysis.Mode errorMode,
                                     int maxCallTargets,
                                     int maxMethods) {}

    public ConstructorSummaryManager(String classPath, int depth) {
        this(classPath, depth, false);
    }

    public ConstructorSummaryManager(String classPath, int depth, boolean useCache) {
        this.classPath = classPath;
        System.out.println("Opal class Path: "+classPath);
        this.opal = new Opal(classPath);
        this.depth = depth;
        this.summaryStore = null;
        this.relevance = null;
        this.batchSize = 0;
        if (useCache && cacheExists()) {
            System.out.println("ConstructorBluePrint cache found — loading from disk");
            this.bluePrintConstructorSummaries = loadBluePrintFromCache();
            System.out.println("ConstructorBluePrint loaded from cache");
        } else {
            System.out.println("generateSmtCodeBluePrintForConstructorSelection-Call");
            this.bluePrintConstructorSummaries = this.generateSmtCodeBluePrintForConstructorSelection();
            System.out.println("generateSmtCodeBluePrintForConstructorSelection-Done");
            if (useCache) {
                writeBluePrintToCache(this.bluePrintConstructorSummaries);
                System.out.println("ConstructorBluePrint written to cache");
            }
        }
        extractErrorWithinConstructors();
    }


    /**
     * Incremental mode: only the signature catalogue, the static error list, and the static type facts are computed
     * up front; of the summaries, only the one of {@code null|NULL} is computed. All other summaries are computed on
     * demand, see {@link #startQuery(List)} and {@link #summarizeNextBatch(Query)}.
     */
    public ConstructorSummaryManager(String classPath, int depth, StaticManager staticManager,
                                     IncrementalOptions options) {
        this.classPath = classPath;
        System.out.println("Opal class Path: "+classPath);
        this.opal = new Opal(classPath);
        this.depth = depth;
        this.batchSize = options.batchSize();
        this.bluePrintConstructorSummaries = null;

        List<String> signatures = new ArrayList<>(List.of(ConstructorSignature.NULL_SIGNATURE));
        this.opal.generateSignaturesOfPossibleConstructorCallsFromNondetObject(this.depth).stream()
                .filter(sig -> !signatures.contains(sig))
                .forEach(signatures::add);
        System.out.println("[ConstructorSummaryManager] incremental mode, " + signatures.size() + " signatures");

        Map<String, ConstructorErrors> errors = this.opal.inferConstructorErrors(signatures,
                options.errorMode(), options.maxCallTargets(), options.maxMethods());
        Set<String> allErrors = new TreeSet<>();
        errors.values().forEach(e -> allErrors.addAll(e.types()));
        this.possibleErrorsWithInConstructors.addAll(allErrors);
        System.out.println("[ConstructorSummaryManager] static constructor errors: " + allErrors);

        StaticTypeFacts facts = new StaticTypeFacts(staticManager.getExtendsRelation(),
                staticManager.getPolymorphicInformation(), errors);
        this.relevance = new SummaryRelevance(facts);
        this.summaryStore = new SummaryStore(signatures, this::summarizeSignature);
        this.summaryStore.ensureSummarized(List.of(ConstructorSignature.parse(ConstructorSignature.NULL_SIGNATURE)));
    }

    /** For tests: incremental mode with a given store and relevance check, without OPAL. */
    ConstructorSummaryManager(SummaryStore store, SummaryRelevance relevance, List<String> errors, int batchSize) {
        this.classPath = null;
        this.opal = null;
        this.depth = 0;
        this.bluePrintConstructorSummaries = null;
        this.summaryStore = store;
        this.relevance = relevance;
        this.batchSize = batchSize;
        this.possibleErrorsWithInConstructors.addAll(errors);
        this.summaryStore.ensureSummarized(List.of(ConstructorSignature.parse(ConstructorSignature.NULL_SIGNATURE)));
    }

    /** @return the number of constructor signatures summarized so far (eager: all, computed up front), -1 if unknown */
    public int summarizedCount() {
        if (this.summaryStore != null) {
            return this.summaryStore.summarizedCount();
        }
        return this.eagerSummaries != null ? this.eagerSummaries.size() : -1;
    }

    /** @return the number of constructor signatures that can be summarized (incremental mode), -1 if unknown */
    public int signatureCount() {
        return this.summaryStore != null ? this.summaryStore.catalogue().size()
                : this.eagerSummaries != null ? this.eagerSummaries.size() : -1;
    }

    public boolean isIncremental() {
        return this.summaryStore != null;
    }

    /**
     * Refinement loop of incremental mode: solves {@code path} with the summaries computed so far (restricted per
     * object to the signatures that are not statically excluded); on UNSAT, summarizes the next batch of needed
     * signatures and solves again. UNSAT is only returned once every needed signature has been summarized, so it
     * is exact. The context must already contain the static SMT code (class hierarchy, polymorphism); it is left
     * unchanged.
     */
    public ConstraintSolver.Result solve(SolverContext solverCtx, List<Expression<Boolean>> path, Valuation val) {
        Query query = startQuery(path);

        // every base object needs a constructor: if one has no relevant signature, no summary can help
        for (Map.Entry<String, List<ConstructorSignature>> e : query.relevant().entrySet()) {
            if (e.getValue().isEmpty()) {
                System.out.println("[ConstructorSummaryManager] UNSAT without solving: no signature can construct "
                        + e.getKey());
                return ConstraintSolver.Result.UNSAT;
            }
        }

        int round = 0;
        checkedComplete = 0;
        while (true) {
            round++;
            String dynamicSmtLibCode = generateIncrementalSMTLibCode(query);
            SmtProblemManager.printSmtProblem(dynamicSmtLibCode);

            solverCtx.push();
            ConstraintSolver.Result res;
            try {
                SmtProblemManager.addSmtProblemAsString(dynamicSmtLibCode, solverCtx);
                solverCtx.add(path);
                System.out.println("\033[35mSolve SMT-problem (round " + round + ")");
                res = solverCtx.solve(val);
            } finally {
                solverCtx.pop();
            }

            if (res != ConstraintSolver.Result.UNSAT) {
                return res;
            }
            Set<String> complete = completeObjects(query);
            if ((round == 1 || complete.size() > checkedComplete) && isUnsatOverApproximated(solverCtx, path, query, complete)) {
                System.out.println("[ConstructorSummaryManager] UNSAT with summaries of " + complete
                        + " only, other objects unconstrained");
                return res;
            }
            checkedComplete = complete.size();
            if (System.currentTimeMillis() >= this.deadline) {
                System.out.println("[ConstructorSummaryManager] dse.timeout reached, query unknown");
                return ConstraintSolver.Result.DONT_KNOW;
            }
            if (summarizeNextBatch(query) == 0) {
                return res;
            }
        }
    }

    /** time budget of the DSE of a single constructor (dse.summaries.timeout), in seconds */
    private static long summaryTimeoutSeconds = 60;

    /** set before creating a manager: in eager mode, all summaries are computed by the constructor */
    public static void setSummaryTimeoutSeconds(long seconds) {
        summaryTimeoutSeconds = seconds;
    }

    /** end of the exploration (dse.timeout): no summaries are computed after it */
    private long deadline = Long.MAX_VALUE;

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    /** number of complete objects at the last over-approximation check of the current query */
    private int checkedComplete;

    /** @return the base objects of the query whose relevant signatures are all summarized. */
    private Set<String> completeObjects(Query query) {
        Set<String> complete = new LinkedHashSet<>();
        for (String object : query.baseIdentifiers()) {
            if (this.summaryStore.unsummarized(query.relevant().get(object)).isEmpty()) {
                complete.add(object);
            }
        }
        return complete;
    }

    /**
     * Over-approximation of the query: objects whose relevant signatures are all summarized are constrained to their
     * summaries, all other objects are unconstrained (summaries only add constraints on objects). If this is UNSAT,
     * the query is UNSAT for any set of summaries, and no further summary needs to be computed. Without complete
     * objects, this checks whether the path is unsatisfiable independently of constructors (e.g., {@code o != o}).
     * The distinctness of objects does not depend on summaries and is included.
     */
    private boolean isUnsatOverApproximated(SolverContext solverCtx, List<Expression<Boolean>> path, Query query,
                                            Set<String> complete) {
        Map<String, List<GeneratedSummary>> summariesPerObject = new LinkedHashMap<>();
        for (String object : complete) {
            summariesPerObject.put(object, this.summaryStore.summaries(query.relevant().get(object)));
        }
        solverCtx.push();
        try {
            SmtProblemManager.addSmtProblemAsString(
                    renderObjectSummaries(summariesPerObject, query.pathSorts(), query.objectIdentifiers()), solverCtx);
            solverCtx.add(path);
            return solverCtx.solve(new Valuation()) == ConstraintSolver.Result.UNSAT;
        } finally {
            solverCtx.pop();
        }
    }

    /**
     * The constructor-summary part of one solver query in incremental mode (one open node of the constraints tree).
     *
     * @param objectIdentifiers all object identifiers of the path (as in eager mode, for distinctness constraints).
     * @param baseIdentifiers   the top-level objects, each of which needs a constructor.
     * @param relevant          per base object: the signatures not statically excluded for it, simplest first.
     * @param needed            union of {@code relevant} in the order in which missing summaries are computed.
     * @param pathSorts         SMT sorts of the variables of the path, by name.
     */
    public record Query(List<String> objectIdentifiers,
                        List<String> baseIdentifiers,
                        Map<String, List<ConstructorSignature>> relevant,
                        List<ConstructorSignature> needed,
                        Map<String, String> pathSorts) {}

    public Query startQuery(List<Expression<Boolean>> path) {
        List<String> objectIdentifiers = extractObjectIdentifiers(extractFreeVariables(path));
        Pattern baseClassPattern = Pattern.compile("__object_\\d+$");
        List<String> baseIdentifiers = objectIdentifiers.stream()
                .filter(identifier -> baseClassPattern.matcher(identifier).find())
                .toList();

        Map<String, List<ConstructorSignature>> relevant = new LinkedHashMap<>();
        for (String object : baseIdentifiers) {
            relevant.put(object, this.relevance.relevantFor(path, object, this.summaryStore.catalogue()));
        }
        List<ConstructorSignature> needed =
                this.relevance.needed(path, baseIdentifiers, this.summaryStore.catalogue());

        System.out.println("[ConstructorSummaryManager] query: objects " + baseIdentifiers + ", "
                + needed.size() + " of " + this.summaryStore.catalogue().size() + " signatures relevant, "
                + this.summaryStore.unsummarized(needed).size() + " of them not summarized yet");
        return new Query(objectIdentifiers, baseIdentifiers, relevant, needed, pathSorts(path));
    }

    /**
     * SMT-LIB code for the query: declarations, and per base object the disjunction of the summaries computed so
     * far among its relevant signatures ({@code (or false)} if there is none yet), and distinctness of objects.
     */
    public String generateIncrementalSMTLibCode(Query query) {
        Map<String, List<GeneratedSummary>> summariesPerObject = new LinkedHashMap<>();
        for (String object : query.baseIdentifiers()) {
            summariesPerObject.put(object, this.summaryStore.summaries(query.relevant().get(object)));
        }
        return renderObjectSummaries(summariesPerObject, query.pathSorts(), query.objectIdentifiers());
    }

    /** a nullary declaration; the sort is a symbol (e.g. {@code Int}) or one parenthesized term ({@code (_ BitVec 32)}) */
    private static final Pattern DECLARATION =
            Pattern.compile("\\(declare-fun\\s+(\\S+)\\s+\\(\\)\\s+(\\([^()]*\\)|[^\\s()]+)\\s*\\)");

    /**
     * a declaration of a function with arguments: the fields and array elements of the heap model
     * ({@code (declare-fun fld.LBox;.x (Int) (_ BitVec 32))}), see {@link #HEAP_FUNCTIONS}
     */
    private static final Pattern FUNCTION_DECLARATION = Pattern.compile(
            "\\(declare-fun\\s+(\\S+)\\s+\\(((?:[^()]|\\([^()]*\\))+)\\)\\s+(\\([^()]*\\)|[^\\s()]+)\\s*\\)");

    /**
     * Functions of the heap model of SPouT: objects are identities (sort {@code Int}), {@code obj.cls} is the class
     * of an object, {@code obj.owner} the nondet object whose construction allocated it ({@code -1} for objects held
     * by static fields, which existed before), {@code obj.idx} its index among the objects of the owner ({@code 0}
     * for the nondet object). Summaries name the objects of a constructed heap {@code __heap_0_j}; they are renamed
     * to {@code __heap_k_j} for object {@code __object_k}.
     */
    static final String HEAP_FUNCTIONS = "(declare-fun obj.cls (Int) String)\n"
            + "(declare-fun obj.owner (Int) (_ BitVec 32))\n"
            + "(declare-fun obj.idx (Int) (_ BitVec 32))\n";

    /** renames the template object {@code __object_0} and its heap objects {@code __heap_0_j} to the given object */
    static String renameObject(String text, String object) {
        String heap = "__heap_" + object.substring("__object_".length()) + "_";
        return text.replace("__object_0", object).replace("__heap_0_", heap);
    }

    /**
     * SMT-LIB code that constrains each object to one of its summaries: declarations, per object
     * {@code (assert (or <summary paths>))}, and distinctness of objects.
     *
     * <p>SPouT names field symbols by object and field name only ({@code __object_0.value}). Summaries of
     * different classes can therefore declare the same symbol with different sorts (e.g. {@code value} of
     * {@code MutableInt} and of {@code MutableBoolean}), which cannot be expressed in one SMT problem. Each symbol
     * gets one sort, the sort of the path constraint if the path uses it and the first sort seen otherwise. In
     * summaries that use the symbol with another sort, it is renamed (e.g. {@code __object_0.value__as_Bool}): such
     * a summary describes an object of another class, for which the path cannot read the field without first
     * checking the class (type test, cast, or dispatch).
     */
    private String renderObjectSummaries(Map<String, List<GeneratedSummary>> summariesPerObject,
                                         Map<String, String> pathSorts, List<String> objectIdentifiers) {
        Map<String, String> declared = new HashMap<>();
        StringBuilder declarations = new StringBuilder();
        StringBuilder assertions = new StringBuilder();
        for (Map.Entry<String, List<GeneratedSummary>> e : summariesPerObject.entrySet()) {
            String object = e.getKey();
            declarations.append(String.format("(declare-fun %s.init () String)%n", object));
            declared.put(object + ".init", "String");
            List<String> disjuncts = new ArrayList<>();
            for (GeneratedSummary summary : e.getValue()) {
                Map<String, String> renaming = new HashMap<>();
                for (String declaration : summary.declarations()) {
                    Matcher f = FUNCTION_DECLARATION.matcher(declaration);
                    while (f.find()) {
                        if (!declared.containsKey(f.group(1))) {
                            declared.put(f.group(1), f.group(3));
                            declarations.append(f.group(0)).append('\n');
                        }
                    }
                    Matcher m = DECLARATION.matcher(declaration);
                    while (m.find()) {
                        String name = renameObject(m.group(1), object);
                        String sort = m.group(2).trim();
                        String canonical = pathSorts.getOrDefault(name, declared.getOrDefault(name, sort));
                        String symbol = name;
                        if (!normalizeSort(canonical).equals(normalizeSort(sort))) {
                            symbol = name + "__as_" + sort.replaceAll("[^A-Za-z0-9]", "");
                            renaming.put(name, symbol);
                        }
                        if (!declared.containsKey(symbol)) {
                            declared.put(symbol, sort);
                            declarations.append(String.format("(declare-fun %s () %s)%n", symbol, sort));
                        }
                    }
                }
                for (String formula : summary.formulas()) {
                    String text = renameObject(formula, object);
                    for (Map.Entry<String, String> r : renaming.entrySet()) {
                        text = text.replaceAll("(?<=[\\s(])" + Pattern.quote(r.getKey()) + "(?=[\\s)])",
                                Matcher.quoteReplacement(r.getValue()));
                    }
                    disjuncts.add(text);
                }
            }
            // balanced: the parser turns an n-ary (or ...) into a chain of depth n, which overflows the stack
            // when it is printed or translated to Z3 (objects with hundreds of summary paths)
            assertions.append(String.format("(assert %s)%n",
                    disjuncts.isEmpty() ? "false" : Opal.balancedOr(disjuncts)));
        }
        // object symbols are needed by the identity constraints, also if no summary declared them (yet)
        for (String object : objectIdentifiers) {
            for (String[] symbol : new String[][]{{object, "Int"}, {object + ".cls", "String"}}) {
                if (!declared.containsKey(symbol[0])) {
                    declared.put(symbol[0], symbol[1]);
                    declarations.append(String.format("(declare-fun %s () %s)%n", symbol[0], symbol[1]));
                }
            }
        }
        declarations.append("(declare-fun null () Int)\n");
        StringBuilder heapFunctions = new StringBuilder();
        for (String line : HEAP_FUNCTIONS.split("\n")) {
            Matcher f = FUNCTION_DECLARATION.matcher(line);
            if (f.find() && !declared.containsKey(f.group(1))) {
                declared.put(f.group(1), f.group(3));
                heapFunctions.append(line).append('\n');
            }
        }
        return heapFunctions.toString() + declarations + assertions + objectIdentityConstraints(objectIdentifiers);
    }

    /** the same sort can be written in different ways, e.g. {@code Float64} and {@code (_ FloatingPoint 11 53)} */
    static String normalizeSort(String sort) {
        String s = sort.trim().replaceAll("\\s+", " ");
        switch (s) {
            case "Float16": return "(_ FloatingPoint 5 11)";
            case "Float32": return "(_ FloatingPoint 8 24)";
            case "Float64": return "(_ FloatingPoint 11 53)";
            case "Float128": return "(_ FloatingPoint 15 113)";
            default: return s;
        }
    }

    /** SMT sorts of the variables of the path, by name. */
    private Map<String, String> pathSorts(List<Expression<Boolean>> path) {
        Map<String, String> sorts = new HashMap<>();
        for (Variable<?> v : extractFreeVariables(path)) {
            try {
                sorts.put(v.getName(), type(v));
            } catch (IllegalArgumentException e) {
                // unsupported type: no sort constraint from the path
            }
        }
        return sorts;
    }

    /**
     * Computes the summaries of the next {@code batchSize} needed, not yet summarized signatures of the query.
     *
     * @return the number of newly computed summaries; 0 means that all needed summaries are known, i.e., the
     *         solver result for the query is final.
     */
    public int summarizeNextBatch(Query query) {
        // complete the object with the fewest missing summaries first: once an object is complete, the
        // over-approximation (see isUnsatOverApproximated) can prove UNSAT without summarizing the other objects
        List<ConstructorSignature> missing = query.baseIdentifiers().stream()
                .map(object -> this.summaryStore.unsummarized(query.relevant().get(object)))
                .filter(list -> !list.isEmpty())
                .min(Comparator.comparingInt(List::size))
                .orElse(List.of());
        List<ConstructorSignature> batch = missing.subList(0, Math.min(this.batchSize, missing.size()));
        if (batch.isEmpty()) {
            return 0;
        }
        System.out.println("[ConstructorSummaryManager] summarizing " + batch.stream()
                .map(ConstructorSignature::signature).toList() + " (" + (missing.size() - batch.size())
                + " signatures of this object left, " + (this.summaryStore.summarizedCount() + batch.size())
                + " summarized in total)");
        return this.summaryStore.ensureSummarized(batch);
    }

    /** @return whether the list of constructor errors is static (incremental mode) and may be incomplete. */
    public boolean hasStaticErrorList() {
        return isIncremental();
    }

    public String generateFullConstructorSMTLIbCode(List<Expression<Boolean>> path) {
        List<Variable<?>> freeVariables = extractFreeVariables(path);
        List<String> objectIdentifiers = extractObjectIdentifiers(freeVariables);

//        Pattern baseClassPattern = Pattern.compile("__object_\\d+\\.cls|__object_\\d+\\.err");
        Pattern baseClassPattern = Pattern.compile("__object_\\d+$");
        List<String> baseIdentifiers = objectIdentifiers.stream()
                .filter(identifier -> baseClassPattern.matcher(identifier).find())
                .toList();

        System.out.println("baseIdentifiers: " + baseIdentifiers);
        System.out.println("objectIdentifiers: " + objectIdentifiers);

        if (this.eagerSummaries != null) {
            // all summaries for every object, with the same handling of conflicting field sorts as incremental mode
            Map<String, List<GeneratedSummary>> summariesPerObject = new LinkedHashMap<>();
            for (String object : baseIdentifiers) {
                summariesPerObject.put(object, this.eagerSummaries);
            }
            return renderObjectSummaries(summariesPerObject, pathSorts(path), objectIdentifiers);
        }

        // blueprint loaded from the cache: no per-signature summaries
        String declarations = buildAllObjectDeclarations(baseIdentifiers);
        String summaries = buildAllObjectSummaries(baseIdentifiers);
        String identityConstraints = objectIdentityConstraints(objectIdentifiers);


        return String.format("%s%n%s%n%s", declarations, summaries, identityConstraints);
    }

    private Collection<String> nestedObjects() {
        return isIncremental() ? this.summaryStore.objects() : this.objectsWithinAllTraces;
    }

    private List<Variable<?>> extractFreeVariables(List<Expression<Boolean>> path) {
        return path.stream()
                .map(ExpressionUtil::freeVariables)
                .flatMap(Collection::stream)
                .toList();
    }


    private void extractErrorWithinConstructors() {
        Pattern pattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = pattern.matcher(this.bluePrintConstructorSummaries);

        Set<String> errors = new HashSet<>();
        while (matcher.find()) {
            errors.add(matcher.group(1));
        }
        this.possibleErrorsWithInConstructors.addAll(errors);
    }

    private boolean cacheExists() {
        Path base = Paths.get(classPath);
        return Files.exists(base.resolve("ConstructurBluePrint.txt"))
                && Files.exists(base.resolve("ConstructurDeclarations.txt"))
                && Files.exists(base.resolve("ConstructurObjects.txt"));
    }

    private String loadBluePrintFromCache() {
        Path base = Paths.get(classPath);
        try {
            String blueprint = Files.readString(base.resolve("ConstructurBluePrint.txt"));
            List<String> declarations = Files.readAllLines(base.resolve("ConstructurDeclarations.txt"));
            List<String> objects = Files.readAllLines(base.resolve("ConstructurObjects.txt"));
            this.declarationsOfBluePrint.addAll(declarations);
            this.objectsWithinAllTraces.addAll(objects);
            return blueprint;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ConstructorBluePrint cache from " + base, e);
        }
    }

    private void writeBluePrintToCache(String blueprint) {
        Path base = Paths.get(classPath);
        try {
            Files.writeString(base.resolve("ConstructurBluePrint.txt"), blueprint);
            Files.write(base.resolve("ConstructurDeclarations.txt"),
                    new ArrayList<>(this.declarationsOfBluePrint));
            Files.write(base.resolve("ConstructurObjects.txt"),
                    this.objectsWithinAllTraces);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ConstructorBluePrint cache to " + base, e);
        }
    }

    private List<String> extractObjectIdentifiers(List<Variable<?>> variables) {
        return variables.stream()
                .map(Variable::getName)
                .filter(name -> name.endsWith(".cls") || name.endsWith(".err"))
                .map(name -> name.substring(0, name.length()-4))
                .distinct()
                .filter(name -> !nestedObjects().contains(name))
                .toList();
    }


    /**
     * Builds SMT-LIB declarations for a single object.
     */
    private String buildDeclarationsForObject(String objectIdentifier) {
        StringBuilder sb = new StringBuilder();

        // declarations of functions are added once, see buildAllObjectDeclarations
        String declarationsTemplate = this.declarationsOfBluePrint.stream()
                .filter(d -> !FUNCTION_DECLARATION.matcher(d).find())
                .collect(Collectors.joining("\n"));

        // Declare init function for object
        sb.append(String.format("\n(declare-fun %s.init () String)", objectIdentifier));

        // Replace placeholder with actual object identifier
        sb.append(renameObject(declarationsTemplate, objectIdentifier));

        return sb.toString();
    }

    /**
     * Builds declarations for all objects.
     */
    private String buildAllObjectDeclarations(List<String> objectIdentifiers) {
        StringBuilder sb = new StringBuilder();

        for (String objectIdentifier : objectIdentifiers) {
            sb.append(buildDeclarationsForObject(objectIdentifier));
        }

        // Add null declaration once
        sb.append("\n(declare-fun null () Int)\n");
        Map<String, String> functions = new LinkedHashMap<>();
        for (String d : (HEAP_FUNCTIONS + String.join("\n", this.declarationsOfBluePrint)).split("\n")) {
            Matcher f = FUNCTION_DECLARATION.matcher(d);
            while (f.find()) {
                functions.putIfAbsent(f.group(1), f.group(0));
            }
        }
        functions.values().forEach(d -> sb.append(d).append('\n'));

        return sb.toString();
    }

    /**
     * Builds summaries for all objects.
     */
    private String buildAllObjectSummaries(List<String> objectIdentifiers) {
        StringBuilder sb = new StringBuilder();

        for (String objectIdentifier : objectIdentifiers) {
            sb.append(buildSummariesForObject(objectIdentifier));
        }

        return sb.toString();
    }

    /**
     * Builds constructor summaries for a single object.
     */
    private String buildSummariesForObject(String objectIdentifier) {
        return String.format("(assert (or %s))",
                renameObject(this.bluePrintConstructorSummaries, objectIdentifier) + "\n");
    }

    private String type(Variable v) {
        // TODO: add missing data types
        if (BuiltinTypes.BOOL.equals(v.getType())) {
            return "Bool";
        } else if (BuiltinTypes.SINT32.equals(v.getType())) {
            return "(_ BitVec 32)";
        } else if (BuiltinTypes.SINT64.equals(v.getType())) {
            return "(_ BitVec 64)";
        } else if (BuiltinTypes.UINT16.equals(v.getType()) || BuiltinTypes.SINT16.equals(v.getType())) {
            return "(_ BitVec 16)";
        } else if (BuiltinTypes.SINT8.equals(v.getType())) {
            return "(_ BitVec 8)";
        } else if (BuiltinTypes.STRING.equals(v.getType())) {
            return "String";
        } else if (BuiltinTypes.INTEGER.equals(v.getType())) {
            return "Int";
        } else if (v.getType() instanceof BitLimitedBVIntegerType) {
            return "(_ BitVec " + ((BitLimitedBVIntegerType) v.getType()).getNumBits() + ")";
        } else if (BuiltinTypes.DOUBLE.equals(v.getType())) {
            return "Float64";
        } else if (BuiltinTypes.FLOAT.equals(v.getType())) {
            return "Float32";
        }
        throw new IllegalArgumentException("Unsupported type: " + v.getType());
    }

    private String generateSmtCodeBluePrintForConstructorSelection() {
        List<String> signaturesConstructorCalls = this.opal.generateSignaturesOfPossibleConstructorCallsFromNondetObject(this.depth);
        System.out.println("signaturesConstructorCalls: ");
        System.out.println("count: "+signaturesConstructorCalls.size());
        System.out.println(signaturesConstructorCalls);
        List<String> filteredSignaturesConstructorCalls = signaturesConstructorCalls.stream().distinct().toList();
        System.out.println("[ConstructorSummaryManager] constructor signatures to summarize: "
                + filteredSignaturesConstructorCalls);

        List<String> formulas = new ArrayList<>();
        this.eagerSummaries = new ArrayList<>();
        for (String signature : filteredSignaturesConstructorCalls) {
            GeneratedSummary summary = this.summarizeSignature(signature);
            this.objectsWithinAllTraces.addAll(summary.objects());
            this.declarationsOfBluePrint.addAll(summary.declarations());
            formulas.addAll(summary.formulas());
            this.eagerSummaries.add(summary);
        }

        return String.join("\n", formulas);
    }

    /**
     * Computes the summary of a single constructor signature by running DSE on it. Used by the eager blueprint
     * generation above and as the {@link SummaryStore.Generator} for incremental summaries.
     *
     * @param signature constructor signature without error marker, e.g. {@code LB;|(I)V|{}}.
     * @return summary templated on {@code __object_0}.
     */
    public GeneratedSummary summarizeSignature(String signature) {
        List<Trace> traces = new ArrayList<>(this.performDseOnConstructor("<>" + signature));

        traces.removeIf(t -> t == null
                || t.getTraceState() instanceof PathResult.AbortResult
                || (t.getSummaries().isEmpty() && t.getDecisions().isEmpty()));

        //Collect objects
        List<String> objects = traces.stream()
                .map(Trace::getObjectIdentifiers)
                .flatMap(Collection::stream)
                .toList();

        //Collect declarations
        String regex = "__(bool|byte|char|short|int|long|float|double|string)_\\d";
        Set<String> declarations = traces.stream()
                .map(Trace::getDeclarations)
                .flatMap(List::stream)
                .map(s -> s.replaceAll(regex, "__object_0" + "$0"))
                .collect(Collectors.toSet());

        List<String> formulas = traces.stream()
                .map(this::generateSmtCodeFromSingleConstructorSummaryTrace)
                .filter(formula -> isParsable(formula, declarations, signature))
                .toList();

        formulas.forEach(this::registerClasses);
        return new GeneratedSummary(signature, formulas, declarations, objects);
    }

    /** class constants of summaries ({@code (= o.f.cls "Ljava/util/HashSet;")}), e.g. JDK classes in fields */
    private static final Pattern CLASS_CONSTANT = Pattern.compile("(?:\\.cls|\\(obj\\.cls [^()\\s]+\\)) \"(L[^\"]*;)\"");
    private final Set<String> summaryClasses = new LinkedHashSet<>();
    private java.util.function.Consumer<String> classListener;

    private void registerClasses(String formula) {
        Matcher m = CLASS_CONSTANT.matcher(formula);
        while (m.find()) {
            if (this.summaryClasses.add(m.group(1)) && this.classListener != null) {
                this.classListener.accept(m.group(1));
            }
        }
    }

    /**
     * Notified of every class that occurs in a summary (also of those in the summaries computed so far), so that
     * the class hierarchy used by the solver can be extended by classes that only occur in summaries.
     */
    public void setClassListener(java.util.function.Consumer<String> listener) {
        this.classListener = listener;
        new ArrayList<>(this.summaryClasses).forEach(listener);
    }

    /**
     * Summary paths whose formula cannot be parsed are dropped (with a warning): one broken formula would make every
     * query that includes the summary unparsable. Dropping a path is sound (every model still uses a valid summary)
     * but incomplete. Known cause: SPouT writes string values of fields without escaping, so a value with a line
     * break (e.g. {@code ToStringStyle.contentStart} of commons-lang3) truncates the summary line of the trace.
     */
    static boolean isParsable(String formula, Set<String> declarations, String signature) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (c == '"') {
                if (inString && i + 1 < formula.length() && formula.charAt(i + 1) == '"') {
                    i++; // escaped quote
                } else {
                    inString = !inString;
                }
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')' && --depth < 0) {
                break;
            }
        }
        if (depth == 0 && !inString) {
            return true;
        }
        System.out.println("WARNING: dropping truncated summary path of " + signature);
        return false;
    }

    private List<Trace> performDseOnConstructor(String constructorSignature) {
        String jarWildCard = this.classPath+"*";
        String libJarWildCard = this.classPath+"libs/*";
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.constructor.summary", "true");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args",
                String.format("-cp %s/:%s:%s:src/main/resources/constructor:../verifier-stub/target/verifier-stub-1.0.jar " +
                                "-Dconcolic.execution=true " +
                                "-Dconcolic.constructor.summary=true " +
//                "-Dconcolic.constructors=%s " +
                                "%s",
                        this.classPath, jarWildCard, libJarWildCard, "SummaryMain"));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("concolic.max.object.annotation.depth", "3");
        // bounded: constructors with loops over symbolic values (e.g. capacity computations) have unboundedly many
        // paths. A summary cut off by the timeout misses paths of the constructor (incomplete, like any timeout).
        long timeout = summaryTimeoutSeconds;
        if (this.deadline != Long.MAX_VALUE) {
            timeout = Math.min(timeout, Math.max(1, (this.deadline - System.currentTimeMillis()) / 1000));
        }
        props.setProperty("dse.timeout", String.valueOf(timeout));

        Config config = Config.fromProperties(props);
        DSE dse = new DSE(config);
        List<Trace> traces = dse.executeConstructor(constructorSignature);


        return traces;
    }


    private String generateSmtCodeFromSingleConstructorSummaryTrace(Trace summaryTrace) {
        List<Expression<Boolean>> decisions = summaryTrace.getDecisions().stream()
                .map(Decision::getCondition)
                .toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        SMTLibExportGenContext genCtx = new SMTLibExportGenContext(ps);
        SMTLibExportVisitor visitor = new SMTLibExportVisitor(genCtx);

        List<String> parsedExpressions = new ArrayList<>();
        for (Expression<Boolean> decision : decisions) {
            // reset: otherwise every decision repeats all earlier ones (quadratically many conjuncts)
            baos.reset();
            decision.accept(visitor, null);
            genCtx.flush();
            parsedExpressions.add(baos.toString().trim());
        }

        List<String> decisionStrings = parsedExpressions.stream()
                .map(s -> s.split("\n"))
                .flatMap(Arrays::stream)
                .filter(line -> !line.startsWith("(declare-const"))
                .toList();

        // without duplicates, as a balanced tree: the parser turns an n-ary (and ...) into a chain of depth n,
        // which overflows the stack of recursive visitors (printing, grounding, translation to Z3)
        List<String> conjuncts = new ArrayList<>(new LinkedHashSet<>(Stream.concat(
                        summaryTrace.getSummaries().stream(),
                        decisionStrings.stream()
                ).filter(s -> s != null && !s.isEmpty()).toList()));
        String erg = conjuncts.isEmpty() ? "true" : balancedAnd(conjuncts);

        erg = addObjectPrefixToPrimitives(erg, "__object_0");

        PathResult traceState = summaryTrace.getTraceState();

        if (traceState instanceof PathResult.ErrorResult) {
            String exceptionClass = ((PathResult.ErrorResult) traceState).getExceptionClass();
            erg = erg.replace("<>", String.format("<%s>", exceptionClass));
            erg = erg.replace("(= __object_0.err \"\")", String.format("(= __object_0.err \"%s\")", exceptionClass));
        }

        return erg;

    }

    static String balancedAnd(List<String> terms) {
        if (terms.size() == 1) {
            return terms.get(0);
        }
        int mid = terms.size() / 2;
        return "(and " + balancedAnd(terms.subList(0, mid)) + " " + balancedAnd(terms.subList(mid, terms.size())) + ")";
    }

    public String getBluePrintConstructorSummaries() {
        return bluePrintConstructorSummaries;
    }

    /**
     * Identities of the nondet objects {@code __object_k}: each is {@code null} or a new object, owned by itself
     * ({@code obj.owner = k}, {@code obj.idx = 0}). Objects of different owners are distinct (as are the objects of
     * one owner, see the summaries), so this replaces pairwise distinctness. The class of {@code __object_k} is
     * {@code __object_k.cls} (also as value of {@code obj.cls}, which is used for other objects).
     */
    public String objectIdentityConstraints(List<String> objectIdentifiers) {
        if (objectIdentifiers == null) {
            throw new IllegalArgumentException("objectIdentifiers must not be null");
        }
        StringBuilder erg = new StringBuilder();
        for (String object : objectIdentifiers) {
            Matcher m = Pattern.compile("__object_(\\d+)").matcher(object);
            if (!m.matches()) {
                continue;
            }
            int k = Integer.parseInt(m.group(1));
            erg.append(String.format("(assert (= (obj.cls %1$s) %1$s.cls))%n", object));
            erg.append(String.format(
                    "(assert (or (= %1$s null) (and (= (obj.owner %1$s) #x%2$08x) (= (obj.idx %1$s) #x00000000))))%n",
                    object, k));
        }
        return erg.toString();
    }

    /**
     * In the given String all occurrences of primitive types (__int_{id}, __byte_{id}, ...)
     * are expanded with the given prefix.
     *
     * @param input  String to be modified
     * @param prefix prefix to be added
     * @return modified String
     */
    public static String addObjectPrefixToPrimitives(String input, String prefix) {
        if (input == null || prefix == null) {
            return input;
        }
        String regex = "__(bool|byte|char|short|int|long|float|double|string)_\\d+";

        // $0 is a back-reference to the whole matched substring
        return input.replaceAll(regex, prefix + "$0");
    }

    public List<String> getPossibleErrorsWithInConstructors() {
        return possibleErrorsWithInConstructors;
    }

    public Set<String> getDeclarationsOfBluePrint() {
        return isIncremental() ? this.summaryStore.declarations() : declarationsOfBluePrint;
    }
}
