package tools.aqua.dse;

import java.io.IOException;

public class ExamplePrinter {
    public static void printExample(String exampleName
    ) {
        String filePath = exampleName + ".java";
        ProcessBuilder pb = new ProcessBuilder("bat", "--color=always", String.format("../examples_concolic/%s", filePath));
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
}
