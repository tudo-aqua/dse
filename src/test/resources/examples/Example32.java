import tools.aqua.concolic.Verifier;

public class Example32 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 
        Integer i = (Integer) o;  

        if (i > 5) {
            assert false;
        }
    }
}
