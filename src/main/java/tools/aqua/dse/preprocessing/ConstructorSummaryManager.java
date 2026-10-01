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
        while (true) {
            round++;
            String dynamicSmtLibCode = generateIncrementalSMTLibCode(query);
            System.out.println("SMT-Problem-Start");
            System.out.printf("\u001B[38;5;208m %s \u001B[0m%n", dynamicSmtLibCode);
            System.out.println("SMT-Problem-End");

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
            if (round == 1 && isUnsatWithoutSummaries(solverCtx, path, query)) {
                System.out.println("[ConstructorSummaryManager] UNSAT independently of constructor summaries");
                return res;
            }
            if (summarizeNextBatch(query) == 0) {
                return res;
            }
        }
    }

    /**
     * Summaries only add constraints on objects. If the path is unsatisfiable with unconstrained objects (e.g.,
     * {@code o != o}), it is unsatisfiable with any set of summaries, and no summary needs to be computed. The
     * distinctness of objects does not depend on summaries and is included.
     */
    private boolean isUnsatWithoutSummaries(SolverContext solverCtx, List<Expression<Boolean>> path, Query query) {
        solverCtx.push();
        try {
            String distinct = objectNotIdentityConstraints(query.objectIdentifiers());
            if (!distinct.isEmpty()) {
                StringBuilder sb = new StringBuilder("(declare-fun null () Int)\n");
                for (String object : query.objectIdentifiers()) {
                    sb.append(String.format("(declare-fun %s () Int)%n", object));
                }
                SmtProblemManager.addSmtProblemAsString(sb + distinct, solverCtx);
            }
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
     */
    public record Query(List<String> objectIdentifiers,
                        List<String> baseIdentifiers,
                        Map<String, List<ConstructorSignature>> relevant,
                        List<ConstructorSignature> needed) {}

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
        return new Query(objectIdentifiers, baseIdentifiers, relevant, needed);
    }

    /**
     * SMT-LIB code for the query: declarations, and per base object the disjunction of the summaries computed so
     * far among its relevant signatures ({@code (or false)} if there is none yet), and distinctness of objects.
     */
    public String generateIncrementalSMTLibCode(Query query) {
        StringBuilder sb = new StringBuilder();
        String declarationsTemplate = String.join("\n", this.summaryStore.declarations());
        for (String object : query.baseIdentifiers()) {
            sb.append(String.format("\n(declare-fun %s.init () String)", object));
            sb.append(declarationsTemplate.replace("__object_0", object));
        }
        sb.append("\n(declare-fun null () Int)\n");
        for (String object : query.baseIdentifiers()) {
            String blueprint = this.summaryStore.blueprint(query.relevant().get(object));
            sb.append(String.format("(assert (or %s))", blueprint.replace("__object_0", object) + "\n"));
        }
        sb.append(objectNotIdentityConstraints(query.objectIdentifiers()));
        return sb.toString();
    }

    /**
     * Computes the summaries of the next {@code batchSize} needed, not yet summarized signatures of the query.
     *
     * @return the number of newly computed summaries; 0 means that all needed summaries are known, i.e., the
     *         solver result for the query is final.
     */
    public int summarizeNextBatch(Query query) {
        List<ConstructorSignature> missing = this.summaryStore.unsummarized(query.needed());
        List<ConstructorSignature> batch = missing.subList(0, Math.min(this.batchSize, missing.size()));
        if (batch.isEmpty()) {
            return 0;
        }
        System.out.println("[ConstructorSummaryManager] summarizing " + batch.stream()
                .map(ConstructorSignature::signature).toList() + " (" + (missing.size() - batch.size())
                + " needed signatures left, " + (this.summaryStore.summarizedCount() + batch.size())
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

        String declarations = buildAllObjectDeclarations(baseIdentifiers);
        String summaries = buildAllObjectSummaries(baseIdentifiers);
        String objectNotIdentityConstraints = objectNotIdentityConstraints(objectIdentifiers);


        return String.format("%s%n%s%n%s", declarations, summaries, objectNotIdentityConstraints);
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

        String declarationsTemplate = String.join("\n", this.declarationsOfBluePrint);

        // Declare init function for object
        sb.append(String.format("\n(declare-fun %s.init () String)", objectIdentifier));

        // Replace placeholder with actual object identifier
        sb.append(declarationsTemplate.replace("__object_0", objectIdentifier));

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
                this.bluePrintConstructorSummaries.replace("__object_0", objectIdentifier) + "\n");
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
        for (String signature : filteredSignaturesConstructorCalls) {
            GeneratedSummary summary = this.summarizeSignature(signature);
            this.objectsWithinAllTraces.addAll(summary.objects());
            this.declarationsOfBluePrint.addAll(summary.declarations());
            formulas.addAll(summary.formulas());
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
        String regex = "__(byte|char|short|int|long|float|double|string)_\\d";
        Set<String> declarations = traces.stream()
                .map(Trace::getDeclarations)
                .flatMap(List::stream)
                .map(s -> s.replaceAll(regex, "__object_0" + "$0"))
                .collect(Collectors.toSet());

        List<String> formulas = traces.stream()
                .map(this::generateSmtCodeFromSingleConstructorSummaryTrace)
                .toList();

        return new GeneratedSummary(signature, formulas, declarations, objects);
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
            decision.accept(visitor, null);
            genCtx.flush();
            parsedExpressions.add(baos.toString().trim());
        }

        List<String> decisionStrings = parsedExpressions.stream()
                .map(s -> s.split("\n"))
                .flatMap(Arrays::stream)
                .filter(line -> !line.startsWith("(declare-const"))
                .toList();

        String erg =  String.format(
                "(and %s)",
                Stream.concat(
                        summaryTrace.getSummaries().stream(),
                        decisionStrings.stream()
                ).filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(" "))
        );

        erg = addObjectPrefixToPrimitives(erg, "__object_0");

        PathResult traceState = summaryTrace.getTraceState();

        if (traceState instanceof PathResult.ErrorResult) {
            String exceptionClass = ((PathResult.ErrorResult) traceState).getExceptionClass();
            erg = erg.replace("<>", String.format("<%s>", exceptionClass));
            erg = erg.replace("(= __object_0.err \"\")", String.format("(= __object_0.err \"%s\")", exceptionClass));
        }

        return erg;

    }

    public String getBluePrintConstructorSummaries() {
        return bluePrintConstructorSummaries;
    }

    public String objectNotIdentityConstraints(List<String> objectIdentifiers) {
        if (objectIdentifiers == null) {
            throw new IllegalArgumentException("objectIdentifiers must not be null");
        }
        if (objectIdentifiers.size() <= 1) {
            return "";
        }

        StringBuilder erg = new StringBuilder();
        for (String identifier1 : objectIdentifiers) {
            for (String identifier2 : objectIdentifiers) {
                if (identifier1.equals(identifier2)) {
                    continue;
                }
                erg.append(String.format("(or ( and (not (= %s %s))) (and (= %s null)(= %s null)))",
                        identifier1, identifier2, identifier1, identifier2));
            }
        }
        String temp = String.format("(assert (and %s))", erg);
        System.out.println(temp);
        return temp;
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
        String regex = "__(byte|char|short|int|long|float|double|string)_\\d+";

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
