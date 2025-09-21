import tools.aqua.concolic.Verifier;

public class Example3 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();
//        Object o2 = Verifier.nondetObject(); 

        if (o == null) {
            assert false;
        }
//        if (o2 == null) {
//        	assert false; 
//        }
    }
}
