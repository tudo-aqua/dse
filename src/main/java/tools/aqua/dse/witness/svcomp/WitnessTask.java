package tools.aqua.dse.witness.svcomp;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import tools.aqua.dse.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class WitnessTask {
    public List<String> inputFiles;
    public Map<String,String> inputFileHashes;
    public String specification;
    public String dataModel = "LP64";
    public String language = "Java";

    private static String computeHash(String s){
        DigestUtils digestUtils = new DigestUtils(MessageDigestAlgorithms.SHA_256);
        try {
            return digestUtils.digestAsHex(Path.of(s).toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> computeHashes(List<String> inputFiles) {
        Map<String, String> hashes = new HashMap<>();
        for (String file : inputFiles) {
            hashes.put(file, computeHash(file));
        }
        return hashes;
    }

    private static boolean javaFileFilter(String p){
        boolean result = p.endsWith(".java");
        return result;
    }

    private static List<String> extractJavaFiles(Path sourceFolder){
       List<String> files;
       try (Stream<Path> stream = Files.walk(sourceFolder)){
            files = stream.filter(Files::isRegularFile).map(Path::toString).filter(p->p.endsWith(".java")).toList();
       } catch (IOException e) {
           throw new RuntimeException(e);
       }
       return files;
    }

    private static String convertTermination(int term){
        return switch (term){
            case Config.TERMINATE_ON_ASSERTION_VIOLATION -> "CHECK( init(Main.main()), LTL(G assert) )";
            case Config.TERMINATE_ON_ERROR -> "CHECK(init(Main.main()), LTL(G ! uncaught(java.lang.RuntimeException)))";
            default -> "The specification is unknown to dse";
        };
    }

    public static WitnessTask createTask(Config config) {
        WitnessTask wt = new WitnessTask();
        wt.inputFiles = new ArrayList<>();
        for (String path: config.getTaskSources()){
            wt.inputFiles.addAll(extractJavaFiles(Path.of(path)));
        }
        wt.inputFileHashes = computeHashes(wt.inputFiles);
        wt.specification = convertTermination(config.getTermination());
        return  wt;
    }
}
