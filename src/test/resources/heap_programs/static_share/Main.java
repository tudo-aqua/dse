import tools.aqua.concolic.Verifier;

/* Sharing of a static object between two nondet objects. */
public class Main {
    public static void main(String[] args) {
        Holder h1 = Verifier.nondetObject(Holder.class, null);
        Holder h2 = Verifier.nondetObject(Holder.class, null);
        if (h1 != null && h2 != null && h1.o != null) {
            if (h1.o == h2.o) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
