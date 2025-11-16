import tools.aqua.concolic.Verifier;

public class Thesis {
    public static void main(String[] args) {
        Object o = Verifier.nondetObject();

        if (o instanceof BT) {
            BT b = (BT) o;
            b.setY(42);
            assert b.getX() + 3 <= b.getY();
        }
    }
}