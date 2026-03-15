package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstructorSummaryManager {
    private final String classPath;
    private final int depth;
    private final Opal opal;
    private final List<String> declarationsOfBluePrint = new ArrayList<>();
    private final String bluePrintConstructorSummaries;

    public ConstructorSummaryManager(String classPath, int depth) {
        this.classPath = classPath;
        this.opal = new Opal(classPath);
        this.depth = depth;
        System.out.println("generateSmtCodeBluePrintForConstructorSelection-Call");
        this.bluePrintConstructorSummaries = this.generateSmtCodeBluePrintForConstructorSelection();
    }


//    public String generateSmtLibCodeForTrace(Trace trace) {
//        StringBuilder smtCodeForTraces = new StringBuilder();
//        for (String objectIdentifier : trace.getObjectIdentifiers()) {
//            smtCodeForTraces.append(this.generateSmtCodeBluePrintForConstructorSelection().replace("__object_0", objectIdentifier));
//        }
//
//        return String.format("(assert (or %s))", smtCodeForTraces);
//    }
//
//    public String generateConstructorDeclarations(Trace trace) {
//        StringBuilder sb = new StringBuilder();
//
//        for (String objectIdentifier : trace.getObjectIdentifiers()) {
//            for (String declaration : this.declarationsOfBluePrint) {
//                sb.append(declaration.replace("__object_0", objectIdentifier)).append("\n");
//            }
//        }
//
//        return sb.toString();
//    }




    public String generateSMTLibCode(List<Expression<Boolean>> path) {
        StringBuilder erg = new StringBuilder();

        //declarations of the variables within the constructor summaries
        List<Variable<?>> freeVariables = path.stream()
                .map(ExpressionUtil::freeVariables)
                .flatMap(Collection::stream)
                .toList();

        for (Variable<?> freeVariable : freeVariables) {
            erg.append(String.format("(declare-fun %s () %s)\n",
                    freeVariable.getName(),
                    freeVariable.getType()));
        }

        //constructor summaries
        ArrayList<String> objectIdentifiers = freeVariables.stream()
                .map(Variable::getName)
                .filter(name -> name.startsWith("__object_"))
                .collect(Collectors.toCollection(ArrayList::new));

        for (String objectIdentifier : objectIdentifiers) {
            erg.append(this.bluePrintConstructorSummaries.replace("__object_0", objectIdentifier)).append("\n");
        }

        return erg.toString();
    }

//    public String generateSMTLibCode(List<String> objectIdentifiers) {
//
//
//        String constructorSummaries = objectIdentifiers.stream()
//                .map(objectIdentifier -> this.bluePrintConstructorSummaries.replace("__object_0", objectIdentifier))
//                .collect(java.util.stream.Collectors.joining(" "));
//
//        return "(assert (or " + constructorSummaries + "))";
//    }

//    public String generateConstructorDeclarations(List<String> objectIdentifiers) {
//
//    }


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
                .toList());

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
        return String.format(
                "(and %s)",
                Stream.concat(
                        summaryTrace.getSummaries().stream(),
                        summaryTrace.getDecisions().stream().map(Decision::toString)
                ).collect(Collectors.joining(" "))
        );
    }

    public String getBluePrintConstructorSummaries() {
        return bluePrintConstructorSummaries;
    }
}
