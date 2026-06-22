package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.*;
import tools.aqua.dse.DSE;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class GeneratedBenchmarksTest {
    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private final boolean debug = true;

    @BeforeEach
    void setUpStreams() {
        this.originalOut = System.out;

        if (this.debug) {
            PrintStream teeStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    capturedOutput.write(b);
                    originalOut.write(b);
                }
            }, true);
            System.setOut(teeStream);
        } else {
            System.setOut(new PrintStream(capturedOutput));
            System.setErr(new PrintStream(capturedErr));
        }
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
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

        for (String key : List.of(
                "OK", "ERROR", "UNSAT", "SKIPPED",
                "#EDGES_OBJECT_CONSTRUCTOR_VARIATION", "#EDGES_NORMAL_VARIATION")) {
            counts.putIfAbsent(key, 0L);
        }

        counts.put("#EDGES_NORMAL_VARIATION", counts.get("#EDGES_NORMAL_VARIATION") - counts.get("SKIPPED") * 2);
        counts.put("#PATHS", counts.get("OK") + counts.get("ERROR") + counts.get("UNSAT"));
        counts.put("#EXECUTED_PATHS", counts.get("OK") + counts.get("ERROR"));
        counts.put("#EDGES", counts.get("#EDGES_OBJECT_CONSTRUCTOR_VARIATION") + counts.get("#EDGES_NORMAL_VARIATION"));
        return counts;
    }

    private void printDuration(Duration duration) {
        long minutes = duration.getSeconds() / 60;
        long seconds = duration.getSeconds() % 60;
        long nanoSeconds = duration.getNano();

        System.out.println("Duration of the DSE execution");
        System.out.println("minutes: " + minutes);
        System.out.println("seconds: " + seconds);
        System.out.println("nanoSeconds: " + nanoSeconds);
    }
