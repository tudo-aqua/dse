package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.*;
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


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
            "set-up-time[ms]",
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
        System.out.println("Wrote to CSV file: " + csvFile);
    }

    private static final String CSV_FILE_PREFIX = "target/test-output/";

    private static final String NAME_OF_THE_EXAMPLES_TEST = "examplesTest";
    private static final String NAME_OF_THE_EXAMPLES_TEST_BASELINE = "examplesTestBaseline";
    private static final Path CSV_FILE_EXAMPLES = Path.of(CSV_FILE_PREFIX + "examplesMeasurement.csv");
    private static final Path CSV_FILE_EXAMPLES_BASELINE = Path.of(CSV_FILE_PREFIX + "examplesBaselineMeasurement.csv");
    private static final int EXAMPLES_TEST_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_CONSTRUCTOR_SCALING_TEST = "constructorScalingTest";
    private static final String NAME_OF_THE_CONSTRUCTOR_SCALING_TEST_BASELINE = "constructorScalingTestBaseline";
    private static final Path CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS = Path.of(CSV_FILE_PREFIX + "constructorScalingMeasurement.csv");
    private static final Path CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS_BASELINE = Path.of(CSV_FILE_PREFIX + "constructorScalingBaselineMeasurement.csv");
    private static final int CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS = 30;
    private static final int CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS = 2;

    private static final String NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST = "nondetObjectScalingTest";
    private static final String NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST_BASELINE = "nondetObjectScalingTestBaseline";
    private static final Path CSV_FILE_NON_DET_OBJECT_SCALING = Path.of(CSV_FILE_PREFIX + "nondetObjectScalingMeasurement.csv");
    private static final Path CSV_FILE_NON_DET_OBJECT_SCALING_BASELINE = Path.of(CSV_FILE_PREFIX + "nondetObjectScalingBaselineMeasurement.csv");
    private static final int NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS = 12;
    private static final int NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS = 1;


    @BeforeAll
    static void setUpTestFiles() throws IOException, InterruptedException {
        FilePreparator.setUpConstructorScalingTest(CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS);
        FilePreparator.setUpNonDetObjectTest(NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS);
        FilePreparator.setUpExampleTests();
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

        String setUpTime;
        if (!baseLineEvaluation) {
            setUpTime = TestUtils.getSetUpTime(output);
        }
        else {
            setUpTime = "0";
        }
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
                setUpTime,
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

    @Test
    @Order(1)
    public void JVMStartUpExample() {
        System.out.println("Real Warm-up: Executing DSE once to load classes and trigger JIT...");
        try {
            DSE dse = TestUtils.getDseInstance("Main", FilePreparator.DIRECTORY_EXAMPLE_TEST + "/example01");
            dse.executeAnalysis();
        } catch (Exception e) {
            System.err.println("Warm-up hint: " + e.getMessage());
        }

        System.out.println("Warm-up complete.");
    }


    static Stream<Arguments> testResourceProvider0() {
        return IntStream.range(1, EXAMPLES_TEST_NUMBER_OF_REPETITIONS + 1)
                .boxed()
                .flatMap(j ->
                        IntStream.range(1, 37)
                                .mapToObj(i -> Arguments.of(
                                        String.format("example%02d", i),
                                        String.format("RunGroup%02d", j),
                                        String.format("/example%02d", i)
                                ))
                );
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider0")
    @Order(2)
    public void exampleTest(String exampleName,
                            String currentRunGroup,
                            String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_EXAMPLES_TEST,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_EXAMPLE_TEST+subdirectory,
                CSV_FILE_EXAMPLES,
                false);
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider0")
    @Order(3)
    public void exampleTestBaseline(String exampleName,
                            String currentRunGroup,
                            String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_EXAMPLES_TEST_BASELINE,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_EXAMPLE_TEST+subdirectory,
                CSV_FILE_EXAMPLES_BASELINE,
                true);
    }


    static Stream<Arguments> testResourceProvider1() {
        return IntStream.range(1, CONSTRUCTOR_SCALING_NUMBER_OF_REPETITIONS + 1)
                .boxed()
                .flatMap(j ->
                        IntStream.range(1, CONSTRUCTOR_SCALING_MAX_NUMBER_OF_CONSTRUCTORS + 1)
                                .mapToObj(i -> Arguments.of(
                                        String.format("RunGroup%03d", j),              // j = Aktuelle Wiederholung (Run)
                                        String.format("numberConstructors%03d", i),
                                        String.format("/factor%03d", i)                // i = Aktueller Faktor (Subdirectory)
                                ))
                );
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider1")
    @Order(4)
    public void constructorScalingTest(String currentRunGroup,
                                       String exampleName,
                                       String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_CONSTRUCTOR_SCALING_TEST,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_CONSTRUCTOR_SCALING_TEST+subdirectory,
                CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS,
                false);
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider1")
    @Order(5)
    public void constructorScalingBaselineTest(String currentRunGroup,
                                               String exampleName,
                                               String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_CONSTRUCTOR_SCALING_TEST_BASELINE,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_CONSTRUCTOR_SCALING_TEST+subdirectory,
                CSV_FILE_SCALING_NUMBER_OF_CONSTRUCTORS_BASELINE,
                true);
    }

    static Stream<Arguments> testResourceProvider4() {
        return IntStream.range(1, NON_DET_OBJECT_SCALING_NUMBER_OF_REPETITIONS + 1)
                .boxed()
                .flatMap(j ->
                        IntStream.range(1, NON_DET_OBJECT_SCALING_MAX_NON_DET_OBJECT_CALLS + 1)
                                .mapToObj(i -> Arguments.of(
                                        String.format("RunGroup%03d", j),
                                        String.format("calls%03d", i),
                                        String.format("/factor%03d", i)
                                ))
                );
    }
    @ParameterizedTest
    @MethodSource("testResourceProvider4")
    @Order(6)
    public void nonDetObjectScalingScalingTest(String currentRunGroup,
                                               String exampleName,
                                               String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_NON_DET_OBJECT_SCALING_TEST+subdirectory,
                CSV_FILE_NON_DET_OBJECT_SCALING,
                false);
    }

    @ParameterizedTest
    @MethodSource("testResourceProvider4")
    @Order(7)
    public void nonDetObjectScalingScalingBaselineTest(String currentRunGroup,
                                                       String exampleName,
                                                       String subdirectory
    ) throws IOException {

        performMetricCalculation(
                NAME_OF_THE_NON_DET_OBJECT_SCALING_TEST_BASELINE,
                exampleName,
                currentRunGroup,
                FilePreparator.DIRECTORY_NON_DET_OBJECT_SCALING_TEST+subdirectory,
                CSV_FILE_NON_DET_OBJECT_SCALING_BASELINE,
                true);
    }
}
