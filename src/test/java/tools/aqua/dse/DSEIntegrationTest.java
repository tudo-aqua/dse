package tools.aqua.dse;

import org.junit.jupiter.api.Test;

import java.util.Properties;

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

        Config config = Config.fromProperties(props);

        DSE dse = new DSE(config);
        dse.executeAnalysis();
    }
}
