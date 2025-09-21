import tools.aqua.concolic.Verifier;

public class Example15 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        if (a.getX() < 0) {
            assert false;
        }
    }
}
