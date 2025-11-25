import tools.aqua.concolic.Verifier;

public class Example37 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        a.getSub().bar(); 
    }
}
