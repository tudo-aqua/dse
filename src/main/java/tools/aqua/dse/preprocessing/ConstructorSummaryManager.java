package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
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
import java.io.PrintStream;
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
    private final String bluePrintConstructorSummaries;
    private final List<String> possibleErrorsWithInConstructors = new ArrayList<>();
    private final List<String> objectsWithinAllTraces = new ArrayList<>();

    public ConstructorSummaryManager(String classPath, int depth) {
        this.classPath = classPath;
        this.opal = new Opal(classPath);
        this.depth = depth;
        System.out.println("generateSmtCodeBluePrintForConstructorSelection-Call");
        this.bluePrintConstructorSummaries = this.generateSmtCodeBluePrintForConstructorSelection();
        extractErrorWithinConstructors();
        System.out.println("generateSmtCodeBluePrintForConstructorSelection-Done");
    }


    public String generateFullConstructorSMTLIbCode(List<Expression<Boolean>> path) {
        List<Variable<?>> freeVariables = extractFreeVariables(path);
        List<String> objectIdentifiers = extractObjectIdentifiers(freeVariables);

        String declarations = buildAllObjectDeclarations(objectIdentifiers);
        String summaries = buildAllObjectSummaries(objectIdentifiers);
        String objectNotIdentityConstraints = objectNotIdentityConstraints(objectIdentifiers);


        return String.format("%s%n%s%n%s", declarations, summaries, objectNotIdentityConstraints);
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

    private List<String> extractObjectIdentifiers(List<Variable<?>> variables) {
//        return variables.stream()
//                .map(Variable::getName)
//                .filter(this.objectsWithinAllTraces::contains)
//                .distinct() // Ensure uniqueness (replacement for Set behavior)
//                .toList();

        Pattern pattern = Pattern.compile("^__object_\\d+.*");

        return variables.stream()
                .map(Variable::getName)
                .filter(name -> name.endsWith(".cls") || name.endsWith(".err"))
                .map(name -> name.substring(0, name.length()-4))
                .distinct()
                .filter(name -> !this.objectsWithinAllTraces.contains(name))
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
        List<String> filteredSignaturesConstructorCalls = signaturesConstructorCalls.stream().distinct().toList();
//        signaturesConstructorCalls.remove("LC;|(LA;)V|{LC;|(I)V|{}}");  //todo: After remove smt-solver problem
//        signaturesConstructorCalls.remove("LC;|(I)V|{}");               //todo: After remove smt-solver problem


        List<Trace> traces = new ArrayList<>();
        for (String signature : filteredSignaturesConstructorCalls) {
            traces.addAll(this.performDseOnConstructor("<>"+signature));
        }

        //Collect objects
        List<String> objectsInAllTraces = traces.stream()
                .map(Trace::getObjectIdentifiers)
                .flatMap(Collection::stream)
                .toList();
        this.objectsWithinAllTraces.addAll(objectsInAllTraces);


        //Collect declarations
        String regex = "__(byte|char|short|int|long|float|double|string)_\\d";
        this.declarationsOfBluePrint.addAll(traces.stream()
                .map(Trace::getDeclarations)
                .flatMap(List::stream)
                .map(s -> s.replaceAll(regex, "__object_0" + "$0"))
                .collect(Collectors.toSet()));

        return this.generateSmtCodeFromConstructorSummaryTraces(traces);
    }

    private List<Trace> performDseOnConstructor(String constructorSignature) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.constructor.summary", "true");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args",
                String.format("-cp %s/:src/main/resources/constructor:../verifier-stub/target/verifier-stub-1.0.jar " +
                                "-Dconcolic.execution=true " +
                                "-Dconcolic.constructor.summary=true " +
//                "-Dconcolic.constructors=%s " +
                                "%s",
                        this.classPath, "SummaryMain"));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");

        Config config = Config.fromProperties(props);
        DSE dse = new DSE(config);
        List<Trace> traces = dse.executeConstructor(constructorSignature);


        return traces;
    }


    private String generateSmtCodeFromConstructorSummaryTraces(List<Trace> summaryTraces) {
            return summaryTraces.stream()
                    .map(this::generateSmtCodeFromSingleConstructorSummaryTrace)
                    .collect(Collectors.joining("\n"));
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
                ).collect(Collectors.joining(" "))
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

        return String.format("(assert (not (= %s)))", String.join(" ", objectIdentifiers));
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
}
