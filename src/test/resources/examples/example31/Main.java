import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        A a1 = Verifier.nondetObject(A.class, new Factories.AFactory());
        A a2 = Verifier.nondetObject(A.class, new Factories.AFactory());

        if (a1.getX() > 5 && (a1.getX() < a2.getY() || a1.getY() == a2.getX())) {
            assert false;
        }
    }
}
