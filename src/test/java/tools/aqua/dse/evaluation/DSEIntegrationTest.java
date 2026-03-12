package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.*;
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class DSEIntegrationTest {
    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private boolean debug = true;
    private boolean compile = true;

    @BeforeEach
    void setUpStreams() {
        //get console output
        this.originalOut = System.out;
        PrintStream originalErr = System.err;

        //combination PrintStream: write in both – capturedOutput and console
        if (this.debug) {
            PrintStream teeStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    capturedOutput.write(b); // speichern
                    originalOut.write(b);    // Konsole
                }
            }, true);
            System.setOut(teeStream);
        }

        // Redirects System.out directly to the ByteArrayOutputStream.
        // No more output is output to the console.
        else {
            System.setOut(new PrintStream(capturedOutput));
            System.setErr(new PrintStream(capturedErr));
        }
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    public void printExample(String exampleName, String directoryOfTheClassesToCompile) {
        // relative path to the example
        Path filePath = Paths.get(directoryOfTheClassesToCompile, exampleName + ".java");

        System.out.println("Printing example file: " + filePath.toAbsolutePath());

        // Use `bat` to print the file contents with syntax highlighting
        ProcessBuilder processBuilder = new ProcessBuilder(
                "bat",
                "--color=always",
                filePath.toString()
        );

        // Set the working directory to the project root (same as in compileClass)
        processBuilder.directory(Paths.get("").toAbsolutePath().toFile());

        // Merge stderr into stdout
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();

            // Read and print the process output
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            // Wait for process termination
            int exitCode = process.waitFor();
            System.out.println("Print finished with exit code: " + exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Printing failed with exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error while printing example", e);
        }
    }


    private static DSE getExecution(String exampleName,
                                    String pathToClassHierachy, 
                                    String pathToExamples) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", String.format("-cp %s/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true %s", pathToExamples, exampleName));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("static.info", pathToClassHierachy);

        Config config = Config.fromProperties(props);

        return new DSE(config);
    }


    private String filterOutPutStream() {
        if (this.debug) {
            return String.format("Console Log:%n %s%n Error Log: %n%s", capturedOutput, capturedErr);
        }

        return Arrays.stream(this.capturedOutput.toString().split("\\R"))
                .filter(line -> !line.startsWith("Warning:"))
                .filter(line -> !line.startsWith("Random seed:"))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private List<String> getDecisionTreeLineByLine(String wholeLogs) {
        return Arrays.stream(wholeLogs.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("+"))
                .collect(Collectors.toList());
    }

    private Map<String, Long> analyseDecisionTree(List<String> decisionTreeLines) {
        Map<String, Long> counts = decisionTreeLines.stream()
//                .filter(s -> s.startsWith("+ OK")
//                        || s.startsWith("+ ERROR")
//                        || s.startsWith("+ UNSAT")
//                        || s.matches("^\\+ \\d+.*"))
                .collect(Collectors.groupingBy(
                        s -> {
                            if (s.startsWith("+ OK")) return "OK";
                            if (s.startsWith("+ ERROR")) return "ERROR";
                            if (s.startsWith("+ UNSAT")) return "UNSAT";
                            if (s.startsWith("+ SKIPPED")) return "SKIPPED";
                            if (s.contains("__object_constructor_")) return "#EDGES_OBJECT_CONSTRUCTOR_VARIATION";
                            if (s.matches("^\\+ \\d+.*")) return "#EDGES_NORMAL_VARIATION";
                            return "UNKNOWN";
                        },
                        Collectors.counting()
                ));

        // Sicherstellen, dass alle benötigten Keys mindestens 0 enthalten
        for (String key : List.of(
                "OK","ERROR","UNSAT","SKIPPED",
                "#EDGES_OBJECT_CONSTRUCTOR_VARIATION","#EDGES_NORMAL_VARIATION")) {
            counts.putIfAbsent(key, 0L);
        }

        counts.put("#EDGES_NORMAL_VARIATION", counts.get("#EDGES_NORMAL_VARIATION")-counts.get("SKIPPED")*2);

        counts.put("#PATHS", counts.get("OK")+counts.get("ERROR")+counts.get("UNSAT"));
        counts.put("#EXECUTED_PATHS", counts.get("OK")+counts.get("ERROR"));
        counts.put("#EDGES",  counts.get("#EDGES_OBJECT_CONSTRUCTOR_VARIATION")+counts.get("#EDGES_NORMAL_VARIATION"));
        return counts;
    }

    private void printDuration(Duration duration) {
        long minutes = duration.getSeconds()/60;
        long seconds = duration.getSeconds()%60;
        long nanoSeconds = duration.getNano();


        System.out.println("Duration of the DSE execution");
        System.out.println("minutes: " + minutes);
        System.out.println("seconds: " + seconds);
        System.out.println("nanoSeconds: " + nanoSeconds);
//        System.out.printf("%d : %d : %d", minutes,  seconds, nanoSeconds);
    }

    private void compileBasicClasses(String exampleName) throws IOException, InterruptedException {
        if (this.compile) {
            List<String> filesToCompile = List.of(exampleName, "AT", "A", "B", "BT","C","Sub", "Sub1", "Sub2", "Greeter");
            FilePreparator.compileClasses(filesToCompile, "src/test/resources/examples/");
        }
    }

    @Test
    public void paperExample() throws IOException, InterruptedException {
        //define example
        String exampleName = "Main";

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C"), "src/test/resources/example/"); //example directory
        FilePreparator.compileClass("D", "src/test/resources/example/test/");

        //execute example
        printExample(exampleName, "src/test/resources/example/");
//        DSE dse = TestUtils.getDseInstance(exampleName,
//                "src/test/resources/hierarchy/thesis_class_hierarchy.txt",
//                "src/test/resources/example/");
//
//        Instant start = Instant.now();
//        dse.executeAnalysis();
//        Instant end = Instant.now();
//        Duration duration = Duration.between(start, end);
//
//        //stop redirection of console log
//        System.setOut(originalOut);
//
//        //printing results
//        String output = filterOutPutStream();
//        //System.out.println(output);
//
//        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//        //                                                CHECKS
//        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//        assertThat(output)
//                .doesNotContain("DIVERGED")
//                .doesNotContain("BUGGY");
//
//        List<String> decisionTree = getDecisionTreeLineByLine(output);
//
//
//        System.out.println(analyseDecisionTree(decisionTree));
//        printDuration(duration);
    }


    @Test
    public void thesis_example()  throws IOException, InterruptedException {
        //define example
        String exampleName = "Thesis";

        //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/");
        DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/thesis_class_hierarchy.txt",
                "src/test/resources/examples/");


        Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test //ok
    public void Example01_basic() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example1";

        //compile example
        compileBasicClasses(exampleName);


        //execute example
        printExample(exampleName, "src/test/resources/examples/");
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");


        Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");


        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test //todo: parsing problem of the trace -> out of memory
    public void Example02_casting() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example2";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
        DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }



    @Test //ok (Example modified -> Delete second null check)
    public void Example03_isNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example3";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        System.out.println("output: ");
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test //ok
    public void Example04_isNotNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example4";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test //todo: adapted example diverged -> object1 und object 2 sind verschieden, da das gleiche Object angelegt wird
    public void Example05_towIndependentObjects_checkIdentical() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example5";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test //todo: noch nicht gelöst
    public void Example06_towIndependentObjects_checkNotIdentical() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example6";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test //ok
    public void Example07_OneObject_checkIdentical() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example7";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);

    }

    @Test
    public void Example08_OneObject_checkNotIdentical() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example8";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    public void Example09_instanceOfA() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example9";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example10_notInstanceOfA() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example10";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example11_instanceOfB() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example11";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");


        List<String> decisionTree = getDecisionTreeLineByLine(output);

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example12_notInstanceOfB() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example12";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    public void Example13_aX_isNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example13";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example14_aX_isNotNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example14";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example15_aX_smallerNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example15";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example16_aX_smalerEqualNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example16";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example17_aX_greaterNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example17";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example18_aX_greaterEqualNull() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example18";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example19_a1X_equals_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example19";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example20_a1X_notEquals_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example20";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example21_a1X_smaller_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example21";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example22_a1X_smallerEquals_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example22";

        //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example23_a1X_greater_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example23";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example24_a1X_greaterEquals_a2X() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example24";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example25_a1X_equals_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example25";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example26_a1X_notEquals_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example26";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example27_a1X_smaller_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example27";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example28_a1X_smallerEquals_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example28";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example29_a1X_greater_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example29";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example30_a1X_greaterEquals_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example30";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example31_komplex_condition() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example31";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example32_createIntegerFromObject() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example32";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    public void Example33_instanceOfInterface() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example33";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);

    }

    @Test
    public void Example34_notInstanceOfInterface() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example34";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void Example35_a1Y_equals_a2Y() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example35";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    public void Example36_foo_checkcast() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example36";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    public void Example37_bar() throws IOException, InterruptedException {
        //define example
        String exampleName = "Example37";

                //compile example
        compileBasicClasses(exampleName);

        //execute example
        printExample(exampleName, "src/test/resources/examples/"); 
                DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/hierarchy/standard_class_hierarchy.txt",
                "src/test/resources/examples/");
                Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = getDecisionTreeLineByLine(output);
        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }
}



