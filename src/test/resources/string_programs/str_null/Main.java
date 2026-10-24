import tools.aqua.concolic.Verifier;
/* A string field that is null or not. */
public class Main {
    public static void main(String[] args) {
        H h = Verifier.nondetObject(H.class, null);
        if (h != null) {
            if (h.name == null) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
