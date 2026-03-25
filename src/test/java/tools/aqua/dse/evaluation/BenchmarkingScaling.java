package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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


    private static final List<String> CSV_COLUMNS = List.of(
            "testName",
            "exampleName",
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

    private static final Set<Path> INITIALIZED_CSV_FILES = ConcurrentHashMap.newKeySet();


    /**
     * Setup method executed once before all parameterized tests.
     * Deletes previous CSV file if it exists and creates a new one with header.
     */
    static void cleanCSVFile(Path csvFile) throws IOException {
        // Ensure parent directories exist
        Files.createDirectories(csvFile.getParent());

        // Delete existing CSV file if present
        Files.deleteIfExists(csvFile);

        // Create a new CSV file with header
        Files.writeString(csvFile, String.join(",", CSV_COLUMNS)+"\n", StandardOpenOption.CREATE);
    }

    /**
     * Writes a single row to the CSV file.
     * The values must be in the same order as defined by the CSV_HEADERS list.
     *
     * @param values list of values corresponding to the column headers
     * @throws IOException if writing to file fails
     */
    private void writeToCsv(List<String> values, Path csvFile) throws IOException {
        if (values.size() != CSV_COLUMNS.size()) {
            throw new IllegalArgumentException("Values count does not match header count");
        }
        String csvLine = String.join(",", values) + "\n";
        Files.writeString(csvFile, csvLine, StandardOpenOption.APPEND);
    }

    private static final int BASIC_EXAMPLE_NUMBER_OF_REPETITIONS = 1;
    private static final String CSV_FILE_PREFIX = "target/test-output/";

    private static final String NAME_OF_THE_CONSTRUCTOR_SCALING_TEST = "constructorScalingTest";
    private static final Path CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS = Path.of(CSV_FILE_PREFIX + "constructorScalingMeasurement.csv");
    private static final Path CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS_BASELINE = Path.of(CSV_FILE_PREFIX + "constructorScalingBaselineMeasurement.csv");
    private static final int CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS = 4;
    private static final int CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_EXTENDS_WIDTH_SCALING_TEST = "extendsWidthScalingTest";
    private static final Path CSV_FILE_EXTENDS_WIDTH_SCALING = Path.of(CSV_FILE_PREFIX + "extendsWidthScalingMeasurement.csv");
    private static final Path CSV_FILE_EXTENDS_WIDTH_SCALING_BASELINE = Path.of(CSV_FILE_PREFIX + "extendsWidthScalingBaselineMeasurement.csv");
    private static final int EXTENDS_WIDTH_SCALING_MAX_WIDTH = 4;
    private static final int EXTENDS_WIDTH_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_EXTENDS_DEPTH_SCALING_TEST = "extendsDepthScalingTest";
    private static final Path CSV_FILE_EXTENDS_DEPTH_SCALING = Path.of(CSV_FILE_PREFIX + "extendsDepthScalingMeasurement.csv");
    private static final Path CSV_FILE_EXTENDS_DEPTH_SCALING_BASELINE = Path.of(CSV_FILE_PREFIX + "extendsDepthScalingBaselineMeasurement.csv");
    private static final int EXTENDS_DEPTH_SCALING_MAX_DEPTH = 4;
    private static final int EXTENDS_DEPTH_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST = "nondetObjectScalingTest";
    private static final Path CSV_FILE_NON_DET_OBJECT_SCALING = Path.of(CSV_FILE_PREFIX + "nondetObjectScalingMeasurement.csv");
    private static final Path CSV_FILE_NON_DET_OBJECT_SCALING_BASELINE = Path.of(CSV_FILE_PREFIX + "nondetObjectScalingBaselineMeasurement.csv");
    private static final int NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS = 4;
    private static final int NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_OBJECT_ATTRIBUTE_SCALING_TEST = "objectAttributeScalingTest";
    private static final Path CSV_FILE_OBJECT_ATTRIBUTE_SCALING = Path.of(CSV_FILE_PREFIX + "objectAttributeScalingMeasurement.csv");
    private static final Path CSV_FILE_OBJECT_ATTRIBUTE_SCALING_BASELINE = Path.of(CSV_FILE_PREFIX + "objectAttributeScalingBaselineMeasurement.csv");
    private static final int OBJECT_ATTRIBUTE_SCALING_MAX_DEPTH = 4;
    private static final int OBJECT_ATTRIBUTE_SCALING_NUMBER_OF_REPETITIONS = 2;


    @BeforeAll
    static void setUpTestFiles() throws IOException, InterruptedException {
        FilePreparator.setUpConstructorScalingTest(CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS);
//        FilePreparator.setUpExtendsWidthTest(EXTENDS_WIDTH_SCALING_MAX_WIDTH);
//        FilePreparator.setUpExtendsDepthTest(EXTENDS_DEPTH_SCALING_MAX_DEPTH);
        FilePreparator.setUpNonDetObjectTest(NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS);
//        FilePreparator.setUpAttributeScalingTest(OBJECT_ATTRIBUTE_SCALING_MAX_DEPTH);
    }

    private void performMetricCalculation(String testName,
                                          String currentExampleName,
                                          String currentRunGroup,
                                          String pathToExamples,
                                          Path csvFile,
                                          boolean baseLineEvaluation) throws IOException {
//        TestUtils.printExample(currentExampleName, pathToExamples);

        if (INITIALIZED_CSV_FILES.add(csvFile)) {
            cleanCSVFile(csvFile);
            System.out.println("CSV File cleaned and initialized: " + csvFile);
        }


        System.out.println("testName: " + testName);
        System.out.println("currentExampleName: "+ currentExampleName);
        System.out.println("currentRunGroup: "+ currentRunGroup);

        long start = System.currentTimeMillis();
        DSE dse;
        if(baseLineEvaluation) {
            dse = TestUtils.getDseBaseLineInstance("Main", pathToExamples);
        }
        else {
            dse = TestUtils.getDseInstance("Main", pathToExamples);
        }
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
        writeToCsv(row, csvFile);
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
            "A",
            "B",
            "C",
            "Greeter",
            "RecursiveIntList",
            "Sub",
            "Sub1",
            "Sub2"
            );

    @Test
    public void JVMStartUpExample() {
        System.out.println("Example to Cold-Start the JVM");
    }

    static Stream<Arguments> testResourceProvider1() {
        return IntStream.range(1, CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS+1)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS + 1)
                                .mapToObj(j -> Arguments.of(
                                        String.format("RunGroup%d", i),             // currentRunGroup
                                        String.format("/factor%d", i)                // subdirectory
                                ))
                );
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider1")
    public void constructorScalingTest(String currentRunGroup,
                                       String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_CONSTRUCTOR_SCALING_TEST,
                "ExampleScalingConstructors",
                currentRunGroup,
                FilePreparator.DIRECTORY_CONSTRUCTOR_SCALING_TEST+subdirectory,
                CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS,
                false);
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider1")
    public void constructorScalingBaselineTest(String currentRunGroup,
                                       String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_CONSTRUCTOR_SCALING_TEST,
                "ExampleScalingConstructors",
                currentRunGroup,
                FilePreparator.DIRECTORY_CONSTRUCTOR_SCALING_TEST+subdirectory,
                CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS_BASELINE,
                true);
    }


    static Stream<Arguments> testResourceProvider2() {
        return IntStream.range(0, EXTENDS_WIDTH_SCALING_MAX_WIDTH)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, EXTENDS_WIDTH_SCALING_NUMBER_OF_REPETITIONS + 1)
                                .mapToObj(j -> Arguments.of(
                                        String.format("RunGroup%d", i),             // currentRunGroup
                                        String.format("/factor%d", i)                // subdirectory
                                ))
                );
    }
    @ParameterizedTest
    @MethodSource("testResourceProvider2")
    public void extendsWidthScalingTest(String currentRunGroup,
                                        String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_EXTENDS_WIDTH_SCALING_TEST,
                "ExampleScalingWidth",
                currentRunGroup,
                FilePreparator.DIRECTORY_EXTENDS_WIDTH_SCALING_TEST+subdirectory,
                CSV_FILE_EXTENDS_WIDTH_SCALING,
                false);
    }

    static Stream<Arguments> testResourceProvider3() {
        return IntStream.range(0, EXTENDS_DEPTH_SCALING_MAX_DEPTH)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, EXTENDS_DEPTH_SCALING_NUMBER_OF_REPETITIONS + 1)
                                .mapToObj(j -> Arguments.of(
                                        String.format("RunGroup%d", i),             // currentRunGroup
                                        String.format("/factor%d", i)                // subdirectory
                                ))
                );
    }
    @ParameterizedTest
    @MethodSource("testResourceProvider3")
    public void extendsDepthScalingTest(String currentRunGroup,
                                        String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_EXTENDS_DEPTH_SCALING_TEST,
                "ExampleScalingDepth",
                currentRunGroup,
                FilePreparator.DIRECTORY_EXTENDS_DEPTH_SCALING_TEST+subdirectory,
                CSV_FILE_EXTENDS_DEPTH_SCALING,
                false);
    }

    static Stream<Arguments> testResourceProvider4() {
        return IntStream.range(1, NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS+1)
                .boxed()
                .flatMap(i ->
                        IntStream.range(1, NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS + 1)
                                .mapToObj(j -> Arguments.of(
                                        String.format("RunGroup%d", i),             // currentRunGroup
                                        String.format("/factor%d", i)                // subdirectory
                                ))
                );
    }
    @ParameterizedTest
    @MethodSource("testResourceProvider4")
    public void nonDetObjectScalingScalingTest(String currentRunGroup,
                                               String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST,
                "ExampleScalingWidth",
                currentRunGroup,
                FilePreparator.DIRECTORY_NON_DET_OBJECT_SCALING_TEST+subdirectory,
                CSV_FILE_NON_DET_OBJECT_SCALING,
                false);
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider4")
    public void nonDetObjectScalingScalingBaselineTest(String currentRunGroup,
                                                       String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST,
                "ExampleScalingWidth",
                currentRunGroup,
                FilePreparator.DIRECTORY_NON_DET_OBJECT_SCALING_TEST+subdirectory,
                CSV_FILE_NON_DET_OBJECT_SCALING_BASELINE,
                true);
    }
}
