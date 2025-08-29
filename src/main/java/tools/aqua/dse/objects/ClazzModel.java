package tools.aqua.dse.objects;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.*;
import gov.nasa.jpf.constraints.expressions.functions.Function;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.trace.Trace;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static gov.nasa.jpf.constraints.expressions.StringBooleanOperator.EQUALS;

public class ClazzModel {

    /**
     * Function that represents the "extends" relationship between classes.
     * It takes two strings (the names of two classes) and returns a boolean indicating if the first class extends the second class.
     */
    public static final Function<Boolean> extendsFct = new Function("extends",
            BuiltinTypes.BOOL, BuiltinTypes.STRING, BuiltinTypes.STRING);

    /**
     * Function that represents the "instance_of" relationship between classes.
     * It takes two strings (the names of two classes) and returns a boolean indicating if the first class extends the second class.
     */
    public static final Function<Boolean> instanceofFct = new Function("instance_of",
            BuiltinTypes.BOOL, BuiltinTypes.STRING, BuiltinTypes.STRING);

    /**
     * Function that represents the initialization of a class by a constructor.
     * It takes two strings (a constructor name and a class name) and returns a boolean indicating if the constructor initializes the class.
     */
    public static final Function<Boolean> initializesFct = new Function("initializes",
            BuiltinTypes.BOOL, BuiltinTypes.STRING, BuiltinTypes.STRING);

    /** Map of class names to their corresponding Clazz instances */
    private final HashMap<String, Clazz> clazzes = new HashMap<>();

    private final Map<String, List<String>> subclassMap = new HashMap<>();


    /**
     * Takes the content of the file which is referenced in DSE-Argument "static.info"
     * and parse the contained class definitions into {@link Clazz} objects
     * @param config String containing a set of class definitions
     */
    public ClazzModel(String config) {
        clazzesFromString(config);
        createNullClazz();
    }

    public void addObjectConstraintsForTrace(Trace trace, SolverContext solverContext) {
        initObjectsStructure(solverContext);
        addConstructorInitializationConstraints(solverContext, trace.getObjectCount());
        addFiniteDomainConstraints(solverContext, trace.getObjectCount());
        addObjectNotIdentityConstraints(solverContext, trace.getObjectCount());
    }

    /**
     * Creates a NULL clazz and adds it to {@link #clazzes}
     */
    private void createNullClazz() {
        ArrayList<String> constructors = new ArrayList<String>();
        constructors.add("NULL");

        //Add NULL class to clazzes
        Clazz NULL = new Clazz(
                "null",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(), //todo;
                constructors);      // for a SMT-LIB Problem null needs a dummy constructor

        clazzes.put("null", NULL);
    }
//
//    /**
//     * Processes a text of MULTIPLE class definition and extracts for each class definition its name, superclasses and
//     * constructors.
//     * Each clazz is added to {@link #clazzes}.
//     *
//     * @param classDefinitions String containing a set of class definitions
//     *                         example:
//     *                         "class A { A() }
// *                              class C {}
// *                              class B extends A { B(), B(II) }"
//     */
//    private void clazzesFromString(String classDefinitions) {
//        classDefinitions = classDefinitions.trim();
//
//        //Process each class definition one by one
//        while (classDefinitions.startsWith("class")) {
//            //Extract next single class definition
//            int splitIndex =  classDefinitions.indexOf("}");
//            String singleClassDefinition = classDefinitions.substring(5, classDefinitions.indexOf("}")).trim();
//            //process extracted class definition
//            singleClassFromString(singleClassDefinition);
//            //Remove processed class definition
//            classDefinitions = classDefinitions.substring(splitIndex+1).trim();
//        }
//        if (!classDefinitions.isEmpty()) {
//            throw new RuntimeException("cannot parse: " + classDefinitions);
//        }
//    }

