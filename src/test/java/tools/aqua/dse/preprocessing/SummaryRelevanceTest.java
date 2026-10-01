package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParser;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static exclusion on the class hierarchy of Fig. 2 of the paper:
 * <pre>
 * class A {}
 * class B extends A { B() { assert false; }  B(int x) {...}  void foo() {} }
 * class C extends B { C() { super(0); } }
 * class D extends B { D(int x) {...}  void foo() {} }
 * </pre>
 * Constraints are given in the format recorded by SPouT and parsed with the real SMT-LIB parser.
 */
public class SummaryRelevanceTest {

    private static final String DECLARATIONS = String.join("",
            "(declare-fun null () Int)",
            "(declare-fun obj.extends (String String) Bool)",
            "(declare-fun obj.method.of (String String String String) Bool)",
            "(declare-fun __object_0 () Int)",
            "(declare-fun __object_0.cls () String)",
            "(declare-fun __object_0.err () String)",
            "(declare-fun __object_0.x () (_ BitVec 32))",
            "(declare-fun __object_0.sub.cls () String)",
            "(declare-fun __object_1 () Int)",
            "(declare-fun __object_1.cls () String)",
            "(declare-fun __object_1.err () String)");

    // decisions as recorded along the path of Fig. 2
    private static final String BOUND_A = "(obj.extends __object_0.cls \"LA;\")";
    private static final String NO_ERROR = "(= __object_0.err \"\")";
    private static final String ASSERTION = "(= __object_0.err \"java/lang/AssertionError\")";
    private static final String INSTANCEOF_B = "(and (not (= __object_0 null)) (obj.extends __object_0.cls \"LB;\"))";
    private static final String NOT_INSTANCEOF_B = "(not " + INSTANCEOF_B + ")";
    private static final String FOO_IS_D = "(obj.method.of __object_0.cls \"foo\" \"()V\" \"LD;\")";
    private static final String X_POSITIVE = "(bvsgt __object_0.x #x00000000)";

    private static final List<String> SIGNATURES = List.of(
            "null|NULL", "LA;|()V|", "LB;|()V|", "LB;|(I)V|{}", "LC;|()V|", "LD;|(I)V|{}");

    private static final Map<String, ConstructorErrors> ERRORS = Map.of(
            "LA;|()V", ConstructorErrors.NONE,
            "LB;|()V", new ConstructorErrors(Set.of("java/lang/AssertionError"), true),
            "LB;|(I)V", ConstructorErrors.NONE,
            "LC;|()V", ConstructorErrors.NONE,
            "LD;|(I)V", ConstructorErrors.NONE);

    private static StaticTypeFacts facts(Map<String, ConstructorErrors> errors) {
        // same shape as Opal.extendsRelation: null edges, reflexive, transitive supertypes (Object excluded)
        List<String[]> ext = new ArrayList<>();
        Map<String, List<String>> supers = Map.of(
                "LA;", List.of("LA;"),
                "LB;", List.of("LB;", "LA;"),
                "LC;", List.of("LC;", "LB;", "LA;"),
                "LD;", List.of("LD;", "LB;", "LA;"));
        supers.forEach((sub, sups) -> {
            ext.add(new String[] {"null", sub});
            sups.forEach(sup -> ext.add(new String[] {sub, sup}));
        });
        List<Opal.PolymorphyInformation> methods = List.of(
                new Opal.PolymorphyInformation("LB;", "foo", "()V", "LB;"),
                new Opal.PolymorphyInformation("LC;", "foo", "()V", "LB;"),
                new Opal.PolymorphyInformation("LD;", "foo", "()V", "LD;"));
        return new StaticTypeFacts(ext, methods, errors);
    }

    private static List<Expression<Boolean>> path(String... decisions) throws Exception {
        List<Expression<Boolean>> result = new ArrayList<>();
        for (String d : decisions) {
            result.addAll(SMTLIBParser.parseSMTProgram(DECLARATIONS + "(assert " + d + ")").assertions);
        }
        return result;
    }

    private static List<ConstructorSignature> candidates() {
        return SIGNATURES.stream().map(ConstructorSignature::parse).toList();
    }

    private static List<String> needed(SummaryRelevance relevance, List<Expression<Boolean>> path) {
        return relevance.needed(path, SummaryRelevance.baseObjects(path), candidates())
                .stream().map(ConstructorSignature::signature).toList();
    }

