import tools.aqua.concolic.Verifier;

/* A local array written through a symbolic index: the element read depends on the index. */
public class Main {
    public static void main(String[] args) {
        int i = Verifier.nondetInt();
        int[] b = new int[3];
        b[i & 1] = 7;
        if (b[0] == 7) {
            assert false;
        } else {
            throw new IllegalStateException();
        }
    }
}