    /**
     * Processes a text of a SINGLE class definition and extracts its name, superclasses and constructors.
     * The clazz is then added to {@link #clazzes}.
     * @param clazzString String representing a single class definition.
     *                              example:
     *                              "class B extends A { B(), B(II) }"
     */
    public void clazzesFromString(String clazzString) {
//        //Each line corresponds to a definition of a single class. Therefore, splitt String at each new line
//        String[] clazzDefinitions = clazzString.split("\\R");
        Pattern classPattern = Pattern.compile("class\\s+L[^}]+}", Pattern.DOTALL);
        Matcher clazzMatcher = classPattern.matcher(clazzString);

//        for (String singleClazzDefinition : clazzDefinitions) {
        while (clazzMatcher.find()) {
            String singleClazzDefinition = clazzMatcher.group();
            // regex: class name required, extends and implements optional, then { ... }
            Pattern main = Pattern.compile(
                    "class+(L[^;]+;)*" +                          // group(1) = NECESSARY: class name (L...;)
                            "(?:extends+(L[^;]+;)*)?" +                 // group(2) = OPTIONAL: superClass (L...;)
                            "(?:implements*(.*?)*)?" +                  // group(3) = OPTIONAL: interfaces block (lazy)
                            "\\{(.*?)\\}",                                    // group(4) = NECESSARY: constructors block (lazy)
                    Pattern.DOTALL
            );

            //Replace ALL whitespaces
            Matcher clazzComponentMatcher = main.matcher(singleClazzDefinition.replaceAll("\\s+", ""));

            //Check if the string complies with the format
            if (!clazzComponentMatcher.find()) {
                throw new IllegalArgumentException("Input does not match expected pattern (missing class name or braces): " + singleClazzDefinition);
            }

            //Extract the components from the singleClazzDefinition
            String name = clazzComponentMatcher.group(1);
            String superClass = clazzComponentMatcher.group(2);
            List<String> interfaces = clazzComponentMatcher.group(3) != null ? Arrays.asList(clazzComponentMatcher.group(3).split(",")) : new ArrayList<>();
            List<String> constructors = Arrays.asList(clazzComponentMatcher.group(4).split(","));

            //Save subclass relation
            if (superClass != null) {
                subclassMap.computeIfAbsent(superClass, k -> new ArrayList<>()).add(name);
            }

            //Create a Clazz object and put it overall Map
            Clazz clazz = new Clazz(name,
                    superClass,
                    interfaces,
                    constructors);

            this.clazzes.put(name, clazz);
        }

        for (Clazz clazz : clazzes.values()) {
            //Set DIRECT subclasses
            clazz.setDirectSubClazzes(getDirectSubclasses(clazz.getName()));

            //Compute and set ALL subclasses
            clazz.setAllSubClazzes(new ArrayList<>(getAllSubclasses(clazz.getName())));
        }
    }

    /**
     * Get all direct subclasses of a class.
     */
    private List<String> getDirectSubclasses(String className) {
        return this.subclassMap.getOrDefault(className, Collections.emptyList());
    }

