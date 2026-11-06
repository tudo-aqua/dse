import tools.aqua.concolic.Verifier;

/* An append through one field is visible through the other only if both refer to the same builder. */
public class Main {
    public static void main(String[] args) {
        P p = Verifier.nondetObject(P.class, null);
        if (p != null && p.a != null && p.b != null) {
            p.a.append("y");
            if (p.b.toString().equals("xy")) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
