import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Object o1 = Verifier.nondetObject(InterfaceAddress.class, null);
        Object o2 = Verifier.nondetObject(InterfaceAddress.class, null);

        if (o1 == null || o2 == null) {
            assert false;
        }

        o1.equals(o2);
    }
}