    /**
     * Get all subclasses (transitive).
     */
    private Set<String> getAllSubclasses(String className) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(getDirectSubclasses(className));
        while (!queue.isEmpty()) {
            String sub = queue.poll();
            if (result.add(sub)) {
                queue.addAll(getDirectSubclasses(sub));
            }
        }
        return result;
    }

    /**
     * Converts the class model stored in {@link #clazzes} into a SMT-LIB problem.
     * The SMT-LIB problem is encoded with the help of the classes of the JAVA PATH FINDER.
     *
     * @param ctx context of the SMT solver used to check this SMT problem
     */
    public void initObjectsStructure(SolverContext ctx) {
        ArrayList<String> allConstructorNames = new ArrayList<>();

        System.out.println("Add general extends constraints");
        //Add an "extends" constraint for each combination of classes
        for (Clazz leftClazz : clazzes.values()) {
            allConstructorNames.addAll(leftClazz.getConstructors());

            //Iterator over all other classes
            for (Clazz rightClazz : clazzes.values()) {
                addConstraint(ctx, extendsFct, leftClazz.getName(), rightClazz.getName(), leftClazz.isCastable(rightClazz));
                //clazzes.get(rightClazz).getAllSubClazzes().contains(leftClazz.getName())leftClazz.isSuperClazz(rightClazz)
            }
        }

        System.out.println("Add general instance_of constraints");
        //Add an "instance_of" constraint for each combination of classes
        for (Clazz leftClazz : clazzes.values()) {

            //Iterator over all other classes
            for (Clazz rightClazz : clazzes.values()) {
                addConstraint(ctx, instanceofFct, leftClazz.getName(), rightClazz.getName(), leftClazz.isInstanceOf(rightClazz));
            }
        }

        System.out.println("Add general initializes constraints");
        // Add an "initialize" constraint for each combination of class and constructor
        for (Clazz clazz : clazzes.values()) {
            for (String constructor : allConstructorNames) {
                addConstraint(ctx, initializesFct, constructor, clazz.getName(), clazz.hasConstructor(constructor));
            }
        }
    }

    public void addFiniteDomainConstraints(SolverContext ctx, int objectCount) {
        System.out.println("Add finite domain constraints");
        ArrayList<String> classNames = new ArrayList<>(this.clazzes.keySet());

        List<String> constructorNames = this.clazzes.values().stream()
                .map(Clazz::getConstructors)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<StringBooleanExpression> classExpressions = new ArrayList<>();
        List<StringBooleanExpression> constructorExpressions = new ArrayList<>();

        Expression<Boolean>[] orClassExpressions = new Expression[objectCount];
        Expression<Boolean>[] orConstructorExpressions = new Expression[objectCount];

        for (int i = 0; i < objectCount; i++) {
            classExpressions.clear();
            constructorExpressions.clear();

            for (String className : classNames) {
                classExpressions.add(new StringBooleanExpression(
                        new Variable<>(BuiltinTypes.STRING, "__object_" + i),
                        EQUALS,
                        new Constant<>(BuiltinTypes.STRING, className)));
            }
            orClassExpressions[i] = createBigOr(classExpressions);

            for (String constructorName : constructorNames) {
                constructorExpressions.add(new StringBooleanExpression(
                        new Variable<>(BuiltinTypes.STRING, "__object_constructor_"+i),
                        EQUALS,
                        new Constant<>(BuiltinTypes.STRING, constructorName)));
            }
            orConstructorExpressions[i] = createBigOr(constructorExpressions);
        }

        ctx.add(orClassExpressions);
        ctx.add(orConstructorExpressions);
    }

    public void addObjectNotIdentityConstraints(SolverContext ctx, int objectCount) {
        System.out.println("Add object not identity constraints");
        for (int i = 0; i < objectCount; i++) {
            for (int j = i+1; j < objectCount; j++) {
                    StringBooleanExpression stringBooleanExpression = new StringBooleanExpression(
                            new Variable<>(BuiltinTypes.STRING, "__object_" + i),
                            EQUALS,
                            new Variable<>(BuiltinTypes.STRING, "__object_" + j)
                    );

                    //negate the stringBoolea Expression
                    Negation negation = new Negation(stringBooleanExpression);
                    ctx.add(negation);
            }
        }
    }


    public Expression<Boolean> createBigOr(List<StringBooleanExpression> conditions) {
        Expression<Boolean> constraint = conditions.get(0);

        for (int i = 1; i < conditions.size(); i++) {
            constraint = new PropositionalCompound(constraint, LogicalOperator.OR, conditions.get(i));
        }
        //todo: ExpressionUtil

        return constraint;
    }

    public void addConstructorInitializationConstraints(SolverContext ctx, int objectCount) {
        System.out.println("Add concrete initialization constraints");
        List<Expression<Boolean>> initConstraints = new ArrayList<>();

        for (int i = 0; i < objectCount; i++) {
            initConstraints.add(new FunctionExpression(
                    initializesFct,
                    new Variable<>(BuiltinTypes.STRING, String.format("__object_constructor_%d", i)),
                    new Variable<>(BuiltinTypes.STRING, String.format("__object_%d", i)))
            );
        }
        ctx.add(initConstraints);
    }

    /**
     * Adds a constraint to the solver context
     * @param ctx           solver context
     * @param function      reference function
     * @param arg1          first argument
     * @param arg2          second argument
     * @param condition     indicates whether the relation is true or false
     */
    private void addConstraint(SolverContext ctx, Function<Boolean> function,
                               String arg1, String arg2, boolean condition) {
        FunctionExpression application = new FunctionExpression(function,
                new Constant<>(BuiltinTypes.STRING, arg1),
                new Constant<>(BuiltinTypes.STRING, arg2));
        ctx.add(new PropositionalCompound(application, LogicalOperator.EQUIV,
                condition ? ExpressionUtil.TRUE : ExpressionUtil.FALSE));
    }



    public HashMap<String, Clazz> getClazzes() {
        return clazzes;
    }

    @Override
    public String toString() {
        return "Objects{" +
                "clazzes=" + clazzes +
                '}';
    }
}
