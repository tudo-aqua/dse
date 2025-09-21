package tools.aqua.dse.evaluation;

import org.junit.jupiter.api.*;
import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;

import java.io.*;
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

    public void printExample(String exampleName
    ) {
        String filePath = exampleName + ".java";
        ProcessBuilder pb = new ProcessBuilder("bat", "--color=always", String.format("../examples_concolic/%s", filePath));
        pb.inheritIO(); // direktes Durchreichen der Ausgabe
        Process p = null;
        try {
            p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

        return  new DSE(config);
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

    @Test //ok
    public void Example01_basic() {
        //define example
        String exampleName = "Example1";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");


        Instant start = Instant.now();
        dse.executeAnalysis();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT WITHOUT CONSTRUCTOR VARIATION
        //+ OK[complete path:true] .

        //EXPECTED OUTPUT
        //+ 0 : (='__object_constructor_0'NULL)
        //  + OK[complete path:true] .
        //+ 1 : (='__object_constructor_0'LB;|()V)
        //  + OK[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB;
        //+ 2 : (='__object_constructor_0'LA;|()V)
        //  + OK[complete path:true] . __object_constructor_0:=LA;|()V,__object_0:=LA;
        //+ 3 : (='__object_constructor_0'LA;|(II)V)
        //  + OK[complete path:true] . __object_constructor_0:=LA;|(II)V,__object_0:=LA;

        List<String> decisionTree = getDecisionTreeLineByLine(output);




        System.out.println(analyseDecisionTree(decisionTree));
        printDuration(duration);





//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(8);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : (='__object_constructor_0'NULL)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : (='__object_constructor_0'LB;|()V)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(4))
//                    .startsWith("+ 2 : (='__object_constructor_0'LA;|()V)");
//            softly.assertThat(decisionTree.get(5))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(6))
//                    .startsWith("+ 3 : (='__object_constructor_0'LA;|(II)V)");
//            softly.assertThat(decisionTree.get(7))
//                    .startsWith("+ OK[complete path:true] .");
//        });


    }

    @Test //todo: parsing problem of the trace -> out of memory
    public void Example02_casting() {
        //define example
        String exampleName = "Example2";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT WITHOUT CONSTRUCTOR VARIATION
        //+ 0 : ('extends'('__object_0',LB;) || 'extends'('__object_0',null))
        //  + OK[complete path:true] .
        //+ 1 : !('extends'('__object_0',LB;) || 'extends'('__object_0',null))
        //  + ERROR[complete path:true] . __object_0:=LA;,__object_constructor_0:=LA;|(II)V .
        //  java/lang/ClassCastException

        //EXPECTED OUTPUT
        //+ 0 : (='__object_constructor_0'NULL)
        //  + 0 : ('extends'('__object_0',LB;) || 'extends'('__object_0',null))
        //    + OK[complete path:true] .
        //  + 1 : !('extends'('__object_0',LB;) || 'extends'('__object_0',null))
        //    + UNSAT
        //+ 1 : (='__object_constructor_0'LB;|()V)
        //  + 0 : ((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + OK[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB;
        //  + 1 : !((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + UNSAT
        //+ 2 : (='__object_constructor_0'LA;|()V)
        //  + 0 : !!((((((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + UNSAT
        //  + 1 : !((((((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + ERROR[complete path:true] . __object_constructor_0:=LA;|()V,__object_0:=LA; . java/lang/ClassCastException
        //+ 3 : (='__object_constructor_0'LA;|(II)V)
        //  + 0 : !!((((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + UNSAT
        //  + 1 : !((((('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null)) || 'extends'('__object_0',LB;)) || 'extends'('__object_0',null))
        //    + ERROR[complete path:true] . __object_constructor_0:=LA;|(II)V,__object_0:=LA; . java/lang/ClassCastException



        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : ('extends'('__object_0',LB;) || 'extends'('__object_0',null))");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !('extends'('__object_0',LB;) || 'extends'('__object_0',null))");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ ERROR[complete path:true]");
//        });
    }



    @Test //ok (Example modified -> Delete second null check)
    public void Example03_isNull() {
        //define example
        String exampleName = "Example3";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

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

        //EXPECTED OUTPUT
        //+ 0 : (='__object_0'null)
        //  + ERROR[complete path:true] .  . java/lang/AssertionError
        //+ 1 : !(='__object_0'null)
        //  + OK[complete path:true] . __object_constructor_0:=LA;|()V,__object_constructor_1:=LB;|()V,__object_1:=LB;,__object_0:=LA;

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : (='__object_0'null)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true] .  . java/lang/AssertionError");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !(='__object_0'null)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true] . ");
//        });
    }

    @Test //ok
    public void Example04_isNotNull() {
        //define example
        String exampleName = "Example4";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

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

        //EXPECTED OUTPUT
        //+ 0 : !(='__object_0'null)
        //  + ERROR[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB; . java/lang/AssertionError
        //+ 1 : (='__object_0'null)
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !(='__object_0'null)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true] .");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : (='__object_0'null)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true] .");
//        });

    }

    @Test //todo: adapted example diverged -> object1 und object 2 sind verschieden, da das gleiche Object angelegt wird
    public void Example05_towIndependentObjects_checkIdentical() {
        //define example
        String exampleName = "Example5";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();

        try {
            FileWriter f = new FileWriter("test.txt");
            f.write(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !(='__object_0'null)
        //  + 0 : !(='__object_1'null)
        //    + 0 : !!(='__object_1''__object_0')
        //      + UNSAT
        //    + 1 : !(='__object_1''__object_0')
        //      + OK[complete path:true] . __object_1:=LB;,__object_constructor_1:=LB;|()V,__object_constructor_0:=LA;|()V,__object_0:=LA;
        //  + 1 : (='__object_1'null)
        //    + OK[complete path:true] . __object_1:=null,__object_constructor_1:=NULL,__object_constructor_0:=LA;|(II)V,__object_0:=LA;
        //+ 1 : (='__object_0'null)
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

        System.out.println(analyseDecisionTree(decisionTree));

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(10);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !(='__object_0'null)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ 0 : !(='__object_1'null)");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 0 : !!(='__object_1''__object_0')");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ UNSAT");
//            softly.assertThat(decisionTree.get(4))
//                    .startsWith("+ 1 : !(='__object_1''__object_0')");
//            softly.assertThat(decisionTree.get(5))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(6))
//                    .startsWith("+ 1 : (='__object_1'null)");
//            softly.assertThat(decisionTree.get(7))
//                    .startsWith("+ OK[complete path:true] .");
//            softly.assertThat(decisionTree.get(8))
//                    .startsWith("+ 1 : (='__object_0'null)");
//            softly.assertThat(decisionTree.get(9))
//                    .startsWith("+ OK[complete path:true] .");
//        });


    }

    @Test //todo: noch nicht gelöst
    public void Example06_towIndependentObjects_checkNotIdentical() {
        //define example
        String exampleName = "Example6";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !(='__object_1''__object_0')
        //  + ERROR[complete path:true] . __object_0:=null,__object_1:=LB;,__object_constructor_0:=NULL,__object_constructor_1:=LB;|()V . java/lang/AssertionError
        //+ 1 : (='__object_1''__object_0')
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !(='__object_1''__object_0')");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : (='__object_1''__object_0')");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//
//        });
    }

    @Test //ok
    public void Example07_OneObject_checkIdentical() {
        //define example
        String exampleName = "Example7";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : (='__object_0''__object_0')
        //  + ERROR[complete path:true] .  . java/lang/AssertionError
        //+ 1 : !(='__object_0''__object_0')
        //  + UNSAT


        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : (='__object_0''__object_0')");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !(='__object_0''__object_0')");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ UNSAT");
