package tools.aqua.dse.objects;

import java.util.*;
import java.util.regex.*;

/**
 * A parser for a custom class hierarchy format.
 * <p>
 * Parses class definitions like:
 * <pre>
 * class LA; { LA;|()V }
 * class LB; extends LA; { LB;|()V }
 * </pre>
 * and allows querying all direct and indirect subclasses of a given class.
 */
public class ClassHierarchyParser {

    /**
     * Maps each superclass to a list of its direct subclasses.
     */
    private final Map<String, List<String>> subclassMap = new HashMap<>();

    /**
     * Parses the input text and builds the internal subclass mapping.
     *
     * @param input the string containing class definitions in the custom format
     */
    public void parse(String input) {
        // Match: class <CLASSNAME>; [extends <SUPERCLASSNAME>;]
        Pattern pattern = Pattern.compile("class (\\S+;)(?: extends (\\S+;))?");
        Scanner scanner = new Scanner(input);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String className = matcher.group(1);     // e.g., "LB;"
                String superClass = matcher.group(2);    // e.g., "LA;" (may be null)
                if (superClass != null) {
                    // Add className as a subclass of superClass
                    subclassMap
                            .computeIfAbsent(superClass, k -> new ArrayList<>())
                            .add(className);
                }
            }
        }
    }

    /**
     * Returns all direct and indirect subclasses of the given class.
     *
     * @param className the name of the class (e.g., "LA;")
     * @return a set of all subclasses (both direct and indirect)
     */
    public Set<String> getAllSubclasses(String className) {
        Set<String> result = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(className);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            List<String> children = subclassMap.getOrDefault(current, Collections.emptyList());
            for (String child : children) {
                // Only add if not already in the result set
                if (result.add(child)) {
                    stack.push(child); // Continue exploring this child
                }
            }
        }

        return result;
    }

    /**
     * Example usage.
     */
    public static void main(String[] args) {
        String input = "class LA; { LA;|()V, LA;|(II)V}\n" +
                "class LB; extends LA;{ LB;|()V}";

        ClassHierarchyParser parser = new ClassHierarchyParser();
        parser.parse(input);

        String[] classesToCheck = { "LA;", "LB;"};
        for (String cls : classesToCheck) {
            Set<String> subclasses = parser.getAllSubclasses(cls);
            System.out.println("Subclasses of " + cls + ": " + subclasses);
        }
    }
}

