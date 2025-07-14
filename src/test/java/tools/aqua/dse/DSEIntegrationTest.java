package tools.aqua.dse;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DSEIntegrationTest {
    @Test
    public void test() {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", "-cp ../examples_concolic/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true Example3");
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("static.info", "../class_hierarchy.txt");

        Config config = Config.fromProperties(props);

        DSE dse = new DSE(config);
        dse.executeAnalysis();
    }

    @Test
    void testDateiEinlesen() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("class_hierarchy.txt");

        assertNotNull(inputStream, "Datei konnte nicht gefunden werden");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String inhalt = reader.readLine();
            System.out.println(inhalt);
        }
    }
}
