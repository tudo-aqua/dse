package tools.aqua.dse;

import org.apache.commons.cli.*;

import java.util.Properties;

public class DSELauncher {

    public static void main(String[] args) {
        DSEArguments arguments = new DSEArguments();
        Config config = null;
        try {
            CommandLine cli = arguments.parse(args);
            config = Config.fromCommandLine(cli);
        }
        catch (Throwable t) {
            arguments.usage(t.getMessage());
            return;
        }

        DSE dse = new DSE(config);
        dse.executeAnalysis();
    }

}
