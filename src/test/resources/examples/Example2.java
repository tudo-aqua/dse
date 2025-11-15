import tools.aqua.concolic.Verifier;

public class Example2 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();

		B b = (B) o;

    }
}
