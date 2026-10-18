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
        printDSEOptionHelp("dse.terminate.on", "| separated list of: assertion, error, bug, taint, completion (default)");
        printDSEOptionHelp("dse.dp", "jconstraints id of solving backend");
        printDSEOptionHelp("dse.dp.incremental", "use incremental solving: true / false (default)");
        printDSEOptionHelp("dse.dp.timeout", "time limit of a single solver query in seconds (default 0: none); undecided\nqueries are unknown (the target is not explored)");
        printDSEOptionHelp("dse.bounds", "use bounds on integer values when solving: true / false (default)");
        printDSEOptionHelp("dse.bounds.step", "step width (increase of bounds) when using bounds iteratively");
        printDSEOptionHelp("dse.bounds.iter", "no. of bounded solving attempts before dropping bounds");
        printDSEOptionHelp("dse.bounds.type", "fibonacci: uses fibonacci seq. from index 2 (1, 2, 3, 5, ...) as steps");
        printDSEOptionHelp("dse.witness", "save witness file if possible: true / false (default)");
        printDSEOptionHelp("dse.sources", "path to folder with sources");
        printDSEOptionHelp("iflow.fraction", "fraction of paths to use in information flow analysis (double)");
        printDSEOptionHelp("static.info", "static information on inheritance and constructors for objects (filename)");
        printDSEOptionHelp("random.seed", "random seed (long value)");
        printDSEOptionHelp("dse.timeout", "timeout in seconds (long value)");
        printDSEOptionHelp("dse.summaries.mode", "constructor summaries: eager (default, all up front) / incremental (on demand)");
        printDSEOptionHelp("dse.summaries.depth", "max. nesting depth of constructor calls in summarized signatures (default 1)");
        printDSEOptionHelp("dse.summaries.batch", "incremental: no. of signatures summarized per refinement step (default 1)");
        printDSEOptionHelp("dse.executor.timeout", "time limit of a single execution in seconds (default 120, 0: none)");
        printDSEOptionHelp("dse.summaries.timeout", "time budget of the DSE of a single constructor in seconds (default 60)");
        printDSEOptionHelp("dse.objects.relations", "encoding of obj.extends / obj.method.of: grounded (default, instantiated\nper constraint) / quantified (defined by universally quantified formulas)");
        printDSEOptionHelp("dse.errors.mode", "incremental: constructor error inference: unknown (default, imprecision means unknown\nerrors) / declared (declared and language exceptions, trusted as complete)");
        printDSEOptionHelp("dse.errors.maxMethods", "incremental: max. no. of methods analyzed for constructor errors (default 200000)");
        printDSEOptionHelp("dse.errors.maxCallTargets", "incremental: max. no. of call targets followed per call site");
        System.out.println();
    }

    private void printDSEOptionHelp(String key, String description) {
        System.out.println(" " +
                String.format("%-22s", key) +
                description.replaceAll("\\n", "\n                       "));
    }

}
