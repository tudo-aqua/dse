package tools.aqua.dse.preprocessing;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstructorSignatureTest {

    @Test
    void parsesNull() {
        ConstructorSignature s = ConstructorSignature.parse("null|NULL");
        assertThat(s.isNull()).isTrue();
        assertThat(s.rootClass()).isEqualTo("null");
        assertThat(s.depth()).isEqualTo(0);
    }

    @Test
    void parsesFlatConstructors() {
        ConstructorSignature noArgs = ConstructorSignature.parse("LB;|()V|");
        assertThat(noArgs.rootClass()).isEqualTo("LB;");
        assertThat(noArgs.descriptor()).isEqualTo("()V");
        assertThat(noArgs.arguments()).isEmpty();
        assertThat(noArgs.depth()).isEqualTo(1);

        ConstructorSignature prim = ConstructorSignature.parse("LA;|(II)V|{}{}");
        assertThat(prim.arguments()).containsExactly("", "");
        assertThat(prim.depth()).isEqualTo(1);

        ConstructorSignature nullArg = ConstructorSignature.parse("LC;|(LA;)V|{null|NULL}");
        assertThat(nullArg.arguments()).containsExactly("null|NULL");
        assertThat(nullArg.depth()).isEqualTo(1);
        assertThat(nullArg.nestedConstructors()).isEmpty();
    }

    @Test
    void parsesNestedConstructors() {
        ConstructorSignature s = ConstructorSignature.parse("LC;|(ILA;)V|{}{LC;|(LA;)V|{LB;|(I)V|{}}}");
        assertThat(s.rootClass()).isEqualTo("LC;");
        assertThat(s.arguments()).containsExactly("", "LC;|(LA;)V|{LB;|(I)V|{}}");
        assertThat(s.depth()).isEqualTo(3);
        assertThat(s.nestedConstructors())
                .extracting(ConstructorSignature::signature)
                .containsExactly("LC;|(LA;)V|{LB;|(I)V|{}}", "LB;|(I)V|{}");
    }

    @Test
    void ordersSimplestFirst() {
        List<ConstructorSignature> sorted = List.of(
                        ConstructorSignature.parse("LC;|(LA;)V|{LB;|()V|}"),
                        ConstructorSignature.parse("LB;|(I)V|{}"),
                        ConstructorSignature.parse("null|NULL"),
                        ConstructorSignature.parse("LB;|()V|"))
                .stream().sorted(ConstructorSignature.SIMPLEST_FIRST).toList();
        assertThat(sorted).extracting(ConstructorSignature::signature)
                .containsExactly("null|NULL", "LB;|()V|", "LB;|(I)V|{}", "LC;|(LA;)V|{LB;|()V|}");
    }

    @Test
    void rejectsMalformedSignatures() {
        assertThatThrownBy(() -> ConstructorSignature.parse("<>LB;|()V|")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstructorSignature.parse("LB;")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstructorSignature.parse("LB;|(I)V|{")).isInstanceOf(IllegalArgumentException.class);
    }
}
