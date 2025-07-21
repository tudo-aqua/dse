package tools.aqua.dse.objects;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.LogicalOperator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.expressions.StringBooleanExpression;
import gov.nasa.jpf.constraints.expressions.functions.Function;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import io.github.cvc5.Solver;
import tools.aqua.dse.trace.Trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static gov.nasa.jpf.constraints.expressions.StringBooleanOperator.EQUALS;

public class ClazzModel {

    /**
     * Function that represents the "extends" relationship between classes.
     * It takes two strings (the names of two classes) and returns a boolean indicating if the first class extends the second class.
     */
    public static final Function<BuiltinTypes.BoolType> extendsFct = new Function("extends",
            BuiltinTypes.BOOL, BuiltinTypes.STRING, BuiltinTypes.STRING);

    /**
     * Function that represents the initialization of a class by a constructor.
     * It takes two strings (a constructor name and a class name) and returns a boolean indicating if the constructor initializes the class.
     */
    public static final Function<BuiltinTypes.BoolType> initializesFct = new Function("initializes",
            BuiltinTypes.BOOL, BuiltinTypes.STRING, BuiltinTypes.STRING);

    /** Map of class names to their corresponding Clazz instances */
    private final HashMap<String, Clazz> clazzes = new HashMap<>();



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
    }

    /**
     * Creates a NULL clazz and adds it to {@link #clazzes}
     */
    private void createNullClazz() {
        //Get the names of all extracted classes
        String[] cNames = clazzes.keySet().toArray(new String[] {});

        //Add NULL class to clazzes
        Clazz NULL = new Clazz(
                "null",
                cNames,                         // Null is a superclass of every class
                new String[] {"NULL"});      // for a SMT-LIB Problem null needs a dummy constructor

        clazzes.put("null", NULL);
    }

    /**
     * Processes a text of MULTIPLE class definition and extracts for each class definition its name, superclasses and
     * constructors.
     * Each clazz is added to {@link #clazzes}.
     *
     * @param classDefinitions String containing a set of class definitions
     *                         example:
     *                         "class A { A() }
 *                              class C {}
 *                              class B extends A, C { B(), B(II) }"
     */
    private void clazzesFromString(String classDefinitions) {
        classDefinitions = classDefinitions.trim();

        //Process each class definition one by one
        while (classDefinitions.startsWith("class")) {
            //Extract next single class definition
            int splitIndex =  classDefinitions.indexOf("}");
            String singleClassDefinition = classDefinitions.substring(5, classDefinitions.indexOf("}")).trim();
            //process extracted class definition
            singleClassFromString(singleClassDefinition);
            //Remove processed class definition
            classDefinitions = classDefinitions.substring(splitIndex+1).trim();
        }
        if (!classDefinitions.isEmpty()) {
            throw new RuntimeException("cannot parse: " + classDefinitions);
        }
    }

    /**
     * Processes a text of a SINGLE class definition and extracts its name, superclasses and constructors.
     * The clazz is then added to {@link #clazzes}.
     * @param singleClassDefinition String representing a single class definition.
     *                              example:
     *                              "class B extends A, C { B(), B(II) }"
     */
    private void singleClassFromString(String singleClassDefinition) {
        singleClassDefinition = singleClassDefinition.trim();
        int splitIndex1 =  singleClassDefinition.indexOf("extends");
        int splitIndex2 =  singleClassDefinition.indexOf("{");
        if (splitIndex1 < 0) {
            splitIndex1 = splitIndex2;
        }

        //Extract class name
        String clazz = singleClassDefinition.substring(0, splitIndex1).trim();

        //Extract superclasses
        String[] superClasses = new String[] {};
        if (splitIndex1 < splitIndex2) {
            for (int i=0; i<superClasses.length; i++) {
                superClasses[i] = superClasses[i].trim();
            }
        }

        //todo: Add one variable "methods" to Clazz and insert methods at this point
        //Extract constructors
        String[] constructors = singleClassDefinition.substring(splitIndex2+1).trim().split(",");

        for (int i=0; i<constructors.length; i++) {
            constructors[i] = constructors[i].trim();
        }

        // If the constructors array is empty, it gets replaced with an empty array
        if (constructors.length == 1 && constructors[0].isEmpty()) {
            constructors = new String[] {};
        }

        // Add the class to the internal map of classes
        this.clazzes.put(clazz, new Clazz(clazz, superClasses, constructors));
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
        for (Clazz clazz : clazzes.values()) {
            allConstructorNames.addAll(Arrays.asList(clazz.getConstructors()));

            //Iterator over all other classes
            for (String clazzName : clazzes.keySet()) {
                addConstraint(ctx, extendsFct, clazz.getName(), clazzName, clazz.isSuperClazz(clazzName));
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
                .flatMap(Arrays::stream)
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
    private void addConstraint(SolverContext ctx, Function<BuiltinTypes.BoolType> function,
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
