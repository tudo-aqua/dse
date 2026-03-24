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
    private static final String DIRECTORY_CONSTRUCTOR_SCALING_TEST = "src/test/resources/generated/constructorScalingTest";
    private static final String DIRECTORY_EXTENDS_WIDTH_SCALING_TEST = "src/test/resources/generated/extendsWithScalingTest";
    private static final String DIRECTORY_EXTENDS_DEPTH_SCALING_TEST = "src/test/resources/generated/extendsDepthScalingTest";
    private static final String DIRECTORY_NON_DET_OBJECT_SCALING_TEST = "src/test/resources/generated/nonDetObjectScalingTest";
    private static final String DIRECTORY_OBJECT_ATTRIBUTE_SCALING_TEST = "src/test/resources/generated/objectAttributeScalingTest";

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
    private static final String SCALING_Attribute_IDENTIFIER = "A";


    private static String generateContentJavaAttributeDepth(int klassId,
                                                        boolean isLastKlass) {
        String classToConstruct = String.format("%s%d", SCALING_Attribute_IDENTIFIER, klassId);
        String nextClass = String.format("%s%d", SCALING_Attribute_IDENTIFIER, klassId + 1);

        String noArgConstructorString = String.format("%n\tpublic %s() {}", classToConstruct);
        String constructorString = String.format("%n\tpublic %s(%s var){}%n", classToConstruct, nextClass);

        return String.format("public class %s {%s %s}",
                classToConstruct,
                noArgConstructorString,
                isLastKlass ? "" : constructorString);
    }

    private static List<String> scalingObjectAtributes_createJavaClasses(int depth,
                                                                         String directory) {
        List<String> namesOfCreatedJavaClasses = new ArrayList<>();
        for  (int i = 0; i <= depth; i++) {
            String className = String.format("%s%d", SCALING_Attribute_IDENTIFIER, i);
            namesOfCreatedJavaClasses.add(className);
            createSingleFile(className,
                    "java",
                    generateContentJavaAttributeDepth(i, i == depth),
                    Path.of(directory));
        }
        return namesOfCreatedJavaClasses;
    }

    public static void setUpAttributeScalingTest(int depth) throws IOException, InterruptedException {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= depth; i++) {
            String directory = String.format("%s/factor%d/", DIRECTORY_OBJECT_ATTRIBUTE_SCALING_TEST, i);
            listOfClassNames.add(createBaseMainClass(Paths.get(directory)));
            listOfClassNames.addAll(scalingObjectAtributes_createJavaClasses(i, directory));
            compileClasses(listOfClassNames, directory);
        }
    }


    // ***************************************************************************************************************
    //                                         Constructor Scaling Test
    // ***************************************************************************************************************
    private static final String SCALING_CONSTRUCTORS_CLASS_IDENTIFIER = "A0";

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

    private static String scalingConstructors_createJavaClass(int numberOfConstructors,
                                                              String directory) {
        String filename = String.format("%s", SCALING_CONSTRUCTORS_CLASS_IDENTIFIER);
        createSingleFile(filename,
                "java",
                scalingConstructors_generateJavaClassContent(numberOfConstructors),
                Path.of(directory));
        return filename;
    }

    private static String createBaseMainClass(Path directory) {
        String fileContent =
                """
                import tools.aqua.concolic.Verifier;
                
                public class Main {
                    public static void main(String[] args) {
                        Object o = Verifier.nondetObject(A0.class, null);
                    }
                }
                """;

        createSingleFile("Main", "java", fileContent, directory);
        return "Main";
    }

    public static void setUpConstructorScalingTest(int numberOfConstructors) throws IOException, InterruptedException {
        List<String> listOfClassNames = new ArrayList<>();

        for (int i = 1; i <= numberOfConstructors; i++) {
            String directory = String.format("%s/factor%d/", DIRECTORY_CONSTRUCTOR_SCALING_TEST, i);
            listOfClassNames.add(createBaseMainClass(Paths.get(directory)));
            listOfClassNames.add(scalingConstructors_createJavaClass(i, directory));
            compileClasses(listOfClassNames, directory);
        }
    }



    // ***************************************************************************************************************
    //                                            Extends WIDTH Test
    // ***************************************************************************************************************

    private static final String SCALING_EXTENDS_WIDTH_IDENTIFIER = "A";

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

    private static String scalingExtendsWidth_createSingleJavaClass(int klassId,
                                                                    String directory) {
        String className = String.format("%s%d", SCALING_EXTENDS_WIDTH_IDENTIFIER, klassId);
        createSingleFile(className,
                "java",
                scalingExtendsWidth_generateContentOfSingleJavaClass(klassId, klassId == 0),
                Path.of(directory));
        return className;
    }

    private static List<String> scalingExtendsWidth_createAllJavaClass(int width,
                                                                 String directory) {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= width; i++) {
            listOfClassNames.add(scalingExtendsWidth_createSingleJavaClass(i, directory));
        }
        return listOfClassNames;
    }


    public static void setUpExtendsWidthTest(int width) throws IOException, InterruptedException {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= width; i++) {
            String directory = String.format("%s/factor%d/", DIRECTORY_EXTENDS_WIDTH_SCALING_TEST, i);
            listOfClassNames.add(createBaseMainClass(Paths.get(directory)));
            listOfClassNames.addAll(scalingExtendsWidth_createAllJavaClass(i, directory));
            compileClasses(listOfClassNames, directory);
        }
    }

    // ***************************************************************************************************************
    //                                            Extends Depth Test
    // ***************************************************************************************************************

    private static final String SCALING_EXTENDS_DEPTH_IDENTIFIER = "A";

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

    private static String scalingExtendsDepth_createSingleJavaClass(int klassId,
                                                                    String directory) {
        String className = String.format("%s%d", SCALING_EXTENDS_DEPTH_IDENTIFIER, klassId);
        createSingleFile(className,
                "java",
                scalingExtendsDepth_generateContentOfSingleJavaClass(klassId, klassId == 0),
                Path.of(directory));
        return className;
    }

    private static List<String> scalingExtendsDepth_createAllJavaClasses(int depth,
                                                                         String directory) {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= depth; i++) {
            listOfClassNames.add(scalingExtendsDepth_createSingleJavaClass(i, directory));
        }
        return listOfClassNames;
    }

    public static void setUpExtendsDepthTest(int depth) throws IOException, InterruptedException {
        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 0; i <= depth; i++) {
            String directory = String.format("%s/factor%d/", DIRECTORY_EXTENDS_DEPTH_SCALING_TEST, i);
            listOfClassNames.add(createBaseMainClass(Paths.get(directory)));
            listOfClassNames.addAll(scalingExtendsDepth_createAllJavaClasses(i, directory));
            compileClasses(listOfClassNames, directory);
        }
    }

    // ***************************************************************************************************************
    //                                           Scaling nonDetObject
    // **************************************************************************************************************

    private static final String SCALING_NON_DET_OBJECT_IDENTIFIER = "A";

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

    private static String nonDetObject_createMain(int numberOfNonDetObjectCalls,
                                                  Path directory) {
        String fileContent = String.format(
                """
                import tools.aqua.concolic.Verifier;
                
                public class Main {
                    public static void main(String[] args) {
                        %s
                    }
                }
                """, createNonDetObjectCalls(numberOfNonDetObjectCalls));

        createSingleFile("Main", "java", fileContent, directory);
        return "Main";
    }

    private static String createNonDetObjectCalls(int numberOfNonDetObjectCalls) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < numberOfNonDetObjectCalls; i++) {
            stringBuilder.append(String.format("Object o%d = Verifier.nondetObject(%s.class, null);%n\t\t",
                    i, SCALING_NON_DET_OBJECT_IDENTIFIER));
        }
        return stringBuilder.toString();
    }


    private static String nonDetObject_generateJavaClassContent() {
        return String.format("public class %s {}",  SCALING_NON_DET_OBJECT_IDENTIFIER);
    }

    private static String nonDetObject_createJavaClass(String directory) {
        String filename = String.format("%s", SCALING_NON_DET_OBJECT_IDENTIFIER);
        createSingleFile(filename,
                "java",
                nonDetObject_generateJavaClassContent(),
                Path.of(directory));
        return filename;
    }


    public static void setUpNonDetObjectTest(int numberNonDetObjectsCalls) throws IOException, InterruptedException {

        List<String> listOfClassNames = new ArrayList<>();
        for (int i = 1; i <= numberNonDetObjectsCalls; i++) {
            String directory = String.format("%s/factor%d/", DIRECTORY_NON_DET_OBJECT_SCALING_TEST, i);
            listOfClassNames.add(nonDetObject_createMain(i, Paths.get(directory)));
            listOfClassNames.add(nonDetObject_createJavaClass(directory));
            compileClasses(listOfClassNames, directory);
        }
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
        setUpNonDetObjectTest(3);
        setUpExtendsWidthTest(3);
        setUpExtendsDepthTest(3);
        setUpAttributeScalingTest(3);
    }
}
