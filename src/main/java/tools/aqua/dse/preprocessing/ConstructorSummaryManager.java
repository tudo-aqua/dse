package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.types.BitLimitedBVIntegerType;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstructorSummaryManager {
    private final String classPath;
    private final int depth;
    private final Opal opal;
    private Set<String> declarationsOfBluePrint = new HashSet<>();
    private final String bluePrintConstructorSummaries;

    public ConstructorSummaryManager(String classPath, int depth) {
        this.classPath = classPath;
        this.opal = new Opal(classPath);
        this.depth = depth;
        System.out.println("generateSmtCodeBluePrintForConstructorSelection-Call");
        this.bluePrintConstructorSummaries = this.generateSmtCodeBluePrintForConstructorSelection();
        System.out.println("generateSmtCodeBluePrintForConstructorSelection-Done");
    }


    public String generateSMTLibCode(List<Expression<Boolean>> path) {
        StringBuilder declarations = new StringBuilder();

        //declarations of the variables within the constructor summariesTheory
        List<Variable<?>> freeVariables = path.stream()
                .map(ExpressionUtil::freeVariables)
                .flatMap(Collection::stream)
                .toList();


        for (Variable<?> freeVariable : freeVariables) {
            declarations.append(String.format("(declare-fun %s () %s)\n",
                    freeVariable.getName(),
                    type(freeVariable)));
        }

        //constructor summaries
        Pattern pattern = Pattern.compile("^__object_\\d+.*");

        Set<String> objectIdentifiers = freeVariables.stream()
                .map(Variable::getName)
                .filter(name -> pattern.matcher(name).matches())
                .map(name -> name.split("\\.")[0])
                .collect(Collectors.toSet());


        // adjust delcare of constructor parameter per object
        for (String declaration : this.declarationsOfBluePrint) {
            String regex = "__(byte|char|short|int|long|float|double|string)_\\d+";
            if (declaration.matches(regex)) {
                this.declarationsOfBluePrint.remove(declaration);
                for (String objectIdentifier : objectIdentifiers) {
                    this.declarationsOfBluePrint.add(addPrefixToTypes(declaration, objectIdentifier));
                }
            }
        }

        StringBuilder declarationsString = new StringBuilder(String.join("\n", this.declarationsOfBluePrint));
        StringBuilder summaries = new StringBuilder();
        for (String objectIdentifier : objectIdentifiers) {
            summaries.append(this.bluePrintConstructorSummaries.replace("__object_0", objectIdentifier)).append("\n");
            declarationsString.append(String.format("\n (declare-fun %s.init () String)", objectIdentifier));
            declarationsString.append("\n(declare-fun null () Int) \n");

        }
        String objectedNotIdentityConstraints = objectNotIdentityConstraints(objectIdentifiers.stream().toList());
        String declaresAndSummaries = String.format("%n%s (assert (or %s)) %n %s", addPrefixToTypes(declarationsString.toString(), "__object_0"), summaries, objectedNotIdentityConstraints);

        return objectedNotIdentityConstraints.isBlank() ? declaresAndSummaries : declaresAndSummaries +"\n"+objectedNotIdentityConstraints;

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
        signaturesConstructorCalls.remove("LC;|(LA;)V|{LC;|(I)V|{}}");  //todo: After remove smt-solver problem
        signaturesConstructorCalls.remove("LC;|(I)V|{}");               //todo: After remove smt-solver problem


        List<Trace> traces = new ArrayList<>();
        for (String signature : signaturesConstructorCalls) {
            traces.addAll(this.performDseOnConstructor("<>"+signature));
        }

        this.declarationsOfBluePrint.addAll(traces.stream()
                .map(Trace::getDeclarations)
                .flatMap(List::stream)
                .collect(Collectors.toSet()));

        return addPrefixToTypes(this.generateSmtCodeFromConstructorSummaryTraces(traces), "__object_0");
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
        String erg =  String.format(
                "(and %s)",
                Stream.concat(
                        summaryTrace.getSummaries().stream(),
                        summaryTrace.getDecisions().stream().map(Decision::toString)
                ).collect(Collectors.joining(" "))
        );

        PathResult traceState = summaryTrace.getTraceState();
        return  traceState instanceof PathResult.ErrorResult ?
                erg.replace("<>", String.format("<%s>", ((PathResult.ErrorResult) traceState).getExceptionClass())) :
                erg;
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
    public static String addPrefixToTypes(String input, String prefix) {
        if (input == null || prefix == null) {
            return input;
        }
        String regex = "__(byte|char|short|int|long|float|double|string)_\\d+";

        // $0 is a back-reference to the whole matched substring
        return input.replaceAll(regex, prefix + "$0");
    }



}
