package tools.aqua.dse.evaluation;

import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestUtils {

    public static final boolean BLUEPRINT_CACHE_ENABLED =
            Boolean.parseBoolean(System.getProperty("test.blueprint.cache", "true"));

    public static void printExample(String exampleName,
                                    String directorOfExample
    ) {
        String filePath = exampleName + ".java";
        ProcessBuilder pb = new ProcessBuilder("bat", "--color=always", String.format("%s%s", directorOfExample, filePath));
        pb.inheritIO(); //direct passing of the output
        Process p = null;
        try {
            p = pb.start();
            p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

//    public static DSE getDseInstance(String exampleName,
//                                     String hierachryName) {
//        Properties props = new Properties();
//        props.setProperty("dse.dp", "z3");
//        props.setProperty("dse.executor", "../executor.sh");
//        props.setProperty("dse.executor.args", "-cp src/test/resources/examples/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true " + exampleName+".java");
//        props.setProperty("dse.dp.incremental", "false");
//        props.setProperty("dse.terminate.on", "completion");
//        props.setProperty("dse.explore", "BFS");
//        props.setProperty("static.info", "src/test/resources/hierarchy/"+hierachryName+".txt");
//
//        Config config = Config.fromProperties(props);
//
//        return  new DSE(config);
//    }

    public static DSE getDseInstance(String exampleName,
                                     String pathToExamples) {
        String jarWildCard = pathToExamples+"*";
        String libJarWildCard = pathToExamples+"libs/*";
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", String.format("-cp %s:%s:%s:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true %s", pathToExamples, jarWildCard, libJarWildCard, exampleName));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("concolic.max.object.annotation.depth", "3");
        props.setProperty("dse.timeout", "900");
        if (BLUEPRINT_CACHE_ENABLED) {
            props.setProperty("dse.constructor.blueprint.cache", "true");
        }

        Config config = Config.fromProperties(props);

        return  new DSE(config);
    }

    public static DSE getDseBaseLineInstance(String exampleName,
                                     String pathToExamples) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", String.format("-cp %s:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true -Dconcolic.object.factories=true %s", pathToExamples, exampleName));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("concolic.max.object.annotation.depth", "3");
        props.setProperty("dse.timeout", "900");
        if (BLUEPRINT_CACHE_ENABLED) {
            props.setProperty("dse.constructor.blueprint.cache", "true");
        }

        Config config = Config.fromProperties(props);

        return  new DSE(config);
    }

//    public static DSE getDseInstance(String exampleName,
//                                    String pathToClassHierachy,
//                                    String pathToExamples) {
//        Properties props = new Properties();
//        props.setProperty("dse.dp", "z3");
//        props.setProperty("dse.executor", "../executor.sh");
//        props.setProperty("dse.executor.args", String.format("-cp %s:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true %s", pathToExamples, exampleName));
//        props.setProperty("dse.dp.incremental", "false");
//        props.setProperty("dse.terminate.on", "completion");
//        props.setProperty("dse.explore", "BFS");
//        props.setProperty("static.info", pathToClassHierachy);
//
//        Config config = Config.fromProperties(props);
//
//        return  new DSE(config);
//    }

//    public static DSE getDseInstance(String exampleName,
//                                     String pathToClassHierarchy) {
//        Properties props = new Properties();
//        props.setProperty("dse.dp", "z3");
//        props.setProperty("dse.executor", "../executor.sh");
//        props.setProperty("dse.executor.args", "-cp ../examples_concolic/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true " + exampleName);
//        props.setProperty("dse.dp.incremental", "false");
//        props.setProperty("dse.terminate.on", "completion");
//        props.setProperty("dse.explore", "BFS");
//        props.setProperty("static.info", pathToClassHierarchy);
//
//        Config config = Config.fromProperties(props);
//
//        return  new DSE(config);
//    }

    public static List<String> getDecisionTreeLineByLine(String wholeLogs) {
        return Arrays.stream(wholeLogs.split("\\R"))
                .map(String::trim)
                .dropWhile(line -> !line.contains("decision tree of the analysed program:"))
                .filter(line -> line.startsWith("+"))
                .collect(Collectors.toList());
    }

    public static boolean decisionTreeContainsAssertionViolation(List<String> decisionTreeLines) {
        return decisionTreeLines.stream().anyMatch(line -> line.contains("java/lang/AssertionError"));
    }

    public static boolean validAssert(List<String> decisionTreeLines) {
        return !decisionTreeContainsAssertionViolation(decisionTreeLines);
    }

    public static boolean decisionTreeContainsLanguageException(List<String> decisionTreeLines) {
        return decisionTreeLines.stream()
                .filter(line -> !line.contains("java/lang/AssertionError"))
                .anyMatch(line -> line.contains("ERROR"));
    }

    public static boolean noRuntimeException(List<String> decisionTreeLines) {
        return !decisionTreeContainsLanguageException(decisionTreeLines);
    }

    public static String getSetUpTime(String wholeLogs) {
        Pattern pattern = Pattern.compile("set-up-time:\\s*(\\d+)ms");
        return Arrays.stream(wholeLogs.split("\\R"))
                .map(String::trim)
                .map(pattern::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find set-up-time"));
    }

    public static Map<String, Long> analyseDecisionTree(List<String> decisionTreeLines) {
        // Define all expected keys
        List<String> allKeys = List.of(
                "OK",
                "ERROR",
                "UNSAT",
                "SKIPPED",
                "#EDGES_OBJECT_CONSTRUCTOR_VARIATION",
                "#EDGES_NORMAL_VARIATION",
                "UNKNOWN"
        );

        // Initialize map with all keys set to 0
        Map<String, Long> counts = new HashMap<>();
        for (String key : allKeys) {
            counts.put(key, 0L);
        }

        // Count occurrences with a simple loop
        for (String s : decisionTreeLines) {
            String key;
            if (s.startsWith("+ OK")) {
                key = "OK";
            } else if (s.startsWith("+ ERROR")) {
                key = "ERROR";
            } else if (s.startsWith("+ UNSAT")) {
                key = "UNSAT";
            } else if (s.startsWith("+ SKIPPED")) {
                key = "SKIPPED";
            } else if (s.contains("__object_constructor_")) {
                key = "#EDGES_OBJECT_CONSTRUCTOR_VARIATION";
            } else if (s.matches("^\\+ \\d+.*")) {
                key = "#EDGES_NORMAL_VARIATION";
            } else {
                key = "UNKNOWN";
            }

            counts.put(key, counts.get(key) + 1);
        }

        // Apply your additional calculations
        counts.put("#EDGES_NORMAL_VARIATION",
                counts.get("#EDGES_NORMAL_VARIATION") - counts.get("SKIPPED") * 2);

        counts.put("#PATHS",
                counts.get("OK") + counts.get("ERROR") + counts.get("UNSAT"));

        counts.put("#EXECUTED_PATHS",
                counts.get("OK") + counts.get("ERROR"));

        counts.put("#EDGES",
                counts.get("#EDGES_OBJECT_CONSTRUCTOR_VARIATION") + counts.get("#EDGES_NORMAL_VARIATION"));

        return counts;

    }

}
