import tools.aqua.concolic.Verifier;

public class Example3 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();

        if (o == null) {
            assert false;
        }
    }
}
