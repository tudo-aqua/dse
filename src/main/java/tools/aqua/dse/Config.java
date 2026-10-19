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
import tools.aqua.dse.objects.GroundingSolverContext;
import tools.aqua.dse.preprocessing.StaticManager;
import tools.aqua.dse.objects.LoggingSolverContext;
import tools.aqua.dse.preprocessing.ConstructorErrorAnalysis;
import tools.aqua.dse.preprocessing.ConstructorSummaryManager;
import tools.aqua.dse.preprocessing.SmtProblemManager;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private List<String> classPaths = new ArrayList<>();

    /** concolic values are passed to the executor base64-encoded: strings can contain separators (,) and quotes */
    private boolean b64encodeExecutorValue = true;

    private boolean incremental = false;

    private boolean witness = false;

    private Random random = null;

    private double fraction = 1.0;

    private ClassLoader sourceLoader = Config.class.getClassLoader();

    // TODO: make this configurable
    private int termination = TERMINATE_WHEN_COMPLETE;

    private final Properties properties;

    private ClazzModel clazzModel = null;

    private boolean constructorSummary = false;
    private boolean constructorBlueprintCache = false;

    /** compute constructor summaries on demand (true) or all up front (false, default) */
    private boolean incrementalSummaries = false;
    /** number of constructor signatures summarized per refinement step in incremental mode */
    private int summaryBatchSize = 1;
    /** maximal nesting depth of constructor calls in constructor signatures (the ASE'26 experiments used 3) */
    private int summaryDepth = 1;
    /** time limit of a single execution (SPouT run), in seconds; 0 means no limit */
    private long executorTimeoutSeconds = 120;

    public long getExecutorTimeoutSeconds() {
        return executorTimeoutSeconds;
    }

    /** time limit of a single solver query (dse.dp.timeout), in seconds; 0 means no limit */
    private long solverTimeoutSeconds = 0;

    public long getSolverTimeoutSeconds() {
        return solverTimeoutSeconds;
    }

    /** the options of bounded solving (dse.bounds*), also used for the DSE of constructors (summaries) */
    private final Properties boundsProperties = new Properties();

    /** time budget of the DSE of a single constructor, in seconds */
    private long summaryTimeoutSeconds = 60;
    // sound default: constructors analyzed incompletely are never excluded because of their errors
    private ConstructorErrorAnalysis.Mode errorAnalysisMode = ConstructorErrorAnalysis.Mode.WITH_UNKNOWN;
    private int errorAnalysisMaxMethods = ConstructorErrorAnalysis.DEFAULT_MAX_METHODS;
    /** instantiate obj.extends / obj.method.of per constraint instead of defining them by quantified formulas */
    private boolean groundObjectRelations = true;
    private int errorAnalysisMaxCallTargets = ConstructorErrorAnalysis.DEFAULT_MAX_CALL_TARGETS;
    private int maxObjectAnnotationDepth = 0;

    private SmtProblemManager smtProblemManager;

    private boolean baselineEvaluation;

    private long timeoutSeconds = 0;

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


    public ClazzModel getClazzModel() {
        return this.clazzModel;
    }


    public SolverContext getSolverContext() {
        System.out.println("Create SolverContext");
        GroundingSolverContext grounding = new GroundingSolverContext(this.solver.createContext());
        SolverContext ctx = new LoggingSolverContext(grounding);
        if(this.executorArgs != null) {
            this.baselineEvaluation = this.executorArgs.contains("-Dconcolic.object.factories=true");
        }

        if (!this.constructorSummary && !this.baselineEvaluation) {
            ctx.push();
            this.initializeSmtProblemManager();
            if(this.smtProblemManager != null) {
                StaticManager staticManager = this.smtProblemManager.getStaticManager();
                if (this.groundObjectRelations) {
                    grounding.setGrounding(staticManager.getGrounding());
                    if (this.smtProblemManager.getConstructorSummaryManager() != null) {
                        this.smtProblemManager.getConstructorSummaryManager().setClassListener(staticManager::registerClass);
                    }
                    staticManager.setNullConstant(ctx);
                } else {
                    staticManager.setStaticSmtLibCode(ctx);
                }
            }
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

    public int getMaxObjectAnnotationDepth() { return maxObjectAnnotationDepth; }

    /**
     * Maximal number of iterations that the executor unrolls symbolically per execution of an operation (e.g. one
     * decision per separator when splitting a symbolic string; later also loops), dse.unroll.bound; 0: no bound.
     */
    private int unrollBound = 0;

    public int getUnrollBound() { return unrollBound; }


    public double getFraction() {
        return fraction;
    }

    public Random getRandom() {
        return random;
    }

    private void parseProperties(Properties props) {
        if (props.containsKey("dse.executor.args")) {
            this.executorArgs = props.getProperty("dse.executor.args");

            Pattern pattern = Pattern.compile("-cp\\s+([^\\s]+)");
            Matcher matcher = pattern.matcher(this.executorArgs);

            if (matcher.find()) {
                this.classPaths = Arrays.stream(matcher.group(1).split(":")).toList();
            }
            else {
                throw new IllegalStateException("no classpath specified");
            }
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
        if (props.containsKey("dse.dp.timeout")) {
            // per query: a query that is not decided in time is unknown (DONT_KNOW), e.g. hard string constraints
            this.solverTimeoutSeconds = Long.parseLong(props.getProperty("dse.dp.timeout").trim());
            if (this.solverTimeoutSeconds > 0) {
                props.setProperty("z3.timeout", String.valueOf(this.solverTimeoutSeconds * 1000));
            }
        }

        // Java strings have at most 2^31 - 1 characters: lets the solver compare ints computed from string
        // lengths and indices in the integer domain
        if (!props.containsKey("z3.javaStrings")) {
            props.setProperty("z3.javaStrings", "true");
        }

        if (props.containsKey("dse.bounds")
                && Boolean.parseBoolean( props.getProperty("dse.bounds"))) {
            BoundedSolverProvider bp = new BoundedSolverProvider();
            this.solver = bp.createSolver(props);
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("dse.bounds")) {
                    this.boundsProperties.setProperty(key, props.getProperty(key));
                }
            }
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

        if (props.containsKey("dse.constructor.summary")) {
            this.constructorSummary = Boolean.parseBoolean(props.getProperty("dse.constructor.summary"));
        }

        if (props.containsKey("dse.constructor.blueprint.cache")) {
            this.constructorBlueprintCache = Boolean.parseBoolean(props.getProperty("dse.constructor.blueprint.cache"));
        }

        if (props.containsKey("dse.summaries.mode")) {
            String mode = props.getProperty("dse.summaries.mode").trim().toLowerCase();
            switch (mode) {
                case "eager": this.incrementalSummaries = false; break;
                case "incremental": this.incrementalSummaries = true; break;
                default: throw new IllegalArgumentException("dse.summaries.mode must be eager or incremental: " + mode);
            }
        }

        if (props.containsKey("dse.summaries.batch")) {
            this.summaryBatchSize = Integer.parseInt(props.getProperty("dse.summaries.batch").trim());
            if (this.summaryBatchSize < 1) {
                throw new IllegalArgumentException("dse.summaries.batch must be >= 1");
            }
        }

        if (props.containsKey("dse.executor.timeout")) {
            this.executorTimeoutSeconds = Long.parseLong(props.getProperty("dse.executor.timeout").trim());
        }

        if (props.containsKey("dse.summaries.timeout")) {
            this.summaryTimeoutSeconds = Long.parseLong(props.getProperty("dse.summaries.timeout").trim());
        }

        if (props.containsKey("dse.summaries.depth")) {
            this.summaryDepth = Integer.parseInt(props.getProperty("dse.summaries.depth").trim());
            if (this.summaryDepth < 1) {
                throw new IllegalArgumentException("dse.summaries.depth must be >= 1");
            }
        }

        if (props.containsKey("dse.errors.mode")) {
            String mode = props.getProperty("dse.errors.mode").trim().toLowerCase();
            switch (mode) {
                case "declared": this.errorAnalysisMode = ConstructorErrorAnalysis.Mode.DECLARED_AND_LANGUAGE; break;
                case "unknown": this.errorAnalysisMode = ConstructorErrorAnalysis.Mode.WITH_UNKNOWN; break;
                default: throw new IllegalArgumentException("dse.errors.mode must be declared or unknown: " + mode);
            }
        }

        if (props.containsKey("dse.objects.relations")) {
            String mode = props.getProperty("dse.objects.relations").trim().toLowerCase();
            switch (mode) {
                case "grounded": this.groundObjectRelations = true; break;
                case "quantified": this.groundObjectRelations = false; break;
                default: throw new IllegalArgumentException("dse.objects.relations must be grounded or quantified: " + mode);
            }
        }

        if (props.containsKey("dse.errors.maxMethods")) {
            this.errorAnalysisMaxMethods = Integer.parseInt(props.getProperty("dse.errors.maxMethods").trim());
        }

        if (props.containsKey("dse.errors.maxCallTargets")) {
            this.errorAnalysisMaxCallTargets = Integer.parseInt(props.getProperty("dse.errors.maxCallTargets").trim());
        }

        if (props.containsKey("dse.unroll.bound")) {
            this.unrollBound = Integer.parseInt(props.getProperty("dse.unroll.bound").trim());
        }
        if (props.containsKey("concolic.max.object.annotation.depth")) {
            this.maxObjectAnnotationDepth = Integer.parseInt(props.getProperty("concolic.max.object.annotation.depth"));
        }

        if (props.containsKey("dse.timeout")) {
            this.timeoutSeconds = Long.parseLong(props.getProperty("dse.timeout"));
        }
        if (props.containsKey("iflow.fraction")) {
            this.fraction = Double.parseDouble(props.getProperty("iflow.fraction"));
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

    public void initializeSmtProblemManager() {
        if (this.constructorSummary) {
            throw new IllegalStateException("In ConstructorSummaryMode the SmtProblemManger cannot be created " +
                    "because of recursion.");
        }
        if (this.smtProblemManager == null && this.classPaths.size() > 0) {
            String path = this.classPaths.get(0);
            System.out.println("config path: " + path);
            int depth = this.summaryDepth;
            ConstructorSummaryManager.setSummaryTimeoutSeconds(this.summaryTimeoutSeconds);
            ConstructorSummaryManager.setSummaryBounds(this.boundsProperties);
            ConstructorSummaryManager.setSolverTimeoutSeconds(this.solverTimeoutSeconds);
            ConstructorSummaryManager.setUnrollBound(this.unrollBound);
            ConstructorSummaryManager.setExecutor(this.executorCmd, this.executorArgs);
            this.smtProblemManager = this.incrementalSummaries
                    ? new SmtProblemManager(path, depth, new ConstructorSummaryManager.IncrementalOptions(
                            this.summaryBatchSize, this.errorAnalysisMode,
                            this.errorAnalysisMaxCallTargets, this.errorAnalysisMaxMethods))
                    : new SmtProblemManager(path, depth, this.constructorBlueprintCache);
        }
    }


    public SmtProblemManager getSmtProblemManager() {
        return this.smtProblemManager;
    }

    public boolean isConstructorSummary() {
        return constructorSummary;
    }

    public boolean isIncrementalSummaries() {
        return incrementalSummaries;
    }

    public boolean isBaselineEvaluation() {
        return baselineEvaluation;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
