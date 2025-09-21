import tools.aqua.concolic.Verifier;

public class Example4 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();

        if (o != null) {
            assert false;
        }
    }
}