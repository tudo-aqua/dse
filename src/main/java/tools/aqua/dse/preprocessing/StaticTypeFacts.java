package tools.aqua.dse.preprocessing;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The program-specific static knowledge used to evaluate path-constraint atoms without a solver: the
 * {@code obj.extends} relation, the {@code obj.method.of} table, and the errors each constructor signature may
 * raise.
 *
 * <p>The relations must be <em>exactly</em> the ones asserted to the SMT solver (see
 * {@link StaticManager#generateStaticSmtLibCode()}), because {@link SummaryRelevance} uses them to decide atoms
 * that the solver would decide the same way.
 */
public final class StaticTypeFacts {

    private final Set<List<String>> extendsPairs = new HashSet<>();
    private final Set<List<String>> methodOf = new HashSet<>();
    private final Map<String, ConstructorErrors> errors = new HashMap<>();

    /**
     * @param extendsPairs pairs {@code {sub, super}} of {@code obj.extends}.
     * @param methodOf     the {@code obj.method.of} table.
     * @param errors       errors per constructor (key: signature of a <em>single</em> constructor call, i.e.,
     *                     {@code Class|Descriptor}, without arguments); missing entries are treated as
     *                     {@link ConstructorErrors#UNKNOWN}.
     */
    public StaticTypeFacts(Collection<String[]> extendsPairs,
                           Collection<Opal.PolymorphyInformation> methodOf,
                           Map<String, ConstructorErrors> errors) {
        for (String[] pair : extendsPairs) {
            this.extendsPairs.add(List.of(pair[0], pair[1]));
        }
        for (Opal.PolymorphyInformation info : methodOf) {
            this.methodOf.add(List.of(info.accessingClass(), info.methodName(),
                    info.methodDescriptor(), info.declaringClass()));
        }
        this.errors.putAll(errors);
    }

    /** Value of {@code (obj.extends sub sup)} as defined in the SMT encoding. */
    public boolean extendsRel(String sub, String sup) {
        return extendsPairs.contains(List.of(sub, sup));
    }

    /** Value of {@code (obj.method.of cls name desc declaring)} as defined in the SMT encoding. */
    public boolean methodOf(String cls, String name, String desc, String declaring) {
        return methodOf.contains(List.of(cls, name, desc, declaring));
    }

    /**
     * Errors that may escape a (possibly nested) constructor call: the union over the root constructor and all
     * nested constructor calls in the signature. {@code null|NULL} raises no error.
     */
    public ConstructorErrors errorsOf(ConstructorSignature signature) {
        if (signature.isNull()) {
            return ConstructorErrors.NONE;
        }
        ConstructorErrors result = errors.getOrDefault(constructorKey(signature), ConstructorErrors.UNKNOWN);
        for (ConstructorSignature nested : signature.nestedConstructors()) {
            result = result.union(errors.getOrDefault(constructorKey(nested), ConstructorErrors.UNKNOWN));
        }
        return result;
    }

    public static String constructorKey(ConstructorSignature signature) {
        return signature.rootClass() + "|" + signature.descriptor();
    }
}
