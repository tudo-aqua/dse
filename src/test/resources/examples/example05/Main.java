import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject(A.class, null);
        Object o2 = Verifier.nondetObject(A.class, null);

        if (o1 == o2) {
             assert false;
        }
    }
}
