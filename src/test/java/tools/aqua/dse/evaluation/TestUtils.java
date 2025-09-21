package tools.aqua.dse.evaluation;

import tools.aqua.dse.Config;
import tools.aqua.dse.DSE;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class TestUtils {
    /**
     * Executes the compilation command for a given Java class.
     * The paths are relative to the project root directory.
     * @param className The name of the class to compile (without .java extension).
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if the current thread is interrupted while waiting for the process to complete.
     */
    public static void compileClass(String className) throws IOException, InterruptedException {
        // Paths relative to the project root directory
        String espressoPath = "SPouT/sdk/mxbuild/darwin-aarch64/GRAALVM_ESPRESSO_NATIVE_CE_JAVA17/graalvm-espresso-native-ce-java17-22.2.0.1-dev/bin/javac";
        String verifierStub = "verifier-stub/target/verifier-stub-1.0.jar";
        String examplesPath = "examples_concolic";

        // Assemble the classpath. The separator is ':' for macOS/Linux.
        String classPath = verifierStub + File.pathSeparator + examplesPath;

        // The source file to be compiled
        String sourceFile = examplesPath + File.separator + className + ".java";

        System.out.println("Starting compilation of: " + sourceFile);

        // Create a ProcessBuilder for the external call
        ProcessBuilder processBuilder = new ProcessBuilder(
                espressoPath,
                "-cp",
                classPath,
                sourceFile
        );

        // Set the working directory to the project root (optional, but good practice).
        // Adjust the path if necessary, or remove this line if it works without it.
        processBuilder.directory(new File("/Users/marvin.lazar/IdeaProjects/master-thesis-gdart/"));

        // Merge the error and standard output streams
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read and print the process output (important for debugging)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        // Wait for the process to finish and check the exit code
        int exitCode = process.waitFor();
        System.out.println("Compilation finished with exit code: " + exitCode);

        // Check if the compilation was successful (exit code 0)
        if (exitCode != 0) {
            org.junit.jupiter.api.Assertions.fail("Compilation failed with exit code " + exitCode);
        }
    }

    public static void printExample(String exampleName
    ) {
        String filePath = exampleName + ".java";
        ProcessBuilder pb = new ProcessBuilder("bat", "--color=always", String.format("src/test/resources/examples/%s", filePath));
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
//        props.setProperty("dse.executor.args", "-cp dse/src/test/resources/examples/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true " + exampleName+".java");
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
                                     String hierachryName) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        // Der Classpath wurde hier geändert
        props.setProperty("dse.executor.args", "-cp src/test/resources/examples/:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true " + exampleName);
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("static.info", "src/test/resources/hierarchy/"+hierachryName+".txt");

        Config config = Config.fromProperties(props);

        return  new DSE(config);
    }

    public static DSE getDseInstance(String exampleName,
                                    String pathToClassHierachy,
                                    String pathToExamples) {
        Properties props = new Properties();
        props.setProperty("dse.dp", "z3");
        props.setProperty("dse.executor", "../executor.sh");
        props.setProperty("dse.executor.args", String.format("-cp %s:../verifier-stub/target/verifier-stub-1.0.jar -Dconcolic.execution=true %s", pathToExamples, exampleName));
        props.setProperty("dse.dp.incremental", "false");
        props.setProperty("dse.terminate.on", "completion");
        props.setProperty("dse.explore", "BFS");
        props.setProperty("static.info", pathToClassHierachy);

        Config config = Config.fromProperties(props);

        return  new DSE(config);
    }

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
                .filter(line -> line.startsWith("+"))
                .collect(Collectors.toList());
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
