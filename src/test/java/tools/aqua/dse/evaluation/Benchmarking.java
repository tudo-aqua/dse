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


public class Benchmarking {

    private static class ExampleMeta{
        private String name;
        private String description;

        public ExampleMeta(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;


    private static final Path CSV_FILE = Path.of("target/test-output/test_runs.csv");
    private static final Path RESOURCE_DIRECTORY = Path.of("src/test/resources");

    private static final List<String> CSV_COLUMNS = List.of(
            "name",
            "description",
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

//    private static final boolean CHECK_CORRECTNESS = true;
//    private static final boolean CALCULATE_METRICS = true;

    private static final int NUMBER_OF_EXECUTIONS_PER_EXAMPLE = 1;


    private static final List<ExampleMeta> EXAMPLES =
            Arrays.asList(
                    new ExampleMeta("Example1", "basic"),
                    new ExampleMeta("Example2", "casting")
//                    new ExampleMeta("Example3", ""),
//                    new ExampleMeta("Example4", ""),
//                    new ExampleMeta("Example5", ""),
//                    new ExampleMeta("Example6", ""),
//                    new ExampleMeta("Example7", ""),
//                    new ExampleMeta("Example8", ""),
//                    new ExampleMeta("Example9", ""),
//                    new ExampleMeta("Example10", ""),
//                    new ExampleMeta("Example11", ""),
//                    new ExampleMeta("Example12", ""),
//                    new ExampleMeta("Example13", ""),
//                    new ExampleMeta("Example14", ""),
//                    new ExampleMeta("Example15", ""),
//                    new ExampleMeta("Example16", ""),
//                    new ExampleMeta("Example17", ""),
//                    new ExampleMeta("Example18", ""),
//                    new ExampleMeta("Example19", ""),
//                    new ExampleMeta("Example20", ""),
//                    new ExampleMeta("Example21", ""),
//                    new ExampleMeta("Example22", ""),
//                    new ExampleMeta("Example23", ""),
//                    new ExampleMeta("Example24", ""),
//                    new ExampleMeta("Example25", ""),
//                    new ExampleMeta("Example26", ""),
//                    new ExampleMeta("Example27", ""),
//                    new ExampleMeta("Example28", ""),
//                    new ExampleMeta("Example29", ""),
//                    new ExampleMeta("Example30", ""),
//                    new ExampleMeta("Example31", ""),
//                    new ExampleMeta("Example32", ""),
//                    new ExampleMeta("Example33", ""),
////                    new ExampleMeta("Example34", ""),
//                    new ExampleMeta("Example35", ""),
//                    new ExampleMeta("Example36", ""),
//                    new ExampleMeta("Example37", ""),
////                    new ExampleMeta("Example38", ""),
////                    new ExampleMeta("Example39", ""),
//                    new ExampleMeta("Example40", ""),
////                    new ExampleMeta("Example41", ""),
//                    new ExampleMeta("Example42", "")
            );

    private static final List<ExampleMeta> SCALING_EXAMPLES =
            Arrays.asList(
                    new ExampleMeta("ScalingNonDetObjects1", "Creates 1 Objects via nonDetObject()"),
                    new ExampleMeta("ScalingNonDetObjects2", "Creates 2 Objects via nonDetObject()"),
                    new ExampleMeta("ScalingNonDetObjects3", "Creates 3 Objects via nonDetObject()")
//                    new ExampleMeta("ScalingNonDetObjects4", "Creates 4 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects5", "Creates 5 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects6", "Creates 6 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects7", "Creates 7 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects8", "Creates 8 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects9", "Creates 9 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects10", "Creates 10 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects11", "Creates 11 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects12", "Creates 12 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects13", "Creates 13 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects14", "Creates 14 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects15", "Creates 15 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects16", "Creates 16 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects17", "Creates 17 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects18", "Creates 18 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects19", "Creates 19 Objects via nonDetObject()"),
//                    new ExampleMeta("ScalingNonDetObjects20", "Creates 20 Objects via nonDetObject()")
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


    @BeforeAll
    static void compileAllClasses() {
        EXAMPLES.forEach(exampleMeta -> {
            try {
                TestUtils.compileClass(exampleMeta.name);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Compilation of class "+exampleMeta.name+" failed", e);
            }
        });

        SCALING_EXAMPLES.forEach(exampleMeta -> {
            try {
                TestUtils.compileClass(exampleMeta.name);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Compilation of class "+exampleMeta.name+" failed", e);
            }
        });
    }

//    /**
//     * Writes a single test run to the CSV file.
//     * Creates the file and header if it does not exist.
//     *
//     * @param name        the name of the ExampleMeta
//     * @param description the description of the ExampleMeta
//     * @param run         the run index
//     * @throws IOException if writing to file fails
//     */
//    private void writeToCsv(String name, String description, int run) throws IOException {
//        String csvLine = String.format("%s,%s,%d%n", name, description, run);
//
//        // Append the line to the CSV file
//        Files.writeString(CSV_FILE, csvLine, StandardOpenOption.APPEND);
//    }

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

    private String filterOutPutStream() {
        return Arrays.stream(this.capturedOutput.toString().split("\\R"))
                .filter(line -> !line.startsWith("Warning:"))
                .filter(line -> !line.startsWith("Random seed:"))
                .collect(Collectors.joining(System.lineSeparator()));
    }


    static Stream<Arguments> testResourceProvider() {
        return EXAMPLES.stream()
                .flatMap(meta -> IntStream.range(0, NUMBER_OF_EXECUTIONS_PER_EXAMPLE) // 0,1,2, ... as run-index
                        .mapToObj(run -> {
                            String pathToClassHierachy = "src/test/resources/standard_class_hierarchy.txt";
                            return Arguments.of(meta.name, meta.description, run, pathToClassHierachy);
                        })
                );
    }

    @ParameterizedTest
    @MethodSource("testResourceProviderScalingConstructor")
    public void executionOfBasicTestAndNonDetObjectScaling(String exampleName,
                                                           String exampleDescription,
                                                           int currentRun,
                                                           String pathToKlassHierarchy
    ) throws IOException {

        performMetricCalculation(exampleName, exampleDescription, currentRun, pathToKlassHierarchy);

    }

    private void performMetricCalculation(String exampleName, String exampleDescription, int currentRun, String pathToKlassHierarchy) throws IOException {
        TestUtils.printExample(exampleName);

        System.out.println("exampleName: "+ exampleName);
        System.out.println("exampleDescription: "+ exampleDescription);
        System.out.println("currentRun: "+ currentRun);

        DSE dse = TestUtils.getDseInstance(exampleName, pathToKlassHierarchy);

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
                exampleName,
                exampleDescription,
                String.valueOf(currentRun),
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


    static Stream<Arguments> testResourceProviderScalingConstructor() {
        return SCALING_EXAMPLES.stream()
                .flatMap(meta -> IntStream.range(0, NUMBER_OF_EXECUTIONS_PER_EXAMPLE) // 0,1,2, ... as run-index
                        .mapToObj(run -> {
                            String pathToClassHierachy = "src/test/resources/scaling_nonDetObject_hierarchy.txt";
                            return Arguments.of(meta.name, meta.description, run, pathToClassHierachy);
                        })
                );
    }

    @ParameterizedTest
    @MethodSource("testResourceProviderScalingConstructor")
    public void executionOfScalingConstructorExample(String exampleName,
                                           String exampleDescription,
                                           int currentRun,
                                           String pathToKlassHierarchy
    ) throws IOException  {
        TestUtils.printExample(exampleName);
    }



    //todo: Anpassung der writeCSV Methode so, dass sie variable anhand der columns ist
    //todo: Überlegen, wie ich die Informationen am besten darstelle: ChatGpT fragen
    //todo: Laufzeitmessung durchführen
    //todo: Durchschnitt und Standardabweichung (ggf. in pandas berechne)
    //todo: Parameter des Baumes berechnen

    //todo: Skalierbarkeitstestfälle anlegen
    //todo: MethodProvider schreiben
    //todo: tests ausführen

    //todo: Assertions.Feld anpassen
}
