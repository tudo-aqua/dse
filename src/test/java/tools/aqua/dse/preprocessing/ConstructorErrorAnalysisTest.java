package tools.aqua.dse.preprocessing;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the OPAL-based constructor error inference on small classes compiled at test time, using the
 * {@code java.base.jmod} of the JDK running the tests.
 */
public class ConstructorErrorAnalysisTest {

    private static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry("Plain", """
                    public class Plain {
                        int x;
                        public Plain() {}
                        public Plain(int x) { this.x = x; }
                    }"""),
            Map.entry("Asserting", """
                    public class Asserting {
                        public Asserting() { assert false; }
                    }"""),
            Map.entry("SubAsserting", """
                    public class SubAsserting extends Asserting {
                        public SubAsserting() { super(); }
                    }"""),
            Map.entry("Dividing", """
                    public class Dividing {
                        int r;
                        public Dividing(int a, int b) { r = a / b; }
                    }"""),
            Map.entry("Catching", """
                    public class Catching {
                        int r;
                        public Catching(int a) {
                            try { r = 10 / a; } catch (ArithmeticException e) { r = 0; }
                        }
                    }"""),
            Map.entry("Throwing", """
                    public class Throwing {
                        public Throwing(int a) { if (a < 0) throw new IllegalArgumentException(); }
                    }"""),
            Map.entry("Deref", """
                    public class Deref {
                        int h;
                        public Deref(Plain p) { h = p.x; }
                    }"""),
            Map.entry("Helper", """
                    public class Helper {
                        public Helper(int[] arr) { init(arr); }
                        private void init(int[] arr) { arr[0] = 1; }
                    }"""),
            Map.entry("Library", """
                    public class Library {
                        public Library() throws java.io.IOException {
                            java.nio.file.Files.readString(java.nio.file.Path.of("x"));
                        }
                    }"""),
            Map.entry("Parsing", """
                    public class Parsing {
                        int x;
                        public Parsing(String s) { x = Integer.parseInt(s); }
                    }"""),
            Map.entry("Native", """
                    public class Native {
                        long t;
                        public Native() { t = System.nanoTime(); }
                    }"""),
            Map.entry("Recursive", """
                    public class Recursive {
                        int r;
                        public Recursive(int n) { r = f(n); }
                        private int f(int n) { return n <= 0 ? 10 / n : f(n - 1); }
                    }"""),
            Map.entry("MutuallyRecursive", """
                    public class MutuallyRecursive {
                        int r;
                        public MutuallyRecursive(int[] a, int n) { r = g(a, n); }
                        private int g(int[] a, int n) { return n <= 0 ? 0 : h(a, n - 1); }
                        private int h(int[] a, int n) { return n == 7 ? a[n] : g(a, n); }
                    }"""));

    private static final Set<String> NONE = Set.of();

    private Opal opal;

    @BeforeClass
    void compileAndLoad() throws IOException {
        File jmod = new File(System.getProperty("java.home"), "jmods/java.base.jmod");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (!jmod.exists() || javac == null) {
            throw new SkipException("needs a JDK with jmods: " + jmod);
        }
        Path dir = Files.createTempDirectory("ctor-errors");
        List<String> args = new ArrayList<>(List.of("-d", dir.toString()));
        for (Map.Entry<String, String> e : SOURCES.entrySet()) {
            Path file = dir.resolve(e.getKey() + ".java");
            Files.writeString(file, e.getValue());
            args.add(file.toString());
        }
        assertThat(javac.run(null, null, null, args.toArray(new String[0]))).isZero();
        opal = new Opal(dir.toString(), jmod);
    }

    private ConstructorErrors errors(String signature, ConstructorErrorAnalysis.Mode mode) {
        ConstructorSignature s = ConstructorSignature.parse(signature);
        return opal.inferConstructorErrors(List.of(signature), mode).get(StaticTypeFacts.constructorKey(s));
    }

    private ConstructorErrors errors(String signature) {
        return errors(signature, ConstructorErrorAnalysis.Mode.DECLARED_AND_LANGUAGE);
    }

    @Test
    void plainConstructorsRaiseNothing() {
        assertThat(errors("LPlain;|()V|")).isEqualTo(new ConstructorErrors(NONE, true));
        // putfield on 'this' cannot raise a NullPointerException
        assertThat(errors("LPlain;|(I)V|{}")).isEqualTo(new ConstructorErrors(NONE, true));
    }

    @Test
    void assertionsAreFound() {
        assertThat(errors("LAsserting;|()V|"))
                .isEqualTo(new ConstructorErrors(Set.of("java/lang/AssertionError"), true));
    }

    @Test
    void superConstructorErrorsPropagate() {
        assertThat(errors("LSubAsserting;|()V|"))
                .isEqualTo(new ConstructorErrors(Set.of("java/lang/AssertionError"), true));
    }

    @Test
    void languageExceptionsAreFound() {
        assertThat(errors("LDividing;|(II)V|{}{}"))
                .isEqualTo(new ConstructorErrors(Set.of("java/lang/ArithmeticException"), true));
    }

    @Test
    void caughtExceptionsDoNotEscape() {
        assertThat(errors("LCatching;|(I)V|{}")).isEqualTo(new ConstructorErrors(NONE, true));
    }

    @Test
    void explicitThrowsAreFound() {
        assertThat(errors("LThrowing;|(I)V|{}"))
                .isEqualTo(new ConstructorErrors(Set.of("java/lang/IllegalArgumentException"), true));
    }

    @Test
    void dereferencingParametersMayRaiseNullPointerExceptions() {
        assertThat(errors("LDeref;|(LPlain;)V|{null|NULL}"))
                .isEqualTo(new ConstructorErrors(Set.of("java/lang/NullPointerException"), true));
    }

    @Test
    void calledMethodsAreAnalyzed() {
        assertThat(errors("LHelper;|([I)V|{}")).isEqualTo(new ConstructorErrors(
                Set.of("java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"), true));
    }

    @Test
    void languageExceptionsInLibraryCodeAreFound() {
        ConstructorErrors errors = errors("LParsing;|(Ljava/lang/String;)V|{null|NULL}");
        assertThat(errors.types()).contains("java/lang/NumberFormatException");
        assertThat(errors.complete()).isTrue();
    }

    @Test
    void nativeMethodsAreImprecise() {
        assertThat(errors("LNative;|()V|")).isEqualTo(new ConstructorErrors(NONE, true));
        assertThat(errors("LNative;|()V|", ConstructorErrorAnalysis.Mode.WITH_UNKNOWN).complete()).isFalse();
    }

    @Test
    void recursionIsAFixedPoint() {
        for (ConstructorErrorAnalysis.Mode mode : ConstructorErrorAnalysis.Mode.values()) {
            assertThat(errors("LRecursive;|(I)V|{}", mode))
                    .isEqualTo(new ConstructorErrors(Set.of("java/lang/ArithmeticException"), true));
            assertThat(errors("LMutuallyRecursive;|([II)V|{}{}", mode)).isEqualTo(new ConstructorErrors(
                    Set.of("java/lang/NullPointerException", "java/lang/ArrayIndexOutOfBoundsException"), true));
        }
    }

    @Test
    void largeLibraryClosuresStayTractable() {
        long start = System.currentTimeMillis();
        ConstructorErrors declared = errors("LLibrary;|()V|");
        ConstructorErrors unknown = errors("LLibrary;|()V|", ConstructorErrorAnalysis.Mode.WITH_UNKNOWN);
        System.out.println("[ConstructorErrorAnalysisTest] Files.readString: " + declared.types().size()
                + " exception types in " + (System.currentTimeMillis() - start) + "ms: " + declared.types());
        assertThat(declared.complete()).isTrue();
        assertThat(unknown.complete()).isFalse();
        // explicitly thrown java.lang.Errors (LinkageError, OutOfMemoryError, ...) are deliberately dropped
        assertThat(declared.types()).noneMatch(t -> t.endsWith("Error") && !t.equals("java/lang/AssertionError"));
        assertThat(unknown.types()).containsAll(declared.types());
    }

    @Test
    void nestedConstructorsAreKeyedSeparately() {
        Map<String, ConstructorErrors> all = opal.inferConstructorErrors(
                List.of("LDeref;|(LPlain;)V|{LPlain;|(I)V|{}}"), ConstructorErrorAnalysis.Mode.DECLARED_AND_LANGUAGE);
        assertThat(all).containsOnlyKeys("LDeref;|(LPlain;)V", "LPlain;|(I)V");
    }
}
