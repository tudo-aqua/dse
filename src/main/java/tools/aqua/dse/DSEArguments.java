package tools.aqua.dse;

import org.apache.commons.cli.*;
import tools.aqua.dse.Config;

import java.util.Arrays;
import java.util.Properties;

public class DSEArguments {

    private final Options options = new Options();

    public DSEArguments() {
        Option property  = OptionBuilder.withArgName( "property=value" )
                .hasArgs(2)
                .withValueSeparator()
                .withDescription( "use value for given property" )
                .create( "D" );

        Option file = OptionBuilder.withArgName( "file" )
                .hasArg()
                .withDescription(  "use given properties file" )
                .create( "f" );

        Option help = OptionBuilder.withArgName( "help" )
                .withDescription(  "show help" )
                .create( "h" );

        options.addOption( property );
        options.addOption( file );
        options.addOption( help );
    }

    public CommandLine parse(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            if (Arrays.asList(args).contains("-h")) {
                throw new ParseException("");
            }
            CommandLine line = parser.parse( options, args );
            return line;
        }
        catch( ParseException exp ) {
            throw new IllegalArgumentException( exp.getMessage() );
        }
    }

    public void usage(String message) {
        System.out.println();
        if (!message.isEmpty()) {
            System.out.println("Error: " + message);
            System.out.println();
        }
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "dse", options );
        System.out.println();
        System.out.println("dse properties:");
        System.out.println();
        printDSEOptionHelp("target.classpath", "target classpath");
        printDSEOptionHelp("target.class", "target class");
        printDSEOptionHelp("dse.executor", "executor command (e.g. java)");
        printDSEOptionHelp("dse.b64encode", "base64-encode concolic values passed to executor: true / false (default)");
        printDSEOptionHelp("dse.explore", "one of: inorder, bfs, dfs (default)");
        printDSEOptionHelp("dse.terminate.on", "| separated list of: assertion, error, bug, completion (default)");
        printDSEOptionHelp("dse.dp", "jconstraints id of solving backend");
        printDSEOptionHelp("dse.dp.incremental", "use incremental solving: true / false (default)");
        printDSEOptionHelp("dse.bounds", "use bounds on integer values when solving: true / false (default)");
        printDSEOptionHelp("dse.bounds.step", "step width (increase of bounds) when using bounds iteratively");
        printDSEOptionHelp("dse.bounds.iter", "no. of bounded solving attempts before dropping bounds");
        printDSEOptionHelp("dse.bounds.type", "fibonacci: uses fibonacci seq. from index 2 (1, 2, 3, 5, ...) as steps");
        System.out.println();
    }

    private void printDSEOptionHelp(String key, String description) {
        System.out.println(" " +
                String.format("%-22s", key) +
                description.replaceAll("\\n", "\n                       "));
    }

}
