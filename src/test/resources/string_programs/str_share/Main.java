import tools.aqua.concolic.Verifier;
/* Identity of strings in fields: p.a == p.b only if the constructor shares the string. */
public class Main {
    public static void main(String[] args) {
        P p = Verifier.nondetObject(P.class, null);
        if (p != null && p.a != null) {
            if (p.a == p.b) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
