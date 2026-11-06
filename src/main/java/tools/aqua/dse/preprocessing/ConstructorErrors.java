package tools.aqua.dse.preprocessing;

import java.util.Set;
import java.util.TreeSet;

/**
 * Statically inferred errors (exception classes, JVM internal names such as {@code java/lang/AssertionError})
 * that may escape a constructor call.
 *
 * @param types    exception classes that may escape.
 * @param complete {@code true} if {@code types} is known to contain every exception that may escape. If
 *                 {@code false}, other (unknown) exceptions are possible as well.
 */
public record ConstructorErrors(Set<String> types, boolean complete) {

    public static final ConstructorErrors NONE = new ConstructorErrors(Set.of(), true);

    /** Nothing is known: any error may occur. */
    public static final ConstructorErrors UNKNOWN = new ConstructorErrors(Set.of(), false);

    public ConstructorErrors {
        types = Set.copyOf(types);
    }

    public ConstructorErrors union(ConstructorErrors other) {
        Set<String> all = new TreeSet<>(types);
        all.addAll(other.types);
        return new ConstructorErrors(all, complete && other.complete);
    }
}
