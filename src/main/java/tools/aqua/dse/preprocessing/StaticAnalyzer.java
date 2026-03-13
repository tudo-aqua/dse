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
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;
import tools.aqua.dse.trace.Decision;
import tools.aqua.dse.trace.Trace;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StaticAnalyzer {





//    private String classPath;
//    private Project project;
//    private final List<KlassIdentifier> klassIdentifiers;
//    private final String staticSMTLibCode;
//    private final Set<String> declarations;
//
//    public StaticAnalyzer(String classPath) {
//        this.classPath = classPath;
//        this.project = Project.apply(new File(classPath));
//        this.klassIdentifiers = this.extractKlassesFromClassPath();
//        this.staticSMTLibCode = generateStaticSmtLibCode();
//        this.declarations = new HashSet<>();
//    }
//
//
//    public String generateSMTProblem(Trace trace) {
//        //dynamic part
////        sb.append(generateTraceDeclarations(trace)).append("\n");
////        sb.append(generateConstructorConstraint(this.klassIdentifiers)).append("\n");
////        sb.append(generateConstraintsOfTrace(trace)).append("\n");
//
////        try {
////            SMTProblem smtProblem = SMTLIBParser.parseSMTProgram(this.staticSMTLibCode);
////
////
////
////
////
////        } catch (IOException e) {
////            throw new RuntimeException(e);
////        } catch (SMTLIBParserException e) {
////            throw new RuntimeException(e);
////        }
//
//        return staticSMTLibCode.toString();
//    }
//
//    private String generateStaticSmtLibCode() {
//        StringBuilder sb = new StringBuilder();
//        sb.append(generateObjectSort()).append("\n");
//        sb.append(generateNullConstant()).append("\n");
//        sb.append(generateExtendsSummary()).append("\n");
//        sb.append(generatePolymorphismSummary(this.klassIdentifiers)).append("\n");
//        return sb.toString();
//    }
//
//
//    public String generateObjectSort() {
//        return "(declare-sort Object 0)";
//    }
//
//    public String generateNullConstant() {
//        return "(declare-fun null () Object)";
//    }
//
//    public String generateTraceDeclarations(Trace trace) {
//        return String.join("\n", trace.getDeclarations());
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//    public String generateDeclarationsOfVariblesFromTraces(Trace trace) {
//        return "";
//    }
//
//
//
//
////    public String generateConstraintsOfTrace(Trace trace) {
//////        return trace.getDecisions().stream().collect(Collectors.joining("\n"));
////    }
//
//
////    private void extractObjectsFromTrace(Trace trace) {
////        trace.stream()
////                .map(ExpressionUtil::freeVariables)
////                .flatMap(Collection::stream)
////    }
//
//
////    public void parseSMTProgram() throws IOException, SMTLIBParserException {
//////        SolverContext context = new SolverContext();
//////         String smtLibCode = "";
//////        SMTProblem smtProblem = SMTLIBParser.parseSMTProgram(smtLibCode);
//////        SolverContext solverContext = smtProblem.addProblemToContext(context);
////    }
}
