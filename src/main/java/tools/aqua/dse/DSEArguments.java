/*
 * Copyright (C) 2021, Automated Quality Assurance Group,
 * TU Dortmund University, Germany. All rights reserved.
 *
 * DSE (dynamic symbolic execution) is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tools.aqua.dse;

import org.apache.commons.cli.*;
import java.util.Arrays;

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
        printDSEOptionHelp("dse.executor", "executor command (e.g. java)");
        printDSEOptionHelp("dse.executor.args", "executor args (e.g. -cp ... Main)");
        printDSEOptionHelp("dse.b64encode", "base64-encode concolic values passed to executor: true / false (default)");
        printDSEOptionHelp("dse.explore", "one of: inorder, bfs, dfs (default)");
        printDSEOptionHelp("dse.terminate.on", "| separated list of: assertion, error, bug, completion (default)");
        printDSEOptionHelp("dse.dp", "jconstraints id of solving backend");
        printDSEOptionHelp("dse.dp.incremental", "use incremental solving: true / false (default)");
        printDSEOptionHelp("dse.bounds", "use bounds on integer values when solving: true / false (default)");
        printDSEOptionHelp("dse.bounds.step", "step width (increase of bounds) when using bounds iteratively");
        printDSEOptionHelp("dse.bounds.iter", "no. of bounded solving attempts before dropping bounds");
        printDSEOptionHelp("dse.bounds.type", "fibonacci: uses fibonacci seq. from index 2 (1, 2, 3, 5, ...) as steps");
        printDSEOptionHelp("dse.witness", "save witness file if possible: true / false (default)");
        printDSEOptionHelp("dse.sources", "path to folder with sources");
        System.out.println();
    }

    private void printDSEOptionHelp(String key, String description) {
        System.out.println(" " +
                String.format("%-22s", key) +
                description.replaceAll("\\n", "\n                       "));
    }

}
