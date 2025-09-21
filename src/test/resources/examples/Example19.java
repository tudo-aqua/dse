import tools.aqua.concolic.Verifier;

public class Example19 {
    public static void main(String[] args) {
        A a1 = (A) Verifier.nondetObject(); 
        A a2 = (A) Verifier.nondetObject(); 

        if (a1.getX() == a2.getX()) {
            assert false;
        }
    }
}
