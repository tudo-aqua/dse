import tools.aqua.concolic.Verifier;

public class Example39 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        o.foo(); 
    }
}
