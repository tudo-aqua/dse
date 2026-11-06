package tools.aqua.dse.preprocessing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue of all constructor signatures (computed statically, eagerly) together with the summaries computed so
 * far (lazily, on demand). Summaries are global and monotone: once a signature is summarized, its summary is
 * available to every later query and every object.
 */
public final class SummaryStore {

    /** Computes the summary of a single constructor signature (e.g., by running DSE on it). */
    @FunctionalInterface
    public interface Generator {
        GeneratedSummary generate(String signature);
    }

    private final Map<String, ConstructorSignature> catalogue = new LinkedHashMap<>();
    private final Map<String, GeneratedSummary> summaries = new LinkedHashMap<>();
    private final Set<String> declarations = new LinkedHashSet<>();
    private final Set<String> objects = new LinkedHashSet<>();
    private final Generator generator;

    /**
     * @param signatures all constructor signatures (duplicates are ignored, order is kept).
     * @param generator  used to compute missing summaries.
     */
    public SummaryStore(Collection<String> signatures, Generator generator) {
        for (String s : signatures) {
            catalogue.computeIfAbsent(s, ConstructorSignature::parse);
        }
        this.generator = generator;
    }

    public Collection<ConstructorSignature> catalogue() {
        return Collections.unmodifiableCollection(catalogue.values());
    }

    public boolean isSummarized(ConstructorSignature signature) {
        return summaries.containsKey(signature.signature());
    }

    public int summarizedCount() {
        return summaries.size();
    }

    /** @return the signatures from {@code signatures} that have not been summarized yet, order preserved. */
    public List<ConstructorSignature> unsummarized(Collection<ConstructorSignature> signatures) {
        return signatures.stream().filter(s -> !isSummarized(s)).collect(Collectors.toList());
    }

    /**
     * Computes the summaries of all given signatures that are not summarized yet.
     *
     * @return the number of newly computed summaries.
     */
    public int ensureSummarized(Collection<ConstructorSignature> signatures) {
        int count = 0;
        for (ConstructorSignature s : signatures) {
            if (!catalogue.containsKey(s.signature())) {
                throw new IllegalArgumentException("Signature not in catalogue: " + s.signature());
            }
            if (isSummarized(s)) {
                continue;
            }
            GeneratedSummary summary = generator.generate(s.signature());
            summaries.put(s.signature(), summary);
            declarations.addAll(summary.declarations());
            objects.addAll(summary.objects());
            count++;
        }
        return count;
    }

    /**
     * Disjunction body (without the enclosing {@code (or ...)}) over all summary paths of the given signatures
     * that are summarized, templated on {@code __object_0}. Returns {@code "false"} if there is none, so that
     * {@code (or false)} correctly encodes "no constructor available".
     */
    public String blueprint(Collection<ConstructorSignature> signatures) {
        List<String> formulas = new ArrayList<>();
        for (ConstructorSignature s : signatures) {
            GeneratedSummary summary = summaries.get(s.signature());
            if (summary != null) {
                formulas.addAll(summary.formulas());
            }
        }
        return formulas.isEmpty() ? "false" : String.join("\n", formulas);
    }

    /** @return the summaries computed so far among {@code signatures}, in the given order. */
    public List<GeneratedSummary> summaries(Collection<ConstructorSignature> signatures) {
        List<GeneratedSummary> result = new ArrayList<>();
        for (ConstructorSignature s : signatures) {
            GeneratedSummary summary = summaries.get(s.signature());
            if (summary != null) {
                result.add(summary);
            }
        }
        return result;
    }

    /** @return declarations (templated on {@code __object_0}) required by all summaries computed so far. */
    public Set<String> declarations() {
        return Collections.unmodifiableSet(declarations);
    }

    /** @return identifiers of nested objects occurring in the summaries computed so far. */
    public Set<String> objects() {
        return Collections.unmodifiableSet(objects);
    }
}
