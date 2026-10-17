import tools.aqua.concolic.Verifier;
/* The content of a string field, set by the constructor. */
public class Main {
    public static void main(String[] args) {
        H h = Verifier.nondetObject(H.class, null);
        if (h != null && h.name != null) {
            if (h.name.contains("b")) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
