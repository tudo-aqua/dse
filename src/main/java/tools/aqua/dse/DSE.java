package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.trace.Trace;

public class DSE {

    private final Config config;

    public DSE(Config config) {
        this.config = config;
    }

    public void executeAnalysis() {
        Explorer explorer = new Explorer(config);
        Executor executor = new Executor(config);

        while (explorer.hasNextValuation()) {
            Valuation val = explorer.getNextValuation();
            Trace trace = executor.execute(val);
            explorer.addTrace(trace);
        }

        System.out.println(explorer.getAnalysis());
    }
}
