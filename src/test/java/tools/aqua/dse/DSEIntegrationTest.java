package tools.aqua.dse;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;


public class DSEIntegrationTest {
    public void printExample(String pathToExample) {
        ProcessBuilder pb = new ProcessBuilder("bat", "--color=always", String.format("../examples_concolic/%s", pathToExample));
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

    @Test
    public void test() {
        printExample("../examples_concolic/Example1.java");
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

    @Test
    public void Example1() {
        //define example
        String exampleName = "Example1.java";

        //execute example
        printExample(String.format("../examples_concolic/%s", exampleName));
        DSE dse = getExecution(exampleName, "../class_hierarchy.txt");
        dse.executeAnalysis();

        //checks

    }

}
