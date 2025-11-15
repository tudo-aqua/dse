import tools.aqua.concolic.Verifier;

public class Thesis {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();

        if (o instanceof BT) {
            BT b = (BT) o;
            b.y = 42;
            assert b.x + 3 <= b.y;
        }
    }
}