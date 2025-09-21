import tools.aqua.concolic.Verifier;

public class Example40 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        a.foo(); 
    }
}
