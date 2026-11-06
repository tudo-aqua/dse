import tools.aqua.concolic.Verifier;

/* Self-loop: n.next == n only with Node(). */
public class Main {
    public static void main(String[] args) {
        Node n = Verifier.nondetObject(Node.class, null);
        if (n != null) {
            if (n.next == n) {
                assert false;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
