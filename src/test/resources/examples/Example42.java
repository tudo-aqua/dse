import tools.aqua.concolic.Verifier;

public class Example42 {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(); 

        a.getSub().bar(); 
    }
}
