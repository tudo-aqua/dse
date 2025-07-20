package tools.aqua.dse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;


public class DSEIntegrationTest {
    private final ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private boolean debug = false;

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

    private static DSE getExecution(String exampleName, String pathToClassHierachy) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", "-cp ../examples_concolic/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true " + exampleName);
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

    @Test
    public void Example1() {
        //define example
        String exampleName = "Example1";

        //execute example
        printExample(exampleName);
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt");
        dse.executeAnalysis();

        //stop redirection of console log
        System.setOut(originalOut);

        //checks
        String output = filterOutPutStream();
        System.out.println(output);

        assertThat(output)
                .contains("+ 0 : (='__object_0'null) \n" +
                        "  + OK[complete path:true] . \n" +
                        "+ 1 : !(='__object_0'null) \n" +
                        "  + OK[complete path:true] . __object_constructor_0:=LB;|()V,__object_0:=LB;");

    }

}
