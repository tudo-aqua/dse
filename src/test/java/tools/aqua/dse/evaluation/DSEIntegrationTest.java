package tools.aqua.dse.evaluation;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
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

//@TestMethodOrder(MethodOrderer.MethodName.class)
public class DSEIntegrationTest {
    private static final Path DECISION_TREE_OUTPUT = Paths.get("decision_trees.txt");

    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private boolean debug = true;
    private boolean compile = true;

    @BeforeClass(alwaysRun = true)
    static void resetDecisionTreeOutput() throws IOException {
        Files.deleteIfExists(DECISION_TREE_OUTPUT);
    }

    // alwaysRun: otherwise TestNG skips these when tests are selected by group (-Dgroups=...)
    @BeforeMethod(alwaysRun = true)
    void setUpStreams() {
        // one instance for all test methods (TestNG): start every test with empty buffers
        capturedOutput.reset();
        capturedErr.reset();
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

                // forward blocks, not single bytes: surefire records a stack trace for every write to the console,
                // which made tests with much output (e.g. large SMT problems) take minutes instead of seconds
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    capturedOutput.write(b, off, len);
                    originalOut.write(b, off, len);
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

    @AfterMethod(alwaysRun = true)
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

    @Test(groups = "tudo")
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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



    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"tudo", "own-tests-baseline"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups={"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups={"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups={"own-tests-baseline", "tudo"})
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

    @Test(groups={"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups={"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests", "tudo"})
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


    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline", "tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-test","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    @Test(groups = {"own-tests-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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

    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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

    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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




    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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



    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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



    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"svComp","tudo"})
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



    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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

    @Test(groups={"svComp-baseline","tudo"})
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



    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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

    @Test(groups = {"svComp-baseline","tudo"})
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


    @Test(groups = {"svComp","tudo"})
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


    @Test(groups = {"svComp-baseline","tudo"})
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

    @Test(groups = {"own-tests","tudo"})
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

    // ***************************************************************************************************************
    //                        Real-world libraries (fat jars from Maven Central, see e2e/libs)
    // ***************************************************************************************************************

    /**
     * Runs DSE on {@code src/test/resources/real_world_programs/<example>/Main}, whose directory contains the fat
     * jar of the library, and checks the reachability of the assertion and of runtime exceptions.
     */
    private void realWorldExample(String example, List<String> classes, boolean assertionReachable,
                                  boolean runtimeExceptionReachable) throws IOException, InterruptedException {
        String directoryOfTheExample = String.format("src/test/resources/real_world_programs/%s/", example);
        FilePreparator.compileClasses(classes, directoryOfTheExample);

        // with broad type bounds, objects of any class (e.g. with String fields) are created; SPouT's symbolic
        // strings are out of scope for these tests, so String fields keep their concrete values
        String executorOptions = example.endsWith("_object") ? "-Dconcolic.symbolic.string.fields=false" : "";
        DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample, executorOptions);
        Instant start = Instant.now();
        dse.executeAnalysis();
        Duration duration = Duration.between(start, Instant.now());

        System.setOut(originalOut);
        String output = filterOutPutStream();
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);
        appendDecisionTree(example, decisionTree);
        assertThat(TestUtils.validAssert(decisionTree)).isEqualTo(!assertionReachable);
        assertThat(TestUtils.noRuntimeException(decisionTree)).isEqualTo(!runtimeExceptionReachable);

        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);
    }

    /** Commons Lang MutableInt: distinct but equal objects; equals agrees with compareTo; NPE in a constructor. */
    @Test(groups = {"real-world", "tudo"})
    public void realWorldCommonsLang3MutableInt() throws IOException, InterruptedException {
        realWorldExample("commons_lang3_mutableint", List.of("Main"), true, true);
    }

    /** Commons Lang ComparableUtils.between / greaterThan over three MutableInts: unreachable; NPE for null. */
    @Test(groups = {"real-world", "tudo"})
    public void realWorldCommonsLang3ComparableUtils() throws IOException, InterruptedException {
        realWorldExample("commons_lang3_comparableutils", List.of("Main"), false, true);
    }

    /** Commons Collections NullComparator.compare: null above non-null iff nullsAreHigh; NPE for null comparator. */
    @Test(groups = {"real-world", "tudo"})
    public void realWorldCommonsCollections4NullComparator() throws IOException, InterruptedException {
        realWorldExample("commons_collections4_nullcomparator", List.of("Main"), true, true);
    }

    /** JDK Objects.equals: asymmetric equals of Point / ColorPoint; Objects.equals is null-safe. */
    @Test(groups = {"real-world", "tudo"})
    public void realWorldJdkObjectsEquals() throws IOException, InterruptedException {
        realWorldExample("jdk_objects_equals", List.of("Point", "ColorPoint", "Main"), true, false);
    }

    /** Commons Collections Predicate.evaluate over its implementations: UniquePredicate; NPE for null children. */
    @Test(groups = {"real-world", "tudo"})
    public void realWorldCommonsCollections4Predicate() throws IOException, InterruptedException {
        realWorldExample("commons_collections4_predicate", List.of("Main"), true, true);
    }

