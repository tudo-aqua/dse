package tools.aqua.dse.preprocessing;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SummaryStoreTest {

    private final List<String> generated = new ArrayList<>();

    private SummaryStore store() {
        generated.clear();
        return new SummaryStore(List.of("null|NULL", "LB;|(I)V|{}", "LB;|(I)V|{}", "LC;|()V|"), sig -> {
            generated.add(sig);
            return switch (sig) {
                case "null|NULL" -> new GeneratedSummary(sig,
                        List.of("(and (= __object_0.init \"null|NULL\") (= __object_0 null))"), Set.of(), List.of());
                case "LB;|(I)V|{}" -> new GeneratedSummary(sig,
                        List.of("(and (= __object_0.init \"<>LB;|(I)V|{__object_0__int_0}\") (= __object_0.x __object_0__int_0))"),
                        Set.of("(declare-fun __object_0__int_0 () (_ BitVec 32))"), List.of());
                default -> new GeneratedSummary(sig, List.of(), Set.of(), List.of("__object_0.inner"));
            };
        });
    }

    private static ConstructorSignature sig(String s) {
        return ConstructorSignature.parse(s);
    }

    @Test
    void catalogueIsDeduplicated() {
        assertThat(store().catalogue()).extracting(ConstructorSignature::signature)
                .containsExactly("null|NULL", "LB;|(I)V|{}", "LC;|()V|");
    }

    @Test
    void summarizesLazilyAndOnlyOnce() {
        SummaryStore store = store();
        assertThat(store.summarizedCount()).isZero();
        assertThat(store.ensureSummarized(List.of(sig("null|NULL")))).isEqualTo(1);
        assertThat(store.ensureSummarized(List.of(sig("null|NULL"), sig("LB;|(I)V|{}")))).isEqualTo(1);
        assertThat(generated).containsExactly("null|NULL", "LB;|(I)V|{}");
        assertThat(store.unsummarized(store.catalogue())).extracting(ConstructorSignature::signature)
                .containsExactly("LC;|()V|");
        assertThat(store.declarations()).containsExactly("(declare-fun __object_0__int_0 () (_ BitVec 32))");
    }

    @Test
    void blueprintContainsOnlyRequestedSummarizedSignatures() {
        SummaryStore store = store();
        store.ensureSummarized(List.of(sig("null|NULL"), sig("LB;|(I)V|{}")));
        assertThat(store.blueprint(List.of(sig("LB;|(I)V|{}"), sig("LC;|()V|"))))
                .isEqualTo("(and (= __object_0.init \"<>LB;|(I)V|{__object_0__int_0}\") (= __object_0.x __object_0__int_0))");
        assertThat(store.blueprint(List.of(sig("LC;|()V|")))).isEqualTo("false");
    }

    @Test
    void signaturesWithoutFeasiblePathsAreSummarizedButContributeNothing() {
        SummaryStore store = store();
        store.ensureSummarized(List.of(sig("LC;|()V|")));
        assertThat(store.isSummarized(sig("LC;|()V|"))).isTrue();
        assertThat(store.blueprint(List.of(sig("LC;|()V|")))).isEqualTo("false");
        assertThat(store.objects()).containsExactly("__object_0.inner");
    }

    @Test
    void rejectsUnknownSignatures() {
        assertThatThrownBy(() -> store().ensureSummarized(List.of(sig("LD;|()V|"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
