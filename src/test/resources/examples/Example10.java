import tools.aqua.concolic.Verifier;

public class Example10 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        if (!(o instanceof A)) {
            assert false;
        }
    }
}
