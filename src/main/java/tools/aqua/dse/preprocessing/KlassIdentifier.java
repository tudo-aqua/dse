package tools.aqua.dse.preprocessing;

import java.util.Comparator;

public record KlassIdentifier(String shortIdentifier, String longIdentifier) {
    /**
     * Comparator that sorts KlassName objects first by shortIdentifier
     * and then by longIdentifier.
     */
    public static final Comparator<KlassIdentifier> COMPARATOR =
            Comparator.comparing(KlassIdentifier::shortIdentifier)
                    .thenComparing(KlassIdentifier::longIdentifier);
}
