import tools.aqua.concolic.Verifier;

/* An element of an array of an input object, read through a symbolic index. */
public class Main {
    public static void main(String[] args) {
        H h = Verifier.nondetObject(H.class, null);
        int i = Verifier.nondetInt();
        if (h != null && h.a != null && 0 <= i && i < h.a.length) {
            if (h.a[i] == 7) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
