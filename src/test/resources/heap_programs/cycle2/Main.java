import tools.aqua.concolic.Verifier;

/* Cycle of length 2: r.next.next == r only with Ring(). */
public class Main {
    public static void main(String[] args) {
        Ring r = Verifier.nondetObject(Ring.class, null);
        if (r != null && r.next != null) {
            if (r.next.next == r) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