@Test
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__CollectionTypeAdapterFactory__create__1da5ad1ada() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__CollectionTypeAdapterFactory__create__1da5ad1ada/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__Excluder__excludeField__ef406d01a3() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__Excluder__excludeField__ef406d01a3/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__GsonBuilder__registerTypeAdapter__ffd9eeaeeb() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__GsonBuilder__registerTypeAdapter__ffd9eeaeeb/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__GsonBuilder__registerTypeHierarchyAdapter__5161328557() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__GsonBuilder__registerTypeHierarchyAdapter__5161328557/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__Gson__getAdapter__b09df9b215() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__Gson__getAdapter__b09df9b215/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__Gson__getDelegateAdapter__72a2d60894() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__Gson__getDelegateAdapter__72a2d60894/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__Gson__toJsonTree__6d0bc5fe10() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__Gson__toJsonTree__6d0bc5fe10/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__Gson__toJson__87db1aff4f() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__Gson__toJson__87db1aff4f/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__JsonAdapterAnnotationTypeAdapterFactory__create__6e9a63c29c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__JsonAdapterAnnotationTypeAdapterFactory__create__6e9a63c29c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__JsonPrimitive__equals__0399f2ba27() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__JsonPrimitive__equals__0399f2ba27/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__MapTypeAdapterFactory__create__4f18c84209() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__MapTypeAdapterFactory__create__4f18c84209/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__ReflectiveTypeAdapterFactory__create__b0c0eb2c78() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__ReflectiveTypeAdapterFactory__create__b0c0eb2c78/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__TypeAdapters__newFactoryForMultipleTypes__66802c1ca1() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__TypeAdapters__newFactoryForMultipleTypes__66802c1ca1/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__TypeAdapters__newFactory__acab8da5b1() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__TypeAdapters__newFactory__acab8da5b1/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_code_gson__gson__2_8_8__TypeToken__isAssignableFrom__dd2ce09e99() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_code_gson__gson__2_8_8/com_google_code_gson__gson__2_8_8__TypeToken__isAssignableFrom__dd2ce09e99/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableListMultimap__copyOf__59c71e5079() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableListMultimap__copyOf__59c71e5079/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableListMultimap__of__6111a0a458() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableListMultimap__of__6111a0a458/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableListMultimap__of__9a424f6fb2() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableListMultimap__of__9a424f6fb2/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableListMultimap__of__c7af95c101() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableListMultimap__of__c7af95c101/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSetMultimap__copyOf__6091ec3276() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSetMultimap__copyOf__6091ec3276/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSetMultimap__of__0c7721450b() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSetMultimap__of__0c7721450b/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSetMultimap__of__122d39d00e() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSetMultimap__of__122d39d00e/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSetMultimap__of__5b01c50dc7() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSetMultimap__of__5b01c50dc7/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSortedMap__of__31b9084183() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSortedMap__of__31b9084183/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSortedMap__of__8270247ed9() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSortedMap__of__8270247ed9/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSortedMap__of__f0670aef3d() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSortedMap__of__f0670aef3d/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSortedMap__of__fd1d0ee3be() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSortedMap__of__fd1d0ee3be/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__ImmutableSortedSet__of__a8aa8a4d89() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__ImmutableSortedSet__of__a8aa8a4d89/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__Iterables__getLast__faa1e1d1aa() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__Iterables__getLast__faa1e1d1aa/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__LinkedListMultimap__replaceValues__13136ae702() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__LinkedListMultimap__replaceValues__13136ae702/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_google_guava__guava_collections__r03__Maps__difference__bf8061474b() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_google_guava__guava-collections__r03/com_google_guava__guava-collections__r03__Maps__difference__bf8061474b/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__HikariConfig__addDataSourceProperty__ebba7b8524() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__HikariConfig__addDataSourceProperty__ebba7b8524/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__HikariConfig__addHealthCheckProperty__46af83e80d() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__HikariConfig__addHealthCheckProperty__46af83e80d/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__HikariConfig__copyStateTo__cc420e6d8f() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__HikariConfig__copyStateTo__cc420e6d8f/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__HikariConfigurationUtil__loadConfiguration__a941e0a63c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__HikariConfigurationUtil__loadConfiguration__a941e0a63c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__PrometheusHistogramMetricsTrackerFactory__create__f0f482d634() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__PrometheusHistogramMetricsTrackerFactory__create__f0f482d634/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__PrometheusMetricsTrackerFactory__create__218f828871() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__PrometheusMetricsTrackerFactory__create__218f828871/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__PropertyElf__getProperty__c8f45b48ab() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__PropertyElf__getProperty__c8f45b48ab/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__PropertyElf__setTargetFromProperties__22789ad5e5() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__PropertyElf__setTargetFromProperties__22789ad5e5/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__UtilityElf__createInstance__f5e3a529c9() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__UtilityElf__createInstance__f5e3a529c9/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__UtilityElf__createThreadPoolExecutor__268c4fd2fd() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__UtilityElf__createThreadPoolExecutor__268c4fd2fd/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__UtilityElf__createThreadPoolExecutor__e288df4273() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__UtilityElf__createThreadPoolExecutor__e288df4273/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void com_zaxxer__HikariCP__7_1_0__UtilityElf__safeIsAssignableFrom__06af2b29cd() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/com_zaxxer__HikariCP__7_1_0/com_zaxxer__HikariCP__7_1_0__UtilityElf__safeIsAssignableFrom__06af2b29cd/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void commons_io__commons_io__2_21_0__FilenameUtils__wildcardMatch__86e8d5d5ba() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/commons-io__commons-io__2_21_0/commons-io__commons-io__2_21_0__FilenameUtils__wildcardMatch__86e8d5d5ba/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void commons_io__commons_io__2_21_0__IOStream__collect__c1661de318() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/commons-io__commons-io__2_21_0/commons-io__commons-io__2_21_0__IOStream__collect__c1661de318/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void commons_io__commons_io__2_21_0__Tailer__create__a2a786027c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/commons-io__commons-io__2_21_0/commons-io__commons-io__2_21_0__Tailer__create__a2a786027c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__collate__afc9583334() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__collate__afc9583334/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__get__f0c6b7357b() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__get__f0c6b7357b/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__removeAll__839febd704() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__removeAll__839febd704/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__sizeIsEmpty__968d9cd703() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__sizeIsEmpty__968d9cd703/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__size__7ede5d54e0() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__size__7ede5d54e0/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__CollectionUtils__subtract__68a5cb7f61() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__CollectionUtils__subtract__68a5cb7f61/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__FixedOrderComparator__compare__d92d442a01() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__FixedOrderComparator__compare__d92d442a01/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__Flat3Map__put__4ec7f60313() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__Flat3Map__put__4ec7f60313/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__InstantiateFactory__instantiateFactory__49cdbbbff3() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__InstantiateFactory__instantiateFactory__49cdbbbff3/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__IterableUtils__partition__9d8a276bdf() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__IterableUtils__partition__9d8a276bdf/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__IteratorUtils__getIterator__bb37e0006c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__IteratorUtils__getIterator__bb37e0006c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__IteratorUtils__toString__ef5a3d9ed3() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__IteratorUtils__toString__ef5a3d9ed3/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__ListOrderedMap__put__0ce4882e48() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__ListOrderedMap__put__0ce4882e48/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__put__32f1ee0830() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__put__32f1ee0830/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__put__3515a0f1f8() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__put__3515a0f1f8/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__put__39a571c0fa() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__put__39a571c0fa/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__put__3ccbb01d63() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__put__3ccbb01d63/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__removeAll__74423dd0d7() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__removeAll__74423dd0d7/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_collections4__4_5_0__MultiKeyMap__removeAll__c30e7951c0() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-collections4__4_5_0/org_apache_commons__commons-collections4__4_5_0__MultiKeyMap__removeAll__c30e7951c0/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__DiffBuilder__append__1f3fb5326c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__DiffBuilder__append__1f3fb5326c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__EnumUtils__getFirstEnumIgnoreCase__3ec101a0da() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__EnumUtils__getFirstEnumIgnoreCase__3ec101a0da/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__EventUtils__addEventListener__78c5d35d94() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__EventUtils__addEventListener__78c5d35d94/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__FastDatePrinter__format__8444af2003() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__FastDatePrinter__format__8444af2003/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__FormattableUtils__append__9d864be05a() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__FormattableUtils__append__9d864be05a/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__MethodUtils__getMatchingAccessibleMethod__3d6354460e() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__MethodUtils__getMatchingAccessibleMethod__3d6354460e/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__MethodUtils__getMatchingMethod__551cdf6430() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__MethodUtils__getMatchingMethod__551cdf6430/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__StringUtils__getFuzzyDistance__e10d6db185() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__StringUtils__getFuzzyDistance__e10d6db185/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__StringUtils__substringsBetween__7391c9f03d() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__StringUtils__substringsBetween__7391c9f03d/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__TypeUtils__getRawType__c52898b404() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__TypeUtils__getRawType__c52898b404/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__TypeUtils__unrollVariables__f85aefcd3c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__TypeUtils__unrollVariables__f85aefcd3c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_commons__commons_lang3__3_17_0__WordUtils__wrap__4e095a7753() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_commons__commons-lang3__3_17_0/org_apache_commons__commons-lang3__3_17_0__WordUtils__wrap__4e095a7753/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__AbstractConfiguration__stop__ab1f0fe9de() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__AbstractConfiguration__stop__ab1f0fe9de/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__AbstractManager__getManager__95185578a0() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__AbstractManager__getManager__95185578a0/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__CronExpression__getTimeAfter__778e77128c() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__CronExpression__getTimeAfter__778e77128c/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__DefaultMergeStrategy__mergConfigurations__5abb6618d4() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__DefaultMergeStrategy__mergConfigurations__5abb6618d4/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__DefaultMergeStrategy__mergeRootProperties__ff43a6772f() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__DefaultMergeStrategy__mergeRootProperties__ff43a6772f/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__FastDatePrinter__format__d877525caf() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__FastDatePrinter__format__d877525caf/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__FileAppender__createAppender__99d12991cf() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__FileAppender__createAppender__99d12991cf/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__InternalLoggerRegistry__computeIfAbsent__2a2ed62ebb() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__InternalLoggerRegistry__computeIfAbsent__2a2ed62ebb/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__Log4jContextFactory__getContext__e3c4d4bb63() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__Log4jContextFactory__getContext__e3c4d4bb63/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__MemoryMappedFileAppender__createAppender__4afce0c24b() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__MemoryMappedFileAppender__createAppender__4afce0c24b/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__PatternParser__parse__c37f744278() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__PatternParser__parse__c37f744278/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__PluginElementVisitor__visit__0053290fa6() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__PluginElementVisitor__visit__0053290fa6/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__RandomAccessFileAppender__createAppender__fee243a1dc() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__RandomAccessFileAppender__createAppender__fee243a1dc/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__RollingFileAppender__createAppender__ae5e1e3dc6() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__RollingFileAppender__createAppender__ae5e1e3dc6/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__ScriptFilter__filter__c9502397a5() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__ScriptFilter__filter__c9502397a5/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_apache_logging_log4j__log4j_core__2_25_3__TypeUtil__isAssignable__012f3b0580() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_apache_logging_log4j__log4j-core__2_25_3/org_apache_logging_log4j__log4j-core__2_25_3__TypeUtil__isAssignable__012f3b0580/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jetbrains_kotlin__kotlin_stdlib__2_4_0__SpreadBuilder__addSpread__65db37481d() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jetbrains_kotlin__kotlin-stdlib__2_4_0/org_jetbrains_kotlin__kotlin-stdlib__2_4_0__SpreadBuilder__addSpread__65db37481d/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__HttpConnection__data__db85dcdef0() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__HttpConnection__data__db85dcdef0/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Jsoup__clean__770c6eb227() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Jsoup__clean__770c6eb227/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__NodeTraversor__traverse__b6f68a82b6() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__NodeTraversor__traverse__b6f68a82b6/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Node__wrap__ea8efeee7b() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Node__wrap__ea8efeee7b/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__addEnforcedAttribute__b4bbcfc084() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__addEnforcedAttribute__b4bbcfc084/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__addProtocols__60bb86a329() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__addProtocols__60bb86a329/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__getEnforcedAttributes__d374782f14() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__getEnforcedAttributes__d374782f14/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__isSafeAttribute__d7bbd45860() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__isSafeAttribute__d7bbd45860/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__removeAttributes__e9e7c92fe0() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__removeAttributes__e9e7c92fe0/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__removeEnforcedAttribute__ed7fdb91a1() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__removeEnforcedAttribute__ed7fdb91a1/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Safelist__removeProtocols__11f2261242() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Safelist__removeProtocols__11f2261242/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Selector__select__d511da94e5() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Selector__select__d511da94e5/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__Tag__valueOf__a31158c300() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__Tag__valueOf__a31158c300/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

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
@Tag("generated")
public void org_jsoup__jsoup__1_18_1__W3CDom__asString__2e2b575178() throws IOException, InterruptedException {
    String directoryOfTheExample = "src/test/resources/generated-examples/org_jsoup__jsoup__1_18_1/org_jsoup__jsoup__1_18_1__W3CDom__asString__2e2b575178/";

    DSE dse = TestUtils.getDseInstance("Main", directoryOfTheExample);

    Instant start = Instant.now();
    dse.executeAnalysis();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);

    System.setOut(originalOut);
    String output = filterOutPutStream();

    assertThat(output)
            .doesNotContain("DIVERGED")
            .doesNotContain("BUGGY");

    List<String> decisionTree = TestUtils.getDecisionTreeLineByLine(output);

    assertThat(TestUtils.validAssert(decisionTree)).isFalse();
    assertThat(TestUtils.noRuntimeException(decisionTree)).isTrue();

    System.out.println(analyseDecisionTree(decisionTree));
    printDuration(duration);
}

}
