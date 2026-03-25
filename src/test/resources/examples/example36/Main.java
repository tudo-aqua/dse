import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        A a = Verifier.nondetObject(A.class, new Factories.AFactory());

        a.getSub().bar(); 
    }
}
