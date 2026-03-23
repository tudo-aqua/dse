import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        A a1 = Verifier.nondetObject(A.class, null);
        A a2 = Verifier.nondetObject(A.class, null);

        if (a1.getX() <= a2.getY()) {
            assert false;
        }
    }
}
