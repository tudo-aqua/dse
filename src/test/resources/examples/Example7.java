import tools.aqua.concolic.Verifier;

public class Example7 {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject();
        Object o2 = o1; 

        if (o1 == o2) {
            assert false;
        }
    }
}
