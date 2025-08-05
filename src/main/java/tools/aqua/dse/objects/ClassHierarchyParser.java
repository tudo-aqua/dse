package tools.aqua.dse.objects;
//
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//public class ClassHierarchyParser {
//
//    // superclass → list of DIRECT Subclasses
//    private final Map<String, List<String>> subclassMap = new HashMap<>();
//
//    public void parse(String input) {
//        // regukar expression: class <NAME>; [extends <NAME>;]
//        Pattern pattern = Pattern.compile("class (\\S+?);(?: extends (\\S+?);)?");
//
//        Scanner scanner = new Scanner(input);
//
//        while (scanner.hasNextLine()) {
//            String line = scanner.nextLine().trim();
//
//            Matcher matcher = pattern.matcher(line);
//            if (matcher.find()) {
//                String className = matcher.group(1) + ";";
//                String superClass = matcher.group(2);
//                if (superClass != null) {
//                    superClass += ";";
//                    subclassMap
//                            .computeIfAbsent(superClass, k -> new ArrayList<>())
//                            .add(className);
//                }
//            }
//        }
//    }
//
//    public Set<String> getAllSubclasses(String className) {
//        Set<String> result = new HashSet<>();
//        Deque<String> stack = new ArrayDeque<>();
//        stack.push(className);
//
//        while (!stack.isEmpty()) {
//            String current = stack.pop();
//            List<String> children = subclassMap.getOrDefault(current, Collections.emptyList());
//            for (String child : children) {
//                if (result.add(child)) {
//                    stack.push(child);
//                }
//            }
//        }
//
//        return result;
//    }
//
//    public static void main(String[] args) {
////        String input = "class LA; { LA;|()V, LA;|(II)V } \n" +
////                "class LB; extends LA; { LB;|()V } \n" +
////                "class LC; extends LB; { LC;|()V } \n" +
////                "class LD; extends LA; { LD;|()V } \n" +
////                "class LE; { LE;|()V } ";
//
//        String input = "class LA; { LA;|()V, LA;|(II)V}\n" +
//                "class LB; extends LA;{ LB;|()V}";
//
//        ClassHierarchyParser parser = new ClassHierarchyParser();
//        parser.parse(input);
//
//        String targetClass = "LA;";
//        Set<String> subclasses = parser.getAllSubclasses(targetClass);
//        System.out.println("Subklassen von " + targetClass + ": " + subclasses);
//        String targetClassB = "LB;";
//        Set<String> subclassesB = parser.getAllSubclasses(targetClassB);
//        System.out.println("Subklassen von " + targetClassB + ": " + subclassesB);
//    }
//}
//
//

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
//        String input = "class LA; { LA;|()V, LA;|(II)V } \n" +
//                "class LB; extends LA; { LB;|()V } \n" +
//                "class LC; extends LB; { LC;|()V } \n" +
//                "class LD; extends LA; { LD;|()V } \n" +
//                "class LE; { LE;|()V } ";
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

