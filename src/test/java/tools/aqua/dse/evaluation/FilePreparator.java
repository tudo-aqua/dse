package tools.aqua.dse.evaluation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FilePreparator {

    private static final Path KLASS_HIERARCHY_DIRECTORY = Path.of("src/test/resources/hierarchy/generated");
    private static final Path EXAMPLE_DIRECTORY = Path.of("src/test/resources/examples/generated");

    /**
     * Creates a single file in the given target directory.
     *
     * @param fileName        Name of the file (must end with ".java").
     *
     * @param targetDirectory Target folder where the file will be created.
     */
    public static void createSingleFile(String fileName,
                                        String fileExtension,
                                        String fileContent,
                                        Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);

            Path filePath = targetDirectory.resolve(fileName+"."+fileExtension);

            Files.writeString(filePath, fileContent);

            System.out.println("File created: " + filePath);
        } catch (IOException e) {
            System.err.println("An error occurred while creating the file:");
            e.printStackTrace();
        }
    }


    // ***************************************************************************************************************
    //                                         Object Attribute Scaling Test
    // ***************************************************************************************************************

    private static String generateSingleKlassInAttributeDepthHierarchy(int klassId,
                                                                   boolean isLastKlass) {
        String classToConstruct = String.format("LAttributeDepth%d;", klassId);
        String nextClass = String.format("LAttributeDepth%d;", klassId + 1);
        String constructorString = String.format("%s|(%s)V", classToConstruct, nextClass);
        String constructorStringLastKlass = String.format("%s|()V", classToConstruct);

        return String.format("class %s {%s}%n",
                classToConstruct,
                isLastKlass ? constructorStringLastKlass: constructorString);
    }

    private static String generateContentAttributeDepthHierarchy(int depth) {
        StringBuilder result = new StringBuilder();
        for  (int i = 1; i <= depth; i++) {
            result.append(generateSingleKlassInAttributeDepthHierarchy(i, i == depth && depth != 1));
        }

        return result.toString();
    }

    private static void scalingObjectAttribute_createHierarchyFile(int depth) {
        for (int i = 1; i <= depth; i++) {
            createSingleFile(String.format("AttributeDepthHierarchy%d", i),
                    "txt",
                    generateContentAttributeDepthHierarchy(i),
                    KLASS_HIERARCHY_DIRECTORY);
        }
    }

    private static String generateContentJavaAttributeDepth(int klassId,
                                                        boolean isLastKlass) {
        String classToConstruct = String.format("AttributeDepth%d", klassId);
        String nextClass = String.format("AttributeDepth%d", klassId + 1);

        String noArgConstructorString = String.format("%n\tpublic %s() {}", classToConstruct);
        String constructorString = String.format("%n\tpublic %s(%s var){}%n", classToConstruct, nextClass);

        return String.format("public class %s {%s %s}",
                classToConstruct,
                noArgConstructorString,
                isLastKlass ? "" : constructorString);
    }

    private static List<String> scalingObjectAtributes_createJavaClasses(int depth) {
        List<String> namesOfCreatedJavaClasses = new ArrayList<>();
        for  (int i = 1; i <= depth; i++) {
            String className = String.format("AttributeDepth%d", i);
            namesOfCreatedJavaClasses.add(className);
            createSingleFile(className,
                    "java",
                    generateContentJavaAttributeDepth(i, i == depth),
                    EXAMPLE_DIRECTORY);
        }
        return namesOfCreatedJavaClasses;
    }

    private static String scalingObjectAtributes_createExamples() {
        String className = "ExampleScalingInnerClasses";
        String fileContent =String.format(
                """
                import tools.aqua.concolic.Verifier;
                
                public class %s {
                    public static void main(String[] args) {
                        Object o = Verifier.nondetObject();
                    }
                }
                """, className);

        createSingleFile(className, "java", fileContent, EXAMPLE_DIRECTORY);
        return className;
    }

    public static void setUpAttributeScalingTest(int depth) throws IOException, InterruptedException {
        scalingObjectAttribute_createHierarchyFile(depth);
        List<String> javaKlassNames = scalingObjectAtributes_createJavaClasses(depth);
        javaKlassNames.add(scalingObjectAtributes_createExamples());
        compileClasses(javaKlassNames, "src/test/resources/examples/generated");
    }

    // ***************************************************************************************************************
    //                                         Constructor Scaling Test
    // ***************************************************************************************************************
    private static final String SCALING_CONSTRUCTORS_CLASS_IDENTIFIER = "ScalingConstructor";

    private static String scalingConstructors_generateClassHierarchy(int klassId) {
        String classToConstruct = String.format("L%s;", SCALING_CONSTRUCTORS_CLASS_IDENTIFIER);

        StringBuilder constructorStringBuilder = new StringBuilder();

        for (int i = 0; i < klassId; i++) {
            constructorStringBuilder.append(
                    String.format("%s|(%s)V,",
                            classToConstruct,
                            "I".repeat(i))
            );
        }

        String constructorString = constructorStringBuilder.substring(0, Math.max(0,constructorStringBuilder.length() - 1));


        return String.format("class %s {%s}",
                classToConstruct,
                constructorString);
    }

    private static void scalingConstructors_createAllClassHierarchies(int numberOfConstructors) {
        for (int i = 0; i <= numberOfConstructors; i++) {
            createSingleFile(String.format("scaling_constructors_%d_hierarchy", i),
                    "txt",
                    scalingConstructors_generateClassHierarchy(i),
                    KLASS_HIERARCHY_DIRECTORY);
        }
    }

    private static String scalingConstructors_generateSingleJavaConstructor(int numberOfParameters) {
        StringBuilder parameterListStringBuilder = new StringBuilder();
        for (int i = 0; i < numberOfParameters; i++) {
            parameterListStringBuilder.append(String.format("int var%d, ", i));
        }
        String parameterList = parameterListStringBuilder.substring(0, Math.max(0, parameterListStringBuilder.length() - 2));

        return String.format("%n\tpublic %s(%s) {}", SCALING_CONSTRUCTORS_CLASS_IDENTIFIER, parameterList);
    }

    private static String scalingConstructors_generateJavaClassContent(int numberOfConstructors) {
        StringBuilder constructorsString = new StringBuilder();
        for (int i = 0; i < numberOfConstructors; i++) {
            constructorsString.append(scalingConstructors_generateSingleJavaConstructor(i));
        }

        return String.format("public class %s {%s%n}",  SCALING_CONSTRUCTORS_CLASS_IDENTIFIER, constructorsString);
    }

    private static String scalingConstructors_createJavaClass(int numberOfConstructors) {
        String filename = String.format("%s", SCALING_CONSTRUCTORS_CLASS_IDENTIFIER);
        createSingleFile(filename,
                "java",
                scalingConstructors_generateJavaClassContent(numberOfConstructors),
                EXAMPLE_DIRECTORY);
        return filename;
    }

    private static String scalingConstructors_createExamples() {
        String className = "ExampleScalingConstructors";
        String fileContent =String.format(
                """
                import tools.aqua.concolic.Verifier;
                
                public class %s {
                    public static void main(String[] args) {
                        Object o = Verifier.nondetObject();
                    }
                }
                """, className);

        createSingleFile(className, "java", fileContent, EXAMPLE_DIRECTORY);
        return className;
    }


    public static void setUpConstructorScalingTest(int numberOfConstructors) throws IOException, InterruptedException {
        List<String> listOfClassNames = new ArrayList<>();
        scalingConstructors_createAllClassHierarchies(numberOfConstructors);
        listOfClassNames.add(scalingConstructors_createJavaClass(numberOfConstructors));
        listOfClassNames.add(scalingConstructors_createExamples());
        compileClasses(listOfClassNames, "src/test/resources/examples/generated");

    }

    // ***************************************************************************************************************
    //                                            Extends WIDTH Test
    // ***************************************************************************************************************

    private static final String SCALING_EXTENDS_WIDTH_IDENTIFIER = "ExtendsWidth";

    private static String scalingExtendsWidth_generateSingleKlassInHierarchy(int klassId,
                                                                             boolean isFirstClass) {
        String classToConstruct = String.format("L%s%d;",
                SCALING_EXTENDS_WIDTH_IDENTIFIER,
                klassId);

        String firstClass =  String.format("L%s%d;",
                SCALING_EXTENDS_WIDTH_IDENTIFIER,
                0);

        return String.format("class %s %s {%s|()V}",
                classToConstruct,
                isFirstClass ? "": String.format("extends %s",firstClass),
                classToConstruct);
    }

    private static String scalingExtendsWidth_generateContentOfSingleClassHierarchy(int width) {
        StringBuilder contentClassHierarchy = new StringBuilder();
        for (int i = 0; i <= width; i++) {
            contentClassHierarchy.append(scalingExtendsWidth_generateSingleKlassInHierarchy(i, i == 0)).append("\n");
        }
        return contentClassHierarchy.toString();
    }

    private static void scalingExtendsWidth_createSingleClassHierarchyFile(int width) {
        createSingleFile(String.format("%s%d", SCALING_EXTENDS_WIDTH_IDENTIFIER, width),
                "txt",
                scalingExtendsWidth_generateContentOfSingleClassHierarchy(width),
                KLASS_HIERARCHY_DIRECTORY);
    }

    private static void scalingExtendsWidth_createAllClassHierarchyFiles(int width) {
        for (int i = 0; i <= width; i++) {
            scalingExtendsWidth_createSingleClassHierarchyFile(i);
        }
    }

    //------------------------------------------------------------------------------------------------------------
    private static String scalingExtendsWidth_generateContentOfSingleJavaClass(int klassId,
                                                                               boolean isFirstClass) {
        String className = String.format("%s%d", SCALING_EXTENDS_WIDTH_IDENTIFIER, klassId);
        String extendsFirstClass = String.format("extends %s0", SCALING_EXTENDS_WIDTH_IDENTIFIER);

        return String.format("""
                public class %s %s{
                    public %s(){}
                }
                """,
                className,
                isFirstClass ? "": extendsFirstClass,
                className);
    }

    private static String scalingExtendsWidth_createSingleJavaClass(int klassId) {
        String className = String.format("%s%d", SCALING_EXTENDS_WIDTH_IDENTIFIER, klassId);
        createSingleFile(className,
                "java",
                scalingExtendsWidth_generateContentOfSingleJavaClass(klassId, klassId == 0),
                EXAMPLE_DIRECTORY);
        return className;
    }

    private static List<String> scalingExtendsWidth_createAllJavaClasses(int width) {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= width; i++) {
            listOfClassNames.add(scalingExtendsWidth_createSingleJavaClass(i));
        }
        return listOfClassNames;
    }


    //------------------------------------------------------------------------------------------------------------
    private static String scalingExtendsWidth_createExamples() {
        String className = "ExampleExtendsWidth";
        String fileContent =String.format(
                """
                import tools.aqua.concolic.Verifier;
                
                public class %s {
                    public static void main(String[] args) {
                        Object o = Verifier.nondetObject();
                    }
                }
                """, className);

        createSingleFile(className, "java", fileContent, EXAMPLE_DIRECTORY);
        return className;
    }


    //------------------------------------------------------------------------------------------------------------


    public static void setUpExtendsWidthTest(int width) throws IOException, InterruptedException {
        scalingExtendsWidth_createAllClassHierarchyFiles(width);
        List<String> listOfClasses = scalingExtendsWidth_createAllJavaClasses(width);
        listOfClasses.add(scalingExtendsWidth_createExamples());
        compileClasses(listOfClasses, "src/test/resources/examples/generated");
    }

    // ***************************************************************************************************************
    //                                            Extends Depth Test
    // ***************************************************************************************************************

    private static final String SCALING_EXTENDS_DEPTH_IDENTIFIER = "ExtendsDepth";

    private static String scalingExtendsDepth_generateSingleKlassInHierarchy(int klassId,
                                                                             boolean isFirstClass) {
        String classToConstruct = String.format("L%s%d;",
                SCALING_EXTENDS_DEPTH_IDENTIFIER,
                klassId);

        String previousClass =  String.format("L%s%d;",
                SCALING_EXTENDS_DEPTH_IDENTIFIER,
                klassId-1);

        return String.format("class %s %s {%s|()V}",
                classToConstruct,
                isFirstClass ? "": String.format("extends %s",previousClass),
                classToConstruct);
    }

    private static String scalingExtendsDepth_generateContentOfSingleClassHierarchy(int depth) {
        StringBuilder contentClassHierarchy = new StringBuilder();
        for (int i = 0; i <= depth; i++) {
            contentClassHierarchy.append(scalingExtendsDepth_generateSingleKlassInHierarchy(i, i == 0)).append("\n");
        }
        return contentClassHierarchy.toString();
    }

    private static void scalingExtendsDepth_createSingleClassHierarchyFile(int depth) {
        createSingleFile(String.format("%s%d", SCALING_EXTENDS_DEPTH_IDENTIFIER, depth),
                "txt",
                scalingExtendsDepth_generateContentOfSingleClassHierarchy(depth),
                KLASS_HIERARCHY_DIRECTORY);
    }

    private static void scalingExtendsDepth_createAllClassHierarchyFiles(int depth) {
        for (int i = 0; i <= depth; i++) {
            scalingExtendsDepth_createSingleClassHierarchyFile(i);
        }
    }

    //------------------------------------------------------------------------------------------------------------
    private static String scalingExtendsDepth_generateContentOfSingleJavaClass(int klassId,
                                                                               boolean isFirstClass) {
        String className = String.format("%s%d", SCALING_EXTENDS_DEPTH_IDENTIFIER, klassId);
        String extendsFirstClass = String.format("extends %s0", SCALING_EXTENDS_DEPTH_IDENTIFIER);

        return String.format("""
                public class %s %s{
                    public %s(){}
                }
                """,
                className,
                isFirstClass ? "": extendsFirstClass,
                className);
    }

    private static String scalingExtendsDepth_createSingleJavaClass(int klassId) {
        String className = String.format("%s%d", SCALING_EXTENDS_DEPTH_IDENTIFIER, klassId);
        createSingleFile(className,
                "java",
                scalingExtendsDepth_generateContentOfSingleJavaClass(klassId, klassId == 0),
                EXAMPLE_DIRECTORY);
        return className;
    }

    private static List<String> scalingExtendsDepth_createAllJavaClasses(int depth) {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= depth; i++) {
            listOfClassNames.add(scalingExtendsDepth_createSingleJavaClass(i));
        }
        return listOfClassNames;
    }
    //------------------------------------------------------------------------------------------------------------
    private static String scalingExtendsDepth_createExamples() {
        String className = "ExampleExtendsDepth";
        String fileContent =String.format(
                """
                import tools.aqua.concolic.Verifier;
                
                public class %s {
                    public static void main(String[] args) {
                        Object o = Verifier.nondetObject();
                    }
                }
                """, className);

        createSingleFile(className, "java", fileContent, EXAMPLE_DIRECTORY);
        return className;
    }



    //------------------------------------------------------------------------------------------------------------

    public static void setUpExtendsDepthTest(int depth) throws IOException, InterruptedException {
        scalingExtendsDepth_createAllClassHierarchyFiles(depth);
        List<String> classNames = scalingExtendsDepth_createAllJavaClasses(depth);
        classNames.add(scalingExtendsDepth_createExamples());
        compileClasses(classNames, "src/test/resources/examples/generated");
    }

    // ***************************************************************************************************************
    //                                           Scaling nonDetObject
    // **************************************************************************************************************

    private static final String SCALING_NON_DET_OBJECT_IDENTIFIER = "ScalingNonDetObject";


    private static String scalingNonDetObject_generateKlassHierarchyContent() {
        return """
                class LScalingNonDetObject1; { LScalingNonDetObject1;|()V, LScalingNonDetObject1;|(I)V}
                class LScalingNonDetObject2; { LScalingNonDetObject2;|()V}
               """;
    }

    private static void scalingNonDetObject_classHierarchy() {
        createSingleFile(SCALING_NON_DET_OBJECT_IDENTIFIER,
                "txt",
                scalingNonDetObject_generateKlassHierarchyContent(),
                KLASS_HIERARCHY_DIRECTORY);
    }

    private static String scalingNonDetObject_generateSingleNonDetObjectCall(int id) {
        return String.format("Object o%d = Verifier.nondetObject();", id);
    }

    private static String scalingNonDetObject_generateAllNonDetObjectCall(int numberNonDetObjectsCalls) {
        StringBuilder allNonDetObjectCalls = new StringBuilder();
        for (int i = 1; i <= numberNonDetObjectsCalls; i++) {
            allNonDetObjectCalls.append(scalingNonDetObject_generateSingleNonDetObjectCall(i)).append("\n\t\t");
        }
        return allNonDetObjectCalls.toString();
    }

    private static String scalingNonDetObject_generateContentForExampleFile(int numberNonDetObjectsCalls) {
        String className = String.format("Example%s%d", SCALING_NON_DET_OBJECT_IDENTIFIER, numberNonDetObjectsCalls);
        return String.format("""
                import tools.aqua.concolic.Verifier;
                
                public class %s {
                    public static void main(String[] args) {
                        %s
                    }
                }
                """,
                className,
                scalingNonDetObject_generateAllNonDetObjectCall(numberNonDetObjectsCalls));
    }

    private static String scalingNonDetObject_createSingleExampleFile(int numberNonDetObjectsCalls) {
        String className = String.format("Example%s%d", SCALING_NON_DET_OBJECT_IDENTIFIER, numberNonDetObjectsCalls);
        createSingleFile(className,
                "java",
                scalingNonDetObject_generateContentForExampleFile(numberNonDetObjectsCalls),
                EXAMPLE_DIRECTORY);

        return className;
    }

    private static List<String> scalingNonDetObject_createAllExampleFiles(int numberNonDetObjectsCalls) {
        List<String> listOfKlassNames =  new ArrayList<>();
        for (int i = 1; i <= numberNonDetObjectsCalls; i++) {
            listOfKlassNames.add(scalingNonDetObject_createSingleExampleFile(i));
        }
        return listOfKlassNames;
    }

    private static List<String> scalingNonDetObject_generateJavaFiles() {
        createSingleFile("ScalingNonDetObject1",
                "java",
                """
                       public class ScalingNonDetObject1 {
                           public ScalingNonDetObject1() {
                           \s
                            }
                           \s
                            public ScalingNonDetObject1(int var1) {
                           \s
                            }
                       }
                       """,
                EXAMPLE_DIRECTORY);

        createSingleFile("ScalingNonDetObject2",
                "java",
                """
                       public class ScalingNonDetObject2 {
                           public ScalingNonDetObject2() {
                           \s
                            }
                       }
                       """,
                EXAMPLE_DIRECTORY);


        return List.of("ScalingNonDetObject1", "ScalingNonDetObject2");
    }


    public static void setUpNonDetObjectTest(int numberNonDetObjectsCalls) throws IOException, InterruptedException {
        scalingNonDetObject_classHierarchy();
        List<String> listOfKlassNames = scalingNonDetObject_createAllExampleFiles(numberNonDetObjectsCalls);
        listOfKlassNames.addAll(scalingNonDetObject_generateJavaFiles());
        compileClasses(listOfKlassNames, "src/test/resources/examples/generated");
    }


    /**
     * Executes the compilation command for a given Java class.
     * The paths are relative to the project root directory.
     * @param className The name of the class to compile (without .java extension).
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting for the process to complete.
     */
    public static void compileClass(String className,
                                    String directoryOfTheClassesToCompile) throws IOException, InterruptedException {
        // Paths relative to the project root directory
        String verifierStub = "../verifier-stub/target/verifier-stub-1.0.jar";
        String examplesPath = directoryOfTheClassesToCompile;

        // Assemble the classpath. The separator is ':' for macOS/Linux.
        String classPath = verifierStub + File.pathSeparator + examplesPath;

        // The source file to be compiled
        String sourceFile = examplesPath + className + ".java";

        System.out.println("Starting compilation of: " + sourceFile);

        // Create a ProcessBuilder for the external call
        ProcessBuilder processBuilder = new ProcessBuilder(
                "javac",
                "-cp",
                classPath,
                sourceFile
        );

        // Set the working directory to the project root (optional, but good practice).
        // Adjust the path if necessary, or remove this line if it works without it.
        processBuilder.directory(Paths.get("").toAbsolutePath().toFile());

        // Merge the error and standard output streams
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read and print the process output (important for debugging)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        // Wait for the process to finish and check the exit code
        int exitCode = process.waitFor();
        System.out.println("Compilation finished with exit code: " + exitCode);

        // Check if the compilation was successful (exit code 0)
        if (exitCode != 0) {
            org.junit.jupiter.api.Assertions.fail("Compilation failed with exit code " + exitCode);
        }
    }

    public static void compileClasses(List<String> classNames,
                                      String directoryOfTheClassesToCompile) throws IOException, InterruptedException {
        for (String className : classNames) {
            compileClass(className, directoryOfTheClassesToCompile);
        }
    }



    public static void main(String[] args) throws IOException, InterruptedException {
        setUpConstructorScalingTest(3);
//        setUpExtendsWidthTest(3);
//        setUpExtendsDepthTest(3);
//        setUpNonDetObjectTest(3);
        setUpAttributeScalingTest(3);
        setUpAttributeScalingTest(3);
    }
}
