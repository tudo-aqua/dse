import tools.aqua.concolic.Verifier;

/* Reads through aliases: with p.a == p.b, p.a.x > 0 implies p.b.x > 0. */
public class Main {
    public static void main(String[] args) {
        BoxPair p = Verifier.nondetObject(BoxPair.class, null);
        if (p != null && p.a != null && p.b != null && p.a.x > 0) {
            if (p.b.x > 0) {
                assert false;
            } else {
                throw new IllegalStateException();   // only with distinct boxes
            }
        }
    }
}
