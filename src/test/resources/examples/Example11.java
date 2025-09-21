import tools.aqua.concolic.Verifier;

public class Example11 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        if (o instanceof B) {
            assert false;
        }
    }
}
