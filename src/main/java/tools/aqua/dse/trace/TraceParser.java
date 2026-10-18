/*
Copyright [yyyy] [name of copyright owner]

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
*/

package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.Negation;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.smtlibUtility.SMTProblem;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;
import tools.aqua.dse.objects.Clazz;
import tools.aqua.dse.objects.ClazzModel;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.preprocessing.ConstructorSummaryManager;
import tools.aqua.dse.preprocessing.Opal;
import tools.aqua.dse.preprocessing.StaticManager;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TraceParser {

    public static Trace parseTrace(List<String> lines,
                                   Valuation vals,
                                   Config config) throws IOException, SMTLIBParserException {
        List<Decision> decisions = new LinkedList<>();
        List<String> declarations = new ArrayList<>();
        List<WitnessAssumption> witness = new LinkedList<>();
        List<String> taintViolations = new LinkedList<>();
        List<String> flows = new LinkedList<>();
        List<String> summeries = new ArrayList<>();
        PathResult result = PathResult.ok(vals);
//        String decl = "";
        // a StringBuilder: traces can have very many declarations (repeated concatenation is quadratic)
        StringBuilder decl = new StringBuilder("(declare-fun obj.extends (String String) Bool) \n (declare-fun obj.method.of (String String String String) Bool)"); //todo: needed?
        int objectCount = 0;
        boolean traceComplete = false;
        Set<String> objectIdentifiers = new HashSet<>();

        for (String line : lines) {
            if (line.startsWith("[DECISION]")) {
                Decision d = parseDecision( line.substring("[DECISION]".length()), decl.toString(), config);
                if (d != null) {
                    decisions.add(d);
                }
            }
            else if (line.startsWith("[AUXILIARY]")) {
                decl.append(line.substring("[AUXILIARY]".length()));
            }
            else if (line.startsWith("[SUMMARY]")) {
                summeries.add(line.substring("[SUMMARY]".length()));
            }
            else if (line.startsWith("[OBJECT]")) {
                objectIdentifiers.add(line.substring("[OBJECT]".length()));
            }
            else if (line.startsWith("[DECLARE]")) {
                String declaration = line.substring("[DECLARE]".length());
                decl.append(declaration);

                declarations.add(declaration);

//                Pattern pattern = Pattern.compile("\\(declare-fun\\s+(__object_\\d+)\\s*\\(\\)\\s+Object\\)");
//                Matcher matcher = pattern.matcher(declaration);
//                if (matcher.find()) {
//                    objectIdentifiers.add(matcher.group());
//                }
            }
            else if (line.startsWith("[ERROR]")) {
                result = PathResult.error(vals, line.substring("[ERROR]".length()).trim(), "");
            }
            else if (line.startsWith("[TAINT VIOLATION]")) {
                //result = PathResult.error(vals, line.trim(), "");
                //TODO: not sure what we should do in this case?
                System.out.println(line.trim());
                taintViolations.add(line.substring("[TAINT VIOLATION]".length()).trim());
            }
            else if (line.startsWith("[ABORT]")) {
                result = PathResult.abort(vals, line.substring("[ABORT]".length()).trim());
            }
            else if (line.startsWith("[ASSUMPTION]")) {
                decisions.add(parseAssumption( line.substring("[ASSUMPTION]".length()), decl.toString()));
            }
            else if (line.startsWith("[WITNESS]")) {
                witness.add(parseWitnessAssumption( line.substring("[WITNESS]".length()).trim() ));
            }
            else if (line.startsWith("[FLOW]")) {
                flows.add( line.substring("[FLOW]".length()).trim() );
            }
            else if (line.startsWith("[TAINTCHECK]")) {
                flows.add( line.substring("[TAINTCHECK]".length()).trim() );
            }
            else if (line.startsWith("[META_INFOS]")) {
                objectCount = Integer.parseInt(line.substring("[META_INFOS] object_count:".length()).trim());
            }
            else if (line.startsWith("[ENDOFTRACE]")) {
                traceComplete = true;
            }
        }

        // TODO: maybe we could do better here if we have received half a trace?
        if (!traceComplete) {
            return null;
        }

        result.setTaintViolations(taintViolations);
        return new Trace(decisions, summeries, declarations, witness, flows, result, objectCount, objectIdentifiers);
    }

    /** @return the decision, or {@code null} if it is dropped (dispatch without static information) */
    public static Decision parseDecision(String decision,
                                         String decl,
                                         Config config) throws IOException, SMTLIBParserException {
        String[] parts = decision.split("\\/\\/ branchCount=|, branchId=");
        SMTProblem smt = null;
        String constraint = parts[0];
        int branches = Integer.parseInt(parts[1]);
        int branchId = Integer.parseInt(parts[2]);

        try {
            smt = SMTLIBParser.parseSMTProgram(decl + constraint);
        } catch (Throwable e) {
            System.err.println("Could not parse: " + decl + constraint);
            throw e;
        }

        if (!config.isBaselineEvaluation()) {
            // Special Handling of branchCount & BranchId of obj.method.of (SPout sets Unknown because it has no information
            // how many polymorph methods exists and which id they own in DSE)
            // the class argument is __object_k.cls or a term (obj.cls <object>) of the heap model
            Pattern pattern = Pattern.compile("obj\\.method\\.of\\s[^\"]*\"([^\"]*)\"\\s+\"([^\"]*)\"\\s+\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(constraint);
            if (matcher.find()) {
                String methodName = matcher.group(1);
                String methodDescriptor = matcher.group(2);
                String definingClass = matcher.group(3);

                StaticManager.DispatchBranch branch = config.getSmtProblemManager().getStaticManager()
                        .dispatchBranch(methodName, methodDescriptor, definingClass);
                if (branch == null) {
                    System.out.println("WARNING: too many definitions of " + methodName + methodDescriptor
                            + " missing in the polymorphism table; dropping the dispatch decision");
                    return null;
                }
                if (!branch.known()) {
                    // the method is missing in the static polymorphism table (e.g. a method of a JDK class, called on
                    // an object in a field): obj.method.of has no rows for it (grounded: false), so the class of the
                    // object is not constrained by this branch. The branch keeps the decision tree consistent with
                    // traces that dispatch to a known definition here.
                    System.out.println("WARNING: no polymorphism information for " + definingClass + "::" + methodName
                            + methodDescriptor + "; unconstrained dispatch branch " + branch.branchId());
                    return new Decision(ExpressionUtil.TRUE, branch.branchCount(), branch.branchId());
                }
                return new Decision(ExpressionUtil.and(smt.assertions), branch.branchCount(), branch.branchId());
            }

            //Special Handling of branchCount & BranchId of (assert (= __object_0.err "{ERROR_TYPE}")) because
            // SPout has no information how many error per constructor can occur
            Pattern errorPattern = Pattern.compile("\\(assert \\(= (__object_\\d+)\\.err \"(.*?)\"\\)\\)");
            Matcher errorMatcher = errorPattern.matcher(constraint);
            if (errorMatcher.find()) {
                String objectName = errorMatcher.group(1);
                String errorMessage = errorMatcher.group(2);
                ConstructorSummaryManager csm = config.getSmtProblemManager().getConstructorSummaryManager();
                List<String> possibleErrorsWithInConstructors = csm.getPossibleErrorsWithInConstructors();

                if (csm.hasStaticErrorList()) {
                    return parseErrorDecisionWithStaticErrors(
                            objectName, errorMessage, possibleErrorsWithInConstructors, decl, smt);
                }

                //todo: error index für leer
                int currentBranchCount = possibleErrorsWithInConstructors.size()+1;
                int currentBranchId = errorMessage.isEmpty() ?
                        currentBranchCount-1 : possibleErrorsWithInConstructors.indexOf(errorMessage);

                assert (currentBranchId != -1) : "Error message " + errorMessage + " not found in possible errors with in constructors";

                return new Decision(ExpressionUtil.and(smt.assertions), currentBranchCount, currentBranchId);
            }
        }

        return new Decision( ExpressionUtil.and(smt.assertions), branches, branchId);

    }

    /**
     * Decision on {@code o.err} with a static list {@code E_0 ... E_{n-1}} of possible constructor errors (computed
     * by the static error inference in incremental mode). Branches: {@code 0..n-1} for {@code o.err = E_i}, {@code n}
     * for "some other error" (an error the static analysis did not predict), {@code n+1} for no error. The condition
     * of the "other" branch is {@code not (o.err = "" or o.err = E_0 or ...)}, so that the branch has a well-defined
     * meaning independently of which unpredicted error was observed first.
     */
    static Decision parseErrorDecisionWithStaticErrors(String objectName,
                                                               String errorMessage,
                                                               List<String> errors,
                                                               String decl,
                                                               SMTProblem smt) throws IOException, SMTLIBParserException {
        int branchCount = errors.size() + 2;
        if (errorMessage.isEmpty()) {
            return new Decision(ExpressionUtil.and(smt.assertions), branchCount, branchCount - 1);
        }
        int index = errors.indexOf(errorMessage);
        if (index >= 0) {
            return new Decision(ExpressionUtil.and(smt.assertions), branchCount, index);
        }

        System.out.println("WARNING: constructor error " + errorMessage + " of " + objectName
                + " was not predicted by the static error analysis; using the \"other error\" branch");
        StringBuilder known = new StringBuilder(String.format("(= %s.err \"\")", objectName));
        for (String error : errors) {
            known.append(String.format(" (= %s.err \"%s\")", objectName, error));
        }
        SMTProblem other = SMTLIBParser.parseSMTProgram(decl + "(assert (not (or " + known + ")))");
        return new Decision(ExpressionUtil.and(other.assertions), branchCount, errors.size());
    }

    private static Expression<Boolean> parseExtends(String constraint,
                                                    ClazzModel clazzModel) {
        // 1. Extract parameter of the extends-assert statement
        ParseResult parseResult = extractValuesFromExtendAssertStatement(constraint);

        // 2. Determine subclasses
//        ClassHierarchyParser parser = new ClassHierarchyParser();
//        parser.parse("class LA; { LA;|()V, LA;|(II)V}\n" +
//                "class LB; extends LA;{ LB;|()V}\n" +
//                "Ljava/lang/Integer; {}\n" +
//                "Ljava/lang/String; {}") ; //todo: Read this from file
//
//        Set<String> subclasses = parser.getAllSubclasses(parseResult.className);
        Clazz clazz = clazzModel.getClazzes().get(parseResult.className);


        List<String> subclasses = new ArrayList<>(clazz.getAllSubClazzes());


        // 3. Add the class itself and null to the array (because every class can be casted to itself and null can be
        // casted to every class)
        subclasses.add(parseResult.className);
        subclasses.add("null");

        // 4. Creates a variable __object_{i} as String
        Variable<String> obj0Var = Variable.create(BuiltinTypes.STRING, parseResult.objectName);

        // 5. Construct the logic formular for the DecisionNode
        List<Expression<Boolean>> expressionList = new ArrayList<>();
        for (String className: subclasses) {
            // 5.1. Creates a constant for the klassName as String
            Constant<String> const1 = Constant.create(BuiltinTypes.STRING, className);

            // 5.2. Creates the application of the uninterpreted function extends(__object_{i}, {klassName})
            Expression<Boolean> extApp1 =
                    new FunctionExpression<>(ClazzModel.extendsFct, obj0Var, const1);

            expressionList.add(extApp1);
        }

        // 6. Connect subexpressions with a big OR
        Expression<Boolean> completExpression = ExpressionUtil.or(expressionList);

        // 7. negate the instance_of function if necessary (e.g. (not (instance_of __object_0, "LA")))
        completExpression = parseResult.negated ? new Negation(completExpression) : completExpression;
        return completExpression;
    }

    private static Expression<Boolean> parseInstanceOf(String constraint) {

        // 1. Extract parameter of the extends-assert statement
        ParseResult parseResult = extractValuesFromInstanceOfAssertStatement(constraint);

        // 1)create variable (e.g. __object_0) as String
        Variable<String> obj0Var = Variable.create(BuiltinTypes.STRING, parseResult.objectName);

        // 2) create constant for class (e.g. "LA;")
        Constant<String> laConst = Constant.create(BuiltinTypes.STRING, parseResult.className);

        // 3) create function extends (e.g. (extends __object_0, "LA"))
        Expression<Boolean> extApp =
                new FunctionExpression<>(ClazzModel.instanceofFct, obj0Var, laConst);

        // 4) negate the extends function if necessary (e.g. (not (extends __object_0, "LA")))
        extApp = parseResult.negated ? new Negation(extApp) : extApp;

        return extApp;
    }

    /**
     * Extracts objectName "__object_{i}" and ClassName"L{...};" and whether the expression is negated from an extends
     * expression
     *
     * @param text          The input string.
     * @return ParseResult  Contains the extracted values
     */
    private static ParseResult extractValuesFromExtendAssertStatement(String text) {
        // count how often "(not" is a prefix of "extends"
        int notCount = 0;
        Pattern notPattern = Pattern.compile("\\(not");
        Matcher notMatcher = notPattern.matcher(text);

        while (notMatcher.find() && notMatcher.start() < text.indexOf("extends")) {
            notCount++;
        }

        // The regular expression that defines the two values as capturing groups.
        // Group 1: (__object_[^ ]+)
        // Group 2: (L[^;]+)
        String regex = "__object_([0-9]+).*?(L[^;]+;)";;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            // Reconstruct the full __object_ string for the first group
            String objectName = "__object_" + matcher.group(1);

            // The second group already captures the full L string
            String clazzName = matcher.group(2);

            boolean negated = notCount % 2 != 0;

            return new ParseResult(objectName, clazzName, negated);
        } else {
            throw new IllegalStateException("no \"extends\" found !!!");
        }
    }
    /**
     * Extracts objectName "__object_{i}" and ClassName"L{...};" and whether the expression is negated from an
     * instanceof expression
     *
     * @param text          The input string.
     * @return ParseResult  Contains the extracted values
     */
    private static ParseResult extractValuesFromInstanceOfAssertStatement(String text) {
        // count how often "(not" is a prefix of "instance_of"
        int notCount = 0;
        Pattern notPattern = Pattern.compile("\\(not");
        Matcher notMatcher = notPattern.matcher(text);

        while (notMatcher.find() && notMatcher.start() < text.indexOf("instance_of")) {
            notCount++;
        }

        // The regular expression that defines the two values as capturing groups.
        // Group 1: (__object_[^ ]+)
        // Group 2: (L[^;]+)
        String regex = "__object_([0-9]+).*?(L[^;]+;)";;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            // Reconstruct the full __object_ string for the first group
            String objectName = "__object_" + matcher.group(1);

            // The second group already captures the full L string
            String clazzName = matcher.group(2);

            boolean negated = notCount % 2 != 0;

            return new ParseResult(objectName, clazzName, negated);
        } else {
            throw new IllegalStateException("no \"instance_of\" found !!!");
        }
    }



    public static Decision parseAssumption(String assumption, String decl) throws IOException, SMTLIBParserException {
        String[] parts = assumption.split("\\/\\/ sat=");
        SMTProblem smt = null;
        try {
            smt = SMTLIBParser.parseSMTProgram(decl + parts[0]);
        } catch (Throwable e) {
            System.err.println("Could not parse: " + decl + parts[0]);
            throw e;
        }
        boolean sat = Boolean.parseBoolean(parts[1]);
        return new Decision( ExpressionUtil.and(smt.assertions), 2, sat ? 1 : 0, true);
    }

    private static WitnessAssumption parseWitnessAssumption(String data) {
        String[] parts = data.split("\\:", 4);
        return new WitnessAssumption(parts[3].trim(), parts[0].trim(), parts[1].trim(), Integer.parseInt(parts[2].trim()));
    }

    public static class ParseResult {
        public final String objectName;
        public final String className;
        public final boolean negated;

        public ParseResult(String objectName,
                           String className,
                           boolean negated) {
            this.objectName = objectName;
            this.className = className;
            this.negated = negated;
        }
    }

}
