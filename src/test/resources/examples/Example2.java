import tools.aqua.concolic.Verifier;

public class Example2 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();
//        A a = (A) o; //correct downcast
//        B b = (B) o; //incorrect downcast

		B b = (B) o;
//		A a = b;  
//        System.out.println(o);
//      if (s.length() > 0 || s.equals("OHOH")) {
//      	assert false;
//      }
    }
}
