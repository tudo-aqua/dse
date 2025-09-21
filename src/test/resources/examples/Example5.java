import tools.aqua.concolic.Verifier;

public class Example5 {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject();
        Object o2 = Verifier.nondetObject(); 


		if (o1 != null && o2 != null) {
        	if (o1 == o2) {
           		 assert false;
        	}
        }
    }
}
