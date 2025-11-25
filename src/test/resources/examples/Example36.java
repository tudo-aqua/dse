import tools.aqua.concolic.Verifier;

public class Example36 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        a.foo(); 
    }
}