    // Variants with loose type bounds (Object, JDK interfaces): many more candidate constructors

    @Test(groups = {"real-world-object", "tudo"})
    public void realWorldObjectJdkObjectsEquals() throws IOException, InterruptedException {
        realWorldExample("jdk_objects_equals_object", List.of("Point", "ColorPoint", "Main"), true, false);
    }

    @Test(groups = {"real-world-object", "tudo"})
    public void realWorldObjectCommonsLang3MutableInt() throws IOException, InterruptedException {
        realWorldExample("commons_lang3_mutableint_object", List.of("Main"), true, true);
    }

    @Test(groups = {"real-world-object", "tudo"})
    public void realWorldObjectCommonsLang3ComparableUtils() throws IOException, InterruptedException {
        realWorldExample("commons_lang3_comparableutils_object", List.of("Main"), false, true);
    }

    @Test(groups = {"real-world-object", "tudo"})
    public void realWorldObjectCommonsCollections4NullComparator() throws IOException, InterruptedException {
        realWorldExample("commons_collections4_nullcomparator_object", List.of("Main"), true, true);
    }

    @Test(groups = {"real-world-object", "tudo"})
    public void realWorldObjectCommonsCollections4Predicate() throws IOException, InterruptedException {
        realWorldExample("commons_collections4_predicate_object", List.of("Main"), true, true);
    }

    // ------------------------------------------------------------------------------------------------------------
    //  heap shapes: cycles and sharing created by constructors (docs/heap-model-plan.md). Every program has one
    //  branch that only aliasing reaches (assertion violation) and one that only distinct objects reach
    //  (IllegalStateException); both must be explored.
    // ------------------------------------------------------------------------------------------------------------

    private void heapExample(String example, List<String> classes, int depth) throws IOException, InterruptedException {
        heapExample("heap_programs", example, classes, depth, "-Dconcolic.symbolic.string.fields=false");
    }

    private void heapExample(String folder, String example, List<String> classes, int depth, String executorOptions)
            throws IOException, InterruptedException {
        String directory = String.format("src/test/resources/%s/%s/", folder, example);
        FilePreparator.compileClasses(classes, directory);
        DSE dse = TestUtils.getDseInstance("Main", directory, executorOptions, String.valueOf(depth));
        dse.executeAnalysis();

        System.setOut(originalOut);
        String output = filterOutPutStream();
        List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);
        System.out.println(String.join("\n", decisionTree));
        assertThat(decisionTree).noneMatch(line -> line.contains("DIVERGED") || line.contains("BUGGY"));
        assertThat(decisionTree).as("branch reachable only with aliasing")
                .anyMatch(line -> line.contains("java/lang/AssertionError"));
        assertThat(decisionTree).as("branch reachable only with distinct objects")
                .anyMatch(line -> line.contains("java/lang/IllegalStateException"));
    }

    @Test(groups = {"heap", "tudo"})
    public void heapSelfLoop() throws IOException, InterruptedException {
        heapExample("selfloop", List.of("Node", "Main"), 1);
    }

    @Test(groups = {"heap", "tudo"})
    public void heapSharing() throws IOException, InterruptedException {
        heapExample("sharing", List.of("Pair", "Main"), 2);
    }

    @Test(groups = {"heap", "tudo"})
    public void heapCycle2() throws IOException, InterruptedException {
        heapExample("cycle2", List.of("Ring", "Main"), 2);
    }

    @Test(groups = {"heap", "tudo"})
    public void heapNestedAlias() throws IOException, InterruptedException {
        heapExample("nested_alias", List.of("Box", "BoxPair", "Main"), 2);
    }

    @Test(groups = {"heap", "tudo"})
    public void heapWriteAlias() throws IOException, InterruptedException {
        heapExample("write_alias", List.of("Box", "BoxPair", "Main"), 2);
    }

    @Test(groups = {"heap", "tudo"})
    public void heapStaticShare() throws IOException, InterruptedException {
        heapExample("static_share", List.of("Holder", "Main"), 2);
    }

    // Strings as objects: identity by terms, content by the function str.val (symbolic string fields)

    private void stringExample(String example, List<String> classes) throws IOException, InterruptedException {
        heapExample("string_programs", example, classes, 2, "");
    }

    @Test(groups = {"strings", "tudo"})
    public void stringShare() throws IOException, InterruptedException {
        stringExample("str_share", List.of("P", "Main"));
    }

    @Test(groups = {"strings", "tudo"})
    public void stringEquals() throws IOException, InterruptedException {
        stringExample("str_equals", List.of("P", "Main"));
    }

    @Test(groups = {"strings", "tudo"})
    public void stringNull() throws IOException, InterruptedException {
        stringExample("str_null", List.of("H", "Main"));
    }

    @Test(groups = {"strings", "tudo"})
    public void stringContent() throws IOException, InterruptedException {
        stringExample("str_content", List.of("H", "Main"));
    }

    @Test(groups = {"strings", "tudo"})
    public void stringLiteral() throws IOException, InterruptedException {
        stringExample("str_literal", List.of("L", "Main"));
    }
}
