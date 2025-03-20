package tools.aqua.dse.objects;

import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.LogicalOperator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.expressions.functions.Function;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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
     * Converts the class model stored in {@link #clazzes} into a SMT-LIB problem.
     * The SMT-LIB problem is encoded with the help of the classes of the JAVA PATH FINDER.
     *
     * @param ctx context of the SMT solver used to check this SMT problem
     */
    public void initObjectsStructure(SolverContext ctx) {
        ArrayList<String> allConstructorNames = new ArrayList<>();

        //Add an "extends" constraint for each combination of classes
        for (Clazz clazz : clazzes.values()) {
            allConstructorNames.addAll(Arrays.asList(clazz.getConstructors()));

            //Iterator over all other classes
            for (String clazzName : clazzes.keySet()) {
                addConstraint(ctx, extendsFct, clazz.getName(), clazzName, clazz.isSuperClazz(clazzName));
            }
        }

        // Add an "initialize" constraint for each combination of class and constructor
        for (Clazz clazz : clazzes.values()) {
            for (String constructor : allConstructorNames) {
                addConstraint(ctx, initializesFct, constructor, clazz.getName(), clazz.hasConstructor(constructor));
            }
        }
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


    /**
     * Takes the content of the file which is referenced in DSE-Argument "static.info"
     * and parse the contained class definitions into {@link Clazz} objects
     * @param config String containing a set of class definitions
     */
    public ClazzModel(String config) {
        clazzesFromString(config);
        createNullClazz();
    }

    /**
     * Creates a NULL clazz and adds it to {@link #clazzes}
     */
    private void createNullClazz() {
        //Get the names of all extracted classes
        String[] cNames = clazzes.keySet().toArray(new String[] {});

        //Add NULL class to clazzes
        Clazz NULL = new Clazz(
                "_NULL",
                cNames,                         // Null is a superclass of every class
                new String[] {"_NULL()"});      // for a SMT-LIB Problem null needs a dummy constructor

        clazzes.put("_NULL", NULL);
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
