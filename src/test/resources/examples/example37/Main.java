import org.joda.money.Money;
import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        try {
            Object o1 = Verifier.nondetObject(Money.class, null);
            Object o2 = Verifier.nondetObject(Money.class, null);

            o1.equals(o2);
        }
        catch (Exception e) {
            assert false;
        }

    }
}
