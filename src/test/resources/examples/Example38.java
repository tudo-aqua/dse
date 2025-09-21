import tools.aqua.concolic.Verifier;

public class Example1 {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject();
		Object o2 = Verifier.nondetObject(); 

        if (o1 == o2) {
        	return false; 
        }
    }
}