//        });
    }

    @Test
    public void Example08_OneObject_checkNotIdentical() {
        //define example
        String exampleName = "Example8";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !(='__object_0''__object_0')
        //  + UNSAT
        //+ 1 : (='__object_0''__object_0')
        //  + OK[complete path:true] .


        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !(='__object_0''__object_0')");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ UNSAT");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : (='__object_0''__object_0')");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//
//        });
    }


    @Test
    public void Example09_instanceOfA() {
        //define example
        String exampleName = "Example9";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : 'instance_of'('__object_0',LA;)
        //  + ERROR[complete path:true] . __object_constructor_0:=LA;|(II)V,__object_0:=LA; . java/lang/AssertionError
        //+ 1 : !'instance_of'('__object_0',LA;)
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : 'instance_of'('__object_0',LA;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !'instance_of'('__object_0',LA;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//
//        });
    }

    @Test
    public void Example10_notInstanceOfA() {
        //define example
        String exampleName = "Example10";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !'instance_of'('__object_0',LA;)
        //  + ERROR[complete path:true] .  . java/lang/AssertionError
        //+ 1 : 'instance_of'('__object_0',LA;)
        //  + OK[complete path:true] . __object_0:=LA;,__object_constructor_0:=LA;|(II)V

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !'instance_of'('__object_0',LA;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : 'instance_of'('__object_0',LA;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//        });
    }

    @Test
    public void Example11_instanceOfB() {
        //define example
        String exampleName = "Example11";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : 'instance_of'('__object_0',LB;)
        //  + ERROR[complete path:true] . __object_0:=LB;,__object_constructor_0:=LB;|()V . java/lang/AssertionError
        //+ 1 : !'instance_of'('__object_0',LB;)
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

        System.out.println(analyseDecisionTree(decisionTree));

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : 'instance_of'('__object_0',LB;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !'instance_of'('__object_0',LB;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//        });
    }

    @Test
    public void Example12_notInstanceOfB() {
        //define example
        String exampleName = "Example12";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !'instance_of'('__object_0',LB;)
        //  + ERROR[complete path:true] .  . java/lang/AssertionError
        //+ 1 : 'instance_of'('__object_0',LB;)
        //  + OK[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB;

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !'instance_of'('__object_0',LB;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : 'instance_of'('__object_0',LB;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//        });
    }


    @Test
    public void Example13_aX_isNull() {
        //define example
        String exampleName = "Example13";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example14_aX_isNotNull() {
        //define example
        String exampleName = "Example14";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example15_aX_smallerNull() {
        //define example
        String exampleName = "Example15";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example16_aX_smalerEqualNull() {
        //define example
        String exampleName = "Example16";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example17_aX_greaterNull() {
        //define example
        String exampleName = "Example17";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example18_aX_greaterEqualNull() {
        //define example
        String exampleName = "Example18";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example19_a1X_equals_a2X() {
        //define example
        String exampleName = "Example19";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example20_a1X_notEquals_a2X() {
        //define example
        String exampleName = "Example20";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example21_a1X_smaller_a2X() {
        //define example
        String exampleName = "Example21";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example22_a1X_smallerEquals_a2X() {
        //define example
        String exampleName = "Example22";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example23_a1X_greater_a2X() {
        //define example
        String exampleName = "Example23";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example24_a1X_greaterEquals_a2X() {
        //define example
        String exampleName = "Example24";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example25_a1X_equals_a2Y() {
        //define example
        String exampleName = "Example25";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example26_a1X_notEquals_a2Y() {
        //define example
        String exampleName = "Example26";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example27_a1X_smaller_a2Y() {
        //define example
        String exampleName = "Example27";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example28_a1X_smallerEquals_a2Y() {
        //define example
        String exampleName = "Example28";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example29_a1X_greater_a2Y() {
        //define example
        String exampleName = "Example29";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks#
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example30_a1X_greaterEquals_a2Y() {
        //define example
        String exampleName = "Example30";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example31_komplex_condition() {
        //define example
        String exampleName = "Example31";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example32_createIntegerFromObject() {
        //define example
        String exampleName = "Example32";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

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

        //EXPECTED OUTPUT
        //+ 0 : (('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LA;))
        //  + 0 : (('extends'('__object_1',LB;) || 'extends'('__object_1',null)) || 'extends'('__object_1',LA;))
        //    + 0 : !(='__object_0'null)
        //      + OK[complete path:true] . __object_constructor_1:=NULL,__object_constructor_0:=LB;|()V,__object_0:=LB;,__object_1:=null
        //    + 1 : (='__object_0'null)
        //      + ERROR[complete path:true] .  . java/lang/NullPointerException
        //  + 1 : !(('extends'('__object_1',LB;) || 'extends'('__object_1',null)) || 'extends'('__object_1',LA;))
        //    + UNSAT
        //+ 1 : !(('extends'('__object_0',LB;) || 'extends'('__object_0',null)) || 'extends'('__object_0',LA;))
        //  + UNSAT

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(10);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(4))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(5))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(6))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(7))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(8))
//                    .startsWith("");
//            softly.assertThat(decisionTree.get(9))
//                    .startsWith("");
//        });
    }

    @Test
    public void Example33_createStringFromObject() {
        //define example
        String exampleName = "Example33";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example35_instanceOfInterface() {
        //define example
        String exampleName = "Example35";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : 'instance_of'('__object_0',LGreeter;)
        //  + ERROR[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB; . java/lang/AssertionError
        //+ 1 : !'instance_of'('__object_0',LGreeter;)
        //  + OK[complete path:true] .

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : 'instance_of'('__object_0',LGreeter;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : !'instance_of'('__object_0',LGreeter;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true] .");
//        });
    }

    @Test
    public void Example36_notInstanceOfInterface() {
        //define example
        String exampleName = "Example36";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //                                                CHECKS
        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");

        //EXPECTED OUTPUT
        //+ 0 : !'instance_of'('__object_0',LGreeter;)
        //  + ERROR[complete path:true] .  . java/lang/AssertionError
        //+ 1 : 'instance_of'('__object_0',LGreeter;)
        //  + OK[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB;

        List<String> decisionTree = getDecisionTreeLineByLine(output);

//        SoftAssertions.assertSoftly(softly -> {
//            softly.assertThat(decisionTree).hasSize(4);
//            softly.assertThat(decisionTree.get(0))
//                    .startsWith("+ 0 : !'instance_of'('__object_0',LGreeter;)");
//            softly.assertThat(decisionTree.get(1))
//                    .startsWith("+ ERROR[complete path:true]");
//            softly.assertThat(decisionTree.get(2))
//                    .startsWith("+ 1 : 'instance_of'('__object_0',LGreeter;)");
//            softly.assertThat(decisionTree.get(3))
//                    .startsWith("+ OK[complete path:true]");
//        });
    }

    @Test
    public void Example37_a1Y_equals_a2Y() {
        //define example
        String exampleName = "Example37";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

//    @Test
//    public void Example39_foo() {
//        //define example
//        String exampleName = "Example39";
//
//        //execute example
//        printExample(exampleName);
//        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
//        dse.executeAnalysis();
//
//        //stop redirection of console log
//        System.setOut(originalOut);
//
//        //printing results
//        String output = filterOutPutStream();
//        //System.out.println(output);
//
//        //checks
//        //todo: Add checks
//        assertThat(output)
//                .doesNotContain("DIVERGED")
//                .doesNotContain("BUGGY");
//    }

    @Test
    public void Example40_foo_checkcast() {
        //define example
        String exampleName = "Example40";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example41_foo_instance_of() {
        //define example
        String exampleName = "Example41";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }

    @Test
    public void Example42_bar() {
        //define example
        String exampleName = "Example42";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }
    @Test
    public void Example50() {
        //define example
        String exampleName = "Example50";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy_2.txt", "../examples_concolic");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //printing results
        String output = filterOutPutStream();
        //System.out.println(output);

        //checks
        //todo: Add checks
        assertThat(output)
                .doesNotContain("DIVERGED")
                .doesNotContain("BUGGY");
    }
}



