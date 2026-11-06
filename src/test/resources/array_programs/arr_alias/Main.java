import tools.aqua.concolic.Verifier;

/* A write to an array through one field is visible through the other only if both refer to the same array. */
public class Main {
    public static void main(String[] args) {
        P p = Verifier.nondetObject(P.class, null);
        if (p != null && p.a != null && p.b != null && p.a.length > 0 && p.b.length > 0) {
            p.a[0] = 5;
            if (p.b[0] == 5) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
