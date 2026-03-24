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
        FilePreparator.compileClass("D", "src/test/resources/example/");
        FilePreparator.compileClass("K", "src/test/resources/example/");
        FilePreparator.compileClass("L", "src/test/resources/example/");
        FilePreparator.compileClass("Main", "src/test/resources/example/");

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance(exampleName,
                "src/test/resources/example/");

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example01() throws IOException, InterruptedException {
        //define example
        String exampleName = "example01";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example01BaseLine() throws IOException, InterruptedException {
        //define example
        String exampleName = "example01";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseBaseLineInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example02() throws IOException, InterruptedException {
        //define example
        String exampleName = "example02";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example03() throws IOException, InterruptedException {
        //define example
        String exampleName = "example03";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example04() throws IOException, InterruptedException {
        //define example
        String exampleName = "example04";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example05() throws IOException, InterruptedException {
        //define example
        String exampleName = "example05";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example06() throws IOException, InterruptedException {
        //define example
        String exampleName = "example06";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example07() throws IOException, InterruptedException {
        //define example
        String exampleName = "example07";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example08() throws IOException, InterruptedException {
        //define example
        String exampleName = "example08";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example09() throws IOException, InterruptedException {
        //define example
        String exampleName = "example09";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example10() throws IOException, InterruptedException {
        //define example
        String exampleName = "example10";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example11() throws IOException, InterruptedException {
        //define example
        String exampleName = "example11";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example12() throws IOException, InterruptedException {
        //define example
        String exampleName = "example12";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example13() throws IOException, InterruptedException {
        //define example
        String exampleName = "example13";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example14() throws IOException, InterruptedException {
        //define example
        String exampleName = "example14";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example15() throws IOException, InterruptedException {
        //define example
        String exampleName = "example15";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example16() throws IOException, InterruptedException {
        //define example
        String exampleName = "example16";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example17() throws IOException, InterruptedException {
        //define example
        String exampleName = "example17";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example18() throws IOException, InterruptedException {
        //define example
        String exampleName = "example18";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example19() throws IOException, InterruptedException {
        //define example
        String exampleName = "example19";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example20() throws IOException, InterruptedException {
        //define example
        String exampleName = "example20";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example21() throws IOException, InterruptedException {
        //define example
        String exampleName = "example21";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example22() throws IOException, InterruptedException {
        //define example
        String exampleName = "example22";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example23() throws IOException, InterruptedException {
        //define example
        String exampleName = "example23";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example24() throws IOException, InterruptedException {
        //define example
        String exampleName = "example24";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example25() throws IOException, InterruptedException {
        //define example
        String exampleName = "example25";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example26() throws IOException, InterruptedException {
        //define example
        String exampleName = "example26";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example27() throws IOException, InterruptedException {
        //define example
        String exampleName = "example27";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example28() throws IOException, InterruptedException {
        //define example
        String exampleName = "example28";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example29() throws IOException, InterruptedException {
        //define example
        String exampleName = "example29";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example30() throws IOException, InterruptedException {
        //define example
        String exampleName = "example30";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example31() throws IOException, InterruptedException {
        //define example
        String exampleName = "example31";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example33() throws IOException, InterruptedException {
        //define example
        String exampleName = "example33";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example34() throws IOException, InterruptedException {
        //define example
        String exampleName = "example34";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example35() throws IOException, InterruptedException {
        //define example
        String exampleName = "example35";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example36() throws IOException, InterruptedException {
        //define example
        String exampleName = "example36";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void example37() throws IOException, InterruptedException {
        //define example
        String exampleName = "example37";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "Greeter", "Sub", "Sub1", "Sub2", "Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp01() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects01";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp02() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects02";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp03() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects03";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp04() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects04";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp05() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects05";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp06() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects06";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp07() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects07";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp08() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects08";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp09() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects09";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp10() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects10";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp11() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects11";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp12() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects12";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }
    @Test
    public void svComp13() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects13";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    public void svComp14() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects14";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2", "Main"),
                directoryOfTheExample);

        //execute example
        //printExample(exampleName, "src/test/resources/example/");
        DSE dse = TestUtils.getDseInstance("Main",
                directoryOfTheExample);

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

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }
}