    @Test
    void extractsTopLevelObjects() throws Exception {
        assertThat(SummaryRelevance.baseObjects(path(BOUND_A, "(= __object_0.sub.cls \"LA;\")", "(= __object_1 null)")))
                .containsExactly("__object_0", "__object_1");
    }

    @Test
    void instanceOfNeedsOnlySubclassConstructors() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, INSTANCEOF_B)))
                .containsExactlyInAnyOrder("LB;|()V|", "LB;|(I)V|{}", "LC;|()V|", "LD;|(I)V|{}");
    }

    @Test
    void negatedInstanceOfNeedsNullAndOtherClasses() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, NOT_INSTANCEOF_B)))
                .containsExactly("null|NULL", "LA;|()V|");
    }

    @Test
    void contradictoryTypeFactsNeedNothing() throws Exception {
        // checkcast B fails after instanceof B succeeded: UNSAT without any summary
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, INSTANCEOF_B, NOT_INSTANCEOF_B))).isEmpty();
    }

    @Test
    void errorBranchNeedsOnlyConstructorsRaisingTheError() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, ASSERTION))).containsExactly("LB;|()V|");
    }

    @Test
    void unknownErrorsAreNotExcluded() throws Exception {
        // no error information for D(int): it might raise the assertion error as well
        Map<String, ConstructorErrors> errors = new java.util.HashMap<>(ERRORS);
        errors.remove("LD;|(I)V");
        SummaryRelevance relevance = new SummaryRelevance(facts(errors));
        assertThat(needed(relevance, path(BOUND_A, ASSERTION))).containsExactlyInAnyOrder("LB;|()V|", "LD;|(I)V|{}");
        // ... but an unknown error is never the empty error
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, NOT_INSTANCEOF_B)))
                .containsExactly("null|NULL", "LA;|()V|");
    }

    @Test
    void polymorphicDispatchSelectsImplementingClasses() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, INSTANCEOF_B, FOO_IS_D))).containsExactly("LD;|(I)V|{}");
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, INSTANCEOF_B, "(not " + FOO_IS_D + ")")))
                .containsExactlyInAnyOrder("LB;|()V|", "LB;|(I)V|{}", "LC;|()V|");
    }

    @Test
    void fieldAndNestedConstraintsAreUnknown() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, INSTANCEOF_B, X_POSITIVE, "(= __object_0.sub.cls \"LD;\")")))
                .containsExactlyInAnyOrder("LB;|()V|", "LB;|(I)V|{}", "LC;|()V|", "LD;|(I)V|{}");
    }

    @Test
    void neededIsUnionOverObjects() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        List<Expression<Boolean>> p = path(BOUND_A, NO_ERROR, FOO_IS_D, "(= __object_1.err \"\")", "(= __object_1 null)");
        assertThat(relevance.relevantFor(p, "__object_0", candidates()))
                .extracting(ConstructorSignature::signature).containsExactly("LD;|(I)V|{}");
        assertThat(relevance.relevantFor(p, "__object_1", candidates()))
                .extracting(ConstructorSignature::signature).containsExactly("null|NULL");
        assertThat(needed(relevance, p)).containsExactlyInAnyOrder("null|NULL", "LD;|(I)V|{}");
    }

    @Test
    void signaturesDecidingTheTargetComeFirst() throws Exception {
        SummaryRelevance relevance = new SummaryRelevance(facts(ERRORS));
        // target is TRUE for B, C, D constructors, UNKNOWN for null and A() (depends on field x)
        assertThat(needed(relevance, path(BOUND_A, NO_ERROR, "(or " + INSTANCEOF_B + " " + X_POSITIVE + ")")))
                .containsExactly("LB;|()V|", "LC;|()V|", "LB;|(I)V|{}", "LD;|(I)V|{}", "null|NULL", "LA;|()V|");
        // without the no-error decision, the assertion-error outcome of B() is not excluded but undecided
        assertThat(needed(relevance, path(BOUND_A, NOT_INSTANCEOF_B)))
                .containsExactly("null|NULL", "LA;|()V|", "LB;|()V|");
        // target is UNKNOWN (field) for all -> simplest first among the remaining
        assertThat(needed(relevance, path(INSTANCEOF_B, X_POSITIVE)))
                .containsExactly("LB;|()V|", "LC;|()V|", "LB;|(I)V|{}", "LD;|(I)V|{}");
    }
}
