package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.*;
import tools.aqua.dse.DSE;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class DSEIntegrationTest {
    private static final Path DECISION_TREE_OUTPUT = Paths.get("decision_trees.txt");

    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private boolean debug = true;
    private boolean compile = true;

    @BeforeAll
    static void resetDecisionTreeOutput() throws IOException {
        Files.deleteIfExists(DECISION_TREE_OUTPUT);
    }

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


    private String filterOutPutStream() {
        if (this.debug) {
            return String.format("Console Log:%n %s%n Error Log: %n%s", capturedOutput, capturedErr);
        }

        return Arrays.stream(this.capturedOutput.toString().split("\\R"))
                .filter(line -> !line.startsWith("Warning:"))
                .filter(line -> !line.startsWith("Random seed:"))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private void appendDecisionTree(String testName, List<String> decisionTree) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("===== ").append(testName).append(" =====").append(System.lineSeparator());
        for (String line : decisionTree) {
            sb.append(line).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        Files.writeString(
                DECISION_TREE_OUTPUT,
                sb.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
        );
    }

    private List<String> getDecisionTreeLineByLine(String wholeLogs) {
        return Arrays.stream(wholeLogs.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("+"))
                .collect(Collectors.toList());
    }

    private Map<String, Long> analyseDecisionTree(List<String> decisionTreeLines) {
        Map<String, Long> counts = decisionTreeLines.stream()
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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example02Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example02";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example03Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example03";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example04Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example04";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example05Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example05";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example06Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example06";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example07Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example07";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example08Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example08";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example09Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example09";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example10Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example10";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example11Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example11";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example12Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example12";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example13Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example13";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example14Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example14";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example15Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example15";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example16Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example16";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example17Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example17";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example18Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example18";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example19Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example19";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example20Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example20";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example21Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example21";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example22Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example22";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("own-tests-baseline")
    public void example23Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example23";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example24Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example24";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example25Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example25";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example26Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example26";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example27Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example27";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example28Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example28";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example29Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example29";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example30Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example30";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example31Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example31";

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

    @Test@Tag("own-test")
    public void example32() throws IOException, InterruptedException {
        //define example
        String exampleName = "example32";

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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example32Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example32";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example33Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example33";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example34Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example34";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example35Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example35";

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
    @Tag("own-tests")
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
        appendDecisionTree(exampleName, decisionTree);

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("own-tests-baseline")
    public void example36Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "example36";

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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isTrue();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    @Test
    @Tag("svComp-baseline")
    public void svComp01Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects01";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isTrue();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp02Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects02";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp03Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects03";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp04Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects04";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isTrue();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp05Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects05";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp06Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects06";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp07Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects07";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isTrue();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp08Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects08";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp09Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects09";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp10Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects10";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();


        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp11Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects11";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp12Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects12";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp13Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects13";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("svComp")
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

        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
        assertThat(TestUtils.noRuntimeException(decisionTree)).isFalse();

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


    @Test
    @Tag("svComp-baseline")
    public void svComp14Baseline() throws IOException, InterruptedException {
        //define example
        String exampleName = "objects14";

        String directoryOfTheExample = String.format("src/test/resources/svComp/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("A", "B", "C", "D", "Sub", "Sub1", "Sub2","Factories", "Main"),
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
    @Tag("own-tests")
    public void example37() throws IOException, InterruptedException {
        //define example
        String exampleName = "example37";

        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);

        // Compile Base Classes
        FilePreparator.compileClasses(List.of("InetAddress", "Inet4Address", "Inet6Address", "InterfaceAddress", "Main"),
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
        appendDecisionTree(exampleName, decisionTree);

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }


//
//    @Test
//    @Tag("own-tests")
//    public void example38() throws IOException, InterruptedException {
//        //define example
//        String exampleName = "example38";
//
//        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);
//
//        // Compile Base Classes
//        FilePreparator.compileClass("Main", directoryOfTheExample);
//
//        //execute example
//        DSE dse = TestUtils.getDseInstance("Main",
//                directoryOfTheExample);
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
//        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);
//
//        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
//        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();
//
//
//        System.out.println(analyseDecisionTree(decisionTree));
//        printDuration(duration);
//    }
//
//
//    @Test
//    @Tag("own-tests")
//    public void example39() throws IOException, InterruptedException {
//        //define example
//        String exampleName = "example39";
//
//        String directoryOfTheExample = String.format("src/test/resources/examples/%s/", exampleName);
//
//        // Compile Base Classes
//        FilePreparator.compileClasses(List.of("Component", "A", "B", "Main"),
//                directoryOfTheExample);
//
//        //execute example
//        //printExample(exampleName, "src/test/resources/example/");
//        DSE dse = TestUtils.getDseInstance("Main",
//                directoryOfTheExample);
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
//        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);
//
//        System.out.println(analyseDecisionTree(decisionTree));
//        printDuration(duration);
//    }
//
//
//    @Test
//    @Tag("own-tests")
//    public void examplejoda() throws IOException, InterruptedException {
//        //define example
//        String exampleName = "joda_money";
//
//        String directoryOfTheExample = String.format("src/test/resources/real_world_programs/%s/", exampleName);
//
//        // Compile Base Classes
////        FilePreparator.compileClass("Main", directoryOfTheExample, directoryOfTheExample + "joda-money-2.0.3.jar");
//        FilePreparator.compileClass("Main", directoryOfTheExample);
//
//        //execute example
//        DSE dse = TestUtils.getDseInstance("Main",
//                directoryOfTheExample);
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
//        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);
//
//        assertThat(TestUtils.validAssert(decisionTree)).isFalse();
//        assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();
//
//
//        System.out.println(analyseDecisionTree(decisionTree));
//        printDuration(duration);
//    }
}



