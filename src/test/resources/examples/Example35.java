import tools.aqua.concolic.Verifier;

public class Example35 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        if (o instanceof Greeter) {
            assert false;
        }
    }
}
