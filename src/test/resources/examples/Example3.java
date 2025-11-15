//import tools.aqua.concolic.Verifier;
//
//public class Example3 {
//    public static void main(String[] args) {
//        Object o = Verifier.nondetObject();
//
//        if (o == null) {
//            assert false;
//        }
//    }
//}


import tools.aqua.concolic.Verifier;

public class Example3 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();
        A a = (A) o;

        if (a != null && a.getX() > 5) {
            assert false;
        }
    }
}
