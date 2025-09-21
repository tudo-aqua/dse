import tools.aqua.concolic.Verifier;

public class Example12 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        if (!(o instanceof B)) {
            assert false;
        }
    }
}
