package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.aqua.dse.DSE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class BenchmarkingScaling {
    // ----------------------------------------------------------------------------------------------------------
    //                                       intercepting the output
    // ----------------------------------------------------------------------------------------------------------
    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;


    @BeforeEach
    void setUpStreams() {
        //get console output
        this.originalOut = System.out;
        PrintStream originalErr = System.err;

        // Redirects System.out directly to the ByteArrayOutputStream.
        // No more output is output to the console.
        System.setOut(new PrintStream(capturedOutput));
        System.setErr(new PrintStream(capturedErr));

    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    private String filterOutPutStream() {
        return Arrays.stream(this.capturedOutput.toString().split("\\R"))
                .filter(line -> !line.startsWith("Warning:"))
                .filter(line -> !line.startsWith("Random seed:"))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    // ----------------------------------------------------------------------------------------------------------
    //                                     Preparation to execute tests
    // ----------------------------------------------------------------------------------------------------------


    private static final Path CSV_FILE = Path.of("target/test-output/test_runs.csv");
    private static final Path RESOURCE_DIRECTORY = Path.of("src/test/resources");

    private static final List<String> CSV_COLUMNS = List.of(
            "testName",
            "exampleName",
            "hierarchyName",
            "run",
            "duration[ms]",
            "#PATHS",
            "#EDGES",
            "#EDGES_OBJECT_CONSTRUCTOR_VARIATION",
            "SKIPPED",
            "ERROR",
            "OK",
            "#EDGES_NORMAL_VARIATION",
            "#EXECUTED_PATHS",
            "UNSAT"
    );

    /**
     * Setup method executed once before all parameterized tests.
     * Deletes previous CSV file if it exists and creates a new one with header.
     */
    @BeforeAll
    static void setupCsvFile() throws IOException {
        // Ensure parent directories exist
        Files.createDirectories(CSV_FILE.getParent());

        // Delete existing CSV file if present
        Files.deleteIfExists(CSV_FILE);

        // Create a new CSV file with header
        Files.writeString(CSV_FILE, String.join(",", CSV_COLUMNS)+"\n", StandardOpenOption.CREATE);
    }

    /**
     * Writes a single row to the CSV file.
     * The values must be in the same order as defined by the CSV_HEADERS list.
     *
     * @param values list of values corresponding to the column headers
     * @throws IOException if writing to file fails
     */
    private void writeToCsv(List<String> values) throws IOException {
        if (values.size() != CSV_COLUMNS.size()) {
            throw new IllegalArgumentException("Values count does not match header count");
        }
        String csvLine = String.join(",", values) + "\n";
        Files.writeString(CSV_FILE, csvLine, StandardOpenOption.APPEND);
    }

    private static final int BASIC_EXAMPLE_NUMBER_OF_REPETITIONS = 2;

    private static final int CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS = 100;
    private static final int CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS = 5;

    private static final int EXTENDS_WIDTH_SCALING_MAX_WIDTH = 100;
    private static final int EXTENDS_WIDTH_SCALING_NUMBER_OF_REPETITIONS = 5;

    private static final int EXTENDS_DEPTH_SCALING_MAX_DEPTH = 100;
    private static final int EXTENDS_DEPTH_SCALING_NUMBER_OF_REPETITIONS = 5;

    private static final int NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS = 6;
    private static final int NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final int OBJECT_ATTRIBUTE_SCALING_MAX_DEPTH = 100;
    private static final int OBJECT_ATTRIBUTE_SCALING_NUMBER_OF_REPETITIONS = 5;


    @BeforeAll
    static void setUpTestFiles() throws IOException, InterruptedException {
        FilePreparator.setUpConstructorScalingTest(CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS);
        FilePreparator.setUpExtendsWidthTest(EXTENDS_WIDTH_SCALING_MAX_WIDTH);
        FilePreparator.setUpExtendsDepthTest(EXTENDS_DEPTH_SCALING_MAX_DEPTH);
        FilePreparator.setUpNonDetObjectTest(NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS);
        FilePreparator.setUpAttributeScalingTest(OBJECT_ATTRIBUTE_SCALING_MAX_DEPTH);
        FilePreparator.compileClasses(BenchmarkingScaling.BASIC_EXAMPLES);
    }

    private void performMetricCalculation(String testName,
                                          String currentExampleName,
                                          String currentHierarchyName,
                                          String currentRunGroup) throws IOException {
        TestUtils.printExample(currentExampleName);

        System.out.println("testName: " + testName);
        System.out.println("currentExampleName: "+ currentExampleName);
        System.out.println("currentHierarchyName: " + currentHierarchyName);
        System.out.println("currentRunGroup: "+ currentRunGroup);

        DSE dse = TestUtils.getDseInstance(currentExampleName, currentHierarchyName);

        long start = System.currentTimeMillis();
        dse.executeAnalysis();
        long end = System.currentTimeMillis();

        //stop redirection of console log
        System.setOut(originalOut);

        //get and transform output
        String output = filterOutPutStream();
        System.out.println("output: "+output);
        List<String> decisionTreeLineByLine = TestUtils.getDecisionTreeLineByLine(output);


        //------------------------------------------------------------------------------------------------------------
        //                                             calculate metrics
        //------------------------------------------------------------------------------------------------------------
        long duration = end - start;
        Map<String, Long> metricsMap = TestUtils.analyseDecisionTree(decisionTreeLineByLine);


        //------------------------------------------------------------------------------------------------------------
        //                                         print metrics in console
        //------------------------------------------------------------------------------------------------------------
        System.out.println("Execution time: "+duration);
        for (Map.Entry<String, Long> entry : metricsMap.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }


        //------------------------------------------------------------------------------------------------------------
        //                                             save metrics to csv
        //------------------------------------------------------------------------------------------------------------
        List<String> row = List.of(
                testName,
                currentExampleName,
                currentHierarchyName,
                String.valueOf(currentRunGroup),
                String.valueOf(duration),
                String.valueOf(metricsMap.get("#PATHS")),
                String.valueOf(metricsMap.get("#EDGES")),
                String.valueOf(metricsMap.get("#EDGES_OBJECT_CONSTRUCTOR_VARIATION")),
                String.valueOf(metricsMap.get("SKIPPED")),
                String.valueOf(metricsMap.get("ERROR")),
                String.valueOf(metricsMap.get("OK")),
                String.valueOf(metricsMap.get("#EDGES_NORMAL_VARIATION")),
                String.valueOf(metricsMap.get("#EXECUTED_PATHS")),
                String.valueOf(metricsMap.get("UNSAT"))
        );
        writeToCsv(row);
    }


    //------------------------------------------------------------------------------------------------------------
    //                                           Execution of Tests
    //------------------------------------------------------------------------------------------------------------

    public final static List<String> BASIC_EXAMPLES = Arrays.asList(
            "Example1",
            "Example2",
            "Example3",
            "Example4",
            "Example5",
            "Example6",
            "Example7",
            "Example8",
            "Example9",
            "Example10",
            "Example11",
            "Example12",
            "Example13",
            "Example14",
            "Example15",
            "Example16",
            "Example17",
            "Example18",
            "Example19",
            "Example20",
            "Example21",
            "Example22",
            "Example23",
            "Example24",
            "Example25",
            "Example26",
            "Example27",
            "Example28",
            "Example29",
            "Example30",
            "Example31",
            "Example32",
            "Example33",
            "Example35",
            "Example36",
            "Example37",
            "Example40",
            "Example42",
            "A",
            "B",
            "C",
            "Greeter",
            "RecursiveIntList",
            "Sub",
            "Sub1",
            "Sub2"
            );

    static Stream<Arguments> testResourceProvider0() {


        String klassHierarchyName = "standard_class_hierarchy";

        return IntStream.range(0, BASIC_EXAMPLES.size())
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, BASIC_EXAMPLE_NUMBER_OF_REPETITIONS + 1)
                                .mapToObj(j -> Arguments.of(
                                        BASIC_EXAMPLES.get(i),
                                        klassHierarchyName,
                                        String.format("RunGroup%d", i + 1)
                                ))
                );
    }
//    @ParameterizedTest
//    @MethodSource("testResourceProvider0")
//    public void executionOfTheBasicExamplesTest(String currentExampleName,
//                                       String currentPKlassHierarchyName,
//                                       String currentRunGroup
//    ) throws IOException {
//        String testName = "executionOfTheBasicExamplesTest";
//        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
//    }




    static Stream<Arguments> testResourceProvider1() {

        return IntStream.range(0, CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS+1)
                                .mapToObj(j -> Arguments.of(
                                        "ExampleScalingConstructors",
                                        String.format("scaling_constructors_%d_hierarchy", i),
                                        String.format("RunGroup%d", i)
                                ))
                );

    }
    @ParameterizedTest
    @MethodSource("testResourceProvider1")
    public void constructorScalingTest(String currentExampleName,
                                       String currentPKlassHierarchyName,
                                       String currentRunGroup
    ) throws IOException {
        String testName = "constructorScalingTest";
        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
    }

    static Stream<Arguments> testResourceProvider2() {

        return IntStream.range(0, EXTENDS_WIDTH_SCALING_MAX_WIDTH)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, EXTENDS_WIDTH_SCALING_NUMBER_OF_REPETITIONS+1)
                                .mapToObj(j -> Arguments.of(
                                        "ExampleExtendsWidth",
                                        String.format("ExtendsWidth%d", i),
                                        String.format("RunGroup%d", i)
                                ))
                );

    }
    @ParameterizedTest
    @MethodSource("testResourceProvider2")
    public void extendsWidthScalingTest(String currentExampleName,
                                       String currentPKlassHierarchyName,
                                       String currentRunGroup
    ) throws IOException {
        String testName = "extendsWidthScalingTest";
        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
    }


    static Stream<Arguments> testResourceProvider3() {

        return IntStream.range(0, EXTENDS_DEPTH_SCALING_MAX_DEPTH)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, EXTENDS_DEPTH_SCALING_NUMBER_OF_REPETITIONS+1)
                                .mapToObj(j -> Arguments.of(
                                        "ExampleExtendsDepth",
                                        String.format("ExtendsDepth%d", i),
                                        String.format("RunGroup%d", i)
                                ))
                );

    }
    @ParameterizedTest
    @MethodSource("testResourceProvider3")
    public void extendsDepthScalingTest(String currentExampleName,
                                        String currentPKlassHierarchyName,
                                        String currentRunGroup
    ) throws IOException {
        String testName = "extendsDepthScalingTest";
        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
    }


    static Stream<Arguments> testResourceProvider4() {

        return IntStream.range(1, NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS+1)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS+1)
                                .mapToObj(j -> Arguments.of(
                                        String.format("ExampleScalingNonDetObject%d",i),
                                        "ScalingNonDetObject",
                                        String.format("RunGroup%d", i)
                                ))
                );

    }
    @ParameterizedTest
    @MethodSource("testResourceProvider4")
    public void nondetObjectScalingTest(String currentExampleName,
                                        String currentPKlassHierarchyName,
                                        String currentRunGroup
    ) throws IOException {
        String testName = "nondetObjectScalingTest";
        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
    }

    static Stream<Arguments> testResourceProvider5() {

        return IntStream.range(1, OBJECT_ATTRIBUTE_SCALING_MAX_DEPTH+1)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, OBJECT_ATTRIBUTE_SCALING_NUMBER_OF_REPETITIONS+1)
                                .mapToObj(j -> Arguments.of(
                                        "ExampleScalingInnerClasses",
                                        String.format("AttributeDepthHierarchy%d", i),
                                        String.format("RunGroup%d", i)
                                ))
                );

    }
    @ParameterizedTest
    @MethodSource("testResourceProvider5")
    public void objectAttributeScalingTest(String currentExampleName,
                                        String currentPKlassHierarchyName,
                                        String currentRunGroup
    ) throws IOException {
        String testName = "objectAttributeScalingTest";
        performMetricCalculation(testName, currentExampleName, currentPKlassHierarchyName, currentRunGroup);
    }


}
