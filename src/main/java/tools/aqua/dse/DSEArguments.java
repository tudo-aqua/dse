package tools.aqua.dse;

import org.apache.commons.cli.*;
import tools.aqua.dse.Config;

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

        options.addOption( property );
        options.addOption( file );
    }

    public CommandLine parse(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse( options, args );
            return line;
        }
        catch( ParseException exp ) {
            System.err.println( "Could not parse arguments: " + exp.getMessage() );
            throw new RuntimeException();
        }
    }

    public void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "dse", options );
    }

}
