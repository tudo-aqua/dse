import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Component o = Verifier.nondetObject(Component.class, null);
        check(o);
    }

    public static void check(Component o) {
        try {
            java.util.Objects.requireNonNull(o);
        } catch (Exception e) {
            assert false;
        }
    }
}
