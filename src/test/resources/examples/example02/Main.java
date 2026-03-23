import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(A.class, null);
		B b = (B) o;
    }
}
