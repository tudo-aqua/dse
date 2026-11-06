package tools.aqua.dse.preprocessing;

import java.util.List;
import java.util.Set;

/**
 * The summary of one constructor signature, templated on {@code __object_0}.
 *
 * @param signature    the summarized constructor signature.
 * @param formulas     one SMT-LIB conjunction {@code (and ...)} per feasible path through the constructor.
 * @param declarations SMT-LIB declarations required by the formulas.
 * @param objects      identifiers of objects created within the summarized constructor (nested objects).
 */
public record GeneratedSummary(String signature,
                               List<String> formulas,
                               Set<String> declarations,
                               List<String> objects) {

    public GeneratedSummary {
        formulas = List.copyOf(formulas);
        declarations = Set.copyOf(declarations);
        objects = List.copyOf(objects);
    }
}
