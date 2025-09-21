import tools.aqua.concolic.Verifier;

public class Example1 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();
//         System.out.println(o);
    }
}
