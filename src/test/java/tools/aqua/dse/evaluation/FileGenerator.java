package tools.aqua.dse.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class generates a series of Java files for scaling tests.
 * Each generated file contains a class with a 'main' method,
 * in which an incrementally increasing number of objects is declared.
 */
public class FileGenerator {

    /**
     * Creates a specified number of Java files in a subdirectory
     * of the current project directory.
     *
     * @param numberOfFiles The total number of files to create.
     * @param targetDirectory  The relative path to the target folder, e.g., "src/main/generated".
     */
    public static void createScalingJavaFiles(int numberOfFiles, Path targetDirectory) {


        System.out.println("Files will be created in the following directory: " + targetDirectory);

        try {
            // Ensure that the target directory exists.
            Files.createDirectories(targetDirectory);

            // Loop to create each file individually.
            for (int i = 1; i <= numberOfFiles; i++) {
                // Generate the class name (e.g., "ScalingObjects3").
                String className = "ScalingNonDetObjects" + i;
                // Generate the file name (e.g., "ScalingExample3.java").
                String fileName = className+ ".java";

                // Dynamically build the content of the Java file.
                StringBuilder fileContent = new StringBuilder();
                fileContent.append("import tools.aqua.concolic.Verifier;\n\n");
                fileContent.append("public class ").append(className).append(" {\n");
                fileContent.append("    public static void main(String[] args) {\n");

                // Add a new object declaration for each iteration.
                for (int j = 1; j <= i; j++) {
                    fileContent.append("        Object o").append(j)
                            .append(" = Verifier.nondetObject();\n");
                }

                fileContent.append("    }\n");
                fileContent.append("}\n");

                // Create the full path to the new file.
                Path filePath = targetDirectory.resolve(fileName);
                // Write the generated content to the file.
                Files.writeString(filePath, fileContent.toString());

                System.out.println("File created: " + filePath);
            }
            System.out.println("\nSuccessfully created " + numberOfFiles + " files.");

        } catch (IOException e) {
            System.err.println("An error occurred while creating the files:");
            e.printStackTrace();
        }
    }



    /**
     * Example usage of the method.
     */
    public static void main(String[] args) {
//        // Define how many files should be created.
//        int totalFilesToCreate = 2;
//
//        // Specify the target folder relative to the project root.
//        // The folders will be created automatically if they don't exist.
//        String outputFolder = "generated-files/scaling-examples";
//
//        createScalingJavaFiles(totalFilesToCreate, Path.of("src/test/resources/examples"));
    }

}
