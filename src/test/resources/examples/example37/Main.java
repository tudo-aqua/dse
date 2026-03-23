import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        A a = (A) Verifier.nondetObject(A.class, null);

        a.getSub().bar(); 
    }
}
