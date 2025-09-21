import tools.aqua.concolic.Verifier;

public class Example17 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        if (a.getX() > 0) {
            assert false;
        }
    }
}
