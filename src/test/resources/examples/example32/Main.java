import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(Integer.class, null);
        Integer i = (Integer) o;  

        if (i > 5) {
            assert false;
        }
    }
}
