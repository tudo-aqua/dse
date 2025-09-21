import tools.aqua.concolic.Verifier;

public class Example13 {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject(); 

        if (o instanceof A) {
            o.foo()
        }
    }
}
