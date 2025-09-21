import tools.aqua.concolic.Verifier;

public class Example0 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject();
        a.getX(); 
    }
}
