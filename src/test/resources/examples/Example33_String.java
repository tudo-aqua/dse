import tools.aqua.concolic.Verifier;

public class Example33 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 
        String s = (String) o;  

        if (s.equals("test")) {
            assert false;
        }
    }
}
