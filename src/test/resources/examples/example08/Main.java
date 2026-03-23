import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject(A.class, null);
        Object o2 = o1; 

        if (o1 != o2) {
            assert false;
        }
    }
}
