import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.mutable.MutableInt;
import tools.aqua.concolic.Verifier;

/*
 * Apache Commons Collections 4.6.0 (with Commons Lang 3.21.0): Predicate.evaluate(o) with a concolic predicate
 * of the interface Predicate (about twenty implementations: NotPredicate, AndPredicate, NullIsTruePredicate,
 * IdentityPredicate, EqualPredicate, UniquePredicate, ...; composite predicates with null children raise a
 * NullPointerException) and a concolic MutableInt argument. Evaluating a predicate twice on the same object
 * gives the same result, except for UniquePredicate, which accepts an object only the first time.
 */
public class Main {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Predicate<MutableInt> predicate = Verifier.nondetObject(Predicate.class, null);
        MutableInt o = Verifier.nondetObject(MutableInt.class, null);
        boolean first = predicate.evaluate(o);
        boolean second = predicate.evaluate(o);
        if (first != second) {
            assert false;
        }
    }
}
