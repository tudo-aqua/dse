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

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.solvers.ConstraintSolverFactory;
import org.apache.commons.cli.CommandLine;
import tools.aqua.dse.bounds.BoundedSolverProvider;
import tools.aqua.dse.objects.ClazzModel;
import tools.aqua.dse.objects.LoggingSolverContext;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class Config {

    public enum ExplorationStrategy  {BFS, DFS, IN_ORDER;

        public static ExplorationStrategy fromString(String property) {
            switch (property.trim().toLowerCase()) {
                case "bfs":
                    return BFS;
                case "dfs":
                    return DFS;
                case "inorder":
                    return IN_ORDER;
                default:
                    throw new IllegalArgumentException("unsupported exploration strategy: " + property);
            }
        }
    };

    public static final int TERMINATE_WHEN_COMPLETE = 0;
    public static final int TERMINATE_ON_ASSERTION_VIOLATION = 1;
    public static final int TERMINATE_ON_ERROR = 2;
    public static final int TERMINATE_ON_BUG = 4;

    public static final int TERMINATE_ON_TAINT = 8;

    private ConstraintSolver solver;

    private ExplorationStrategy strategy = ExplorationStrategy.DFS;

    private String executorCmd;

    private String executorArgs;

    private boolean b64encodeExecutorValue = false;

    private boolean incremental = false;

    private boolean witness = false;

    private Random random = null;

    private double fraction = 1.0;

    private ClassLoader sourceLoader = Config.class.getClassLoader();

    // TODO: make this configurable
    private int termination = TERMINATE_WHEN_COMPLETE;

    private final Properties properties;

    private ClazzModel clazzModel = null;

    private Config(Properties properties) {
        this.properties = properties;
    }

    /**
     * should dse explore open nodes
     * @return
     */
    public boolean getExploreMode() {
        return true;
    }

    /**
     * values to replay before exploring
     *
     * @return
     */
    public Iterator<Valuation> getReplayValues() {
        return null;
    }

    /**
     * use incremental solving
     *
     * @return
     */
    public boolean isIncremental() {
        return incremental;
    }



    public SolverContext getSolverContext() {
        System.out.println("Create SolverContext");
        SolverContext ctx = new LoggingSolverContext(this.solver.createContext());
        // init object constraints signature
        if (clazzModel != null) {
            System.out.println("Claaz Model found");
            clazzModel.initObjectsStructure(ctx);
            clazzModel.addConstructorInitializationConstraints(ctx, 1);
            clazzModel.addFiniteDomainConstraints(ctx, 1);
        }
        return ctx;
    }


    /**
     * max depth of exploration exceeded at depth
     *
     * @param depth
     * @return
     */
    public boolean maxDepthExceeded(int depth) {
        return false;
    }

    /**
     * exploration strategy
     *
     * @return
     */
    public ExplorationStrategy getStrategy() {
        return strategy;
    }

    public String getExecutorCmd() {
        return executorCmd;
    }

    public String getExecutorArgs() {
        return executorArgs;
    }

    public ClassLoader getSourceLoader() {
        return sourceLoader;
    }

    public boolean isB64encodeExecutorValue() {
        return b64encodeExecutorValue;
    }

    public int getTermination() {
        return termination;
    }

    public boolean isWitness() { return witness; }


    public double getFraction() {
        return fraction;
    }

    public Random getRandom() {
        return random;
    }

    private void parseProperties(Properties props) {
        if (props.containsKey("dse.executor.args")) {
            this.executorArgs = props.getProperty("dse.executor.args");
        }
        if (props.containsKey("dse.executor")) {
            this.executorCmd = props.getProperty("dse.executor");
        }
        else {
            throw new IllegalStateException("no executor command specified");
        }
        if (props.containsKey("dse.b64encode")) {
            this.b64encodeExecutorValue = Boolean.parseBoolean( props.getProperty("dse.b64encode") );
        }
        if (props.containsKey("dse.explore")) {
            this.strategy = ExplorationStrategy.fromString(props.getProperty("dse.explore"));
        }
        if (props.containsKey("dse.terminate.on")) {
            this.termination = parseTermination(props.getProperty("dse.terminate.on"));
        }
        if (props.containsKey("dse.dp.incremental")) {
            this.incremental = Boolean.parseBoolean(props.getProperty("dse.dp.incremental"));
        }

        if (props.containsKey("dse.bounds")
                && Boolean.parseBoolean( props.getProperty("dse.bounds"))) {
            BoundedSolverProvider bp = new BoundedSolverProvider();
            this.solver = bp.createSolver(props);
        }
        else {
            String solverName = props.getProperty("dse.dp");
            this.solver = ConstraintSolverFactory.createSolver(solverName, props);
        }

        if (props.containsKey("dse.witness")) {
            this.witness = Boolean.parseBoolean(props.getProperty("dse.witness"));
        }
        if (props.containsKey("dse.sources")) {
            String sources = props.getProperty("dse.sources");
            String[] folders = sources.split("\\:");
            URL urls[] = new URL[folders.length];
            int i=0;
            for (String f : folders) {
                try {
                    urls[i++] = Paths.get(f.trim()).toAbsolutePath().toUri().toURL();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }

            sourceLoader = new URLClassLoader(urls);
        }
        if (props.containsKey("iflow.fraction")) {
            this.fraction = Double.parseDouble(props.getProperty("iflow.fraction"));
        }

        if (props.containsKey("static.info")) {
            String filename = props.getProperty("static.info");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
                String line = null;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.clazzModel = new ClazzModel(sb.toString());
        }

        long seed = (new Random()).nextLong();
        if (props.containsKey("random.seed")) {
            seed = Long.parseLong(props.getProperty("random.seed"));
        }
        System.out.println("Random seed: " + seed);
        this.random = new Random(seed);
    }

    private int parseTermination(String property) {
        int terminate = TERMINATE_WHEN_COMPLETE;
        String[] flags = property.split("\\|");
        for (String flag : flags) {
            flag = flag.trim();
            if (!flag.isEmpty()) {
                switch (flag.toLowerCase()) {
                    case "assertion":
                        terminate |= TERMINATE_ON_ASSERTION_VIOLATION;
                        break;
                    case "error":
                        terminate |= TERMINATE_ON_ERROR;
                        break;
                    case "bug":
                        terminate |= TERMINATE_ON_BUG;
                        break;
                    case "taint":
                        terminate |= TERMINATE_ON_TAINT;
                        break;
                    case "completion":
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported termination flag: " + flag);
                }
            }
        }
        return terminate;
    }

    public static Config fromProperties(Properties props) {
        Config config = new Config(props);
        config.parseProperties(props);
        return config;
    }

    public static Config fromCommandLine(CommandLine cli) {
        Properties props = new Properties();
        if (cli.hasOption("f")) {
            String filename = cli.getOptionValue("f");
            try (FileInputStream fs = new FileInputStream(filename)) {
                props.load(fs);
            } catch (IOException e) {
                System.err.println("Could not read properties file " + filename);
               throw new RuntimeException();
            }
        }

        if (cli.hasOption("D")) {
            Properties propArgs = cli.getOptionProperties("D");
            for (Map.Entry<Object,Object> entry : propArgs.entrySet()) {
                props.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return Config.fromProperties(props);
    }

}
