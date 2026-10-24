import tools.aqua.concolic.Verifier;

/* The content of a builder in a field, set by the constructor. */
public class Main {
    public static void main(String[] args) {
        H h = Verifier.nondetObject(H.class, null);
        if (h != null && h.sb != null) {
            if (h.sb.toString().contains("b")) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
