package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.Negation;
import gov.nasa.jpf.constraints.expressions.NumericBooleanExpression;
import gov.nasa.jpf.constraints.expressions.NumericComparator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.expressions.StringBooleanExpression;
import gov.nasa.jpf.constraints.expressions.StringBooleanOperator;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.util.ExpressionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static exclusion of constructor summaries for a path constraint.
 *
 * <p>A signature {@code s} is <em>excluded</em> for a (top-level) object {@code o} if the path constraint is
 * FALSE under every abstract outcome of {@code s}, evaluated in three-valued (Kleene) logic. Atoms that can be
 * decided from {@code s} and the {@link StaticTypeFacts} alone are fixed; every other atom (field values, nested
 * objects {@code o.f...}, primitives, other objects, unknown expression types) is UNKNOWN.
 *
 * <p>Abstract outcomes of a signature with root class {@code R}:
 * <ul>
 *   <li>{@code null|NULL}: {@code o = null}, {@code o.cls = "null"}, {@code o.err = ""}.</li>
 *   <li>normal termination: {@code o != null}, {@code o.cls = R}, {@code o.err = ""}.</li>
 *   <li>for each statically known error {@code E}: {@code o.cls = R}, {@code o.err = E}, nullness unknown.</li>
 *   <li>if the error set is incomplete: {@code o.cls = R}, {@code o.err} some unknown non-empty value.</li>
 * </ul>
 *
 * <p>Soundness: since any model of the path constraint that uses {@code s} for {@code o} falls into one of the
 * outcomes, and the decided atoms have the same value as in the SMT encoding, an excluded signature cannot occur
 * in a model. Leaving its summary out of the SMT problem therefore does not change satisfiability. This relies on
 * two assumptions: summaries of a constructor of {@code R} fix {@code o.cls = R} (also on error paths, as SPouT
 * records the class at allocation) and, if they terminate normally, {@code o != null}; and
 * {@link StaticTypeFacts#errorsOf} over-approximates the errors (or is marked incomplete).
 */
public final class SummaryRelevance {

    /** Kleene truth values. */
    enum Tri {
        TRUE, FALSE, UNKNOWN;

        static Tri of(boolean b) {
            return b ? TRUE : FALSE;
        }

        Tri not() {
            return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN;
        }

        Tri and(Tri o) {
            if (this == FALSE || o == FALSE) return FALSE;
            if (this == TRUE && o == TRUE) return TRUE;
            return UNKNOWN;
        }

        Tri or(Tri o) {
            if (this == TRUE || o == TRUE) return TRUE;
            if (this == FALSE && o == FALSE) return FALSE;
            return UNKNOWN;
        }

        Tri equiv(Tri o) {
            if (this == UNKNOWN || o == UNKNOWN) return UNKNOWN;
            return of(this == o);
        }
    }

    /**
     * Abstract outcome of a constructor call for one object.
     *
     * @param isNull         value of {@code o = null}.
     * @param cls            value of {@code o.cls}, {@code null} if unknown.
     * @param err            value of {@code o.err}, {@code null} if unknown.
     * @param errNonEmpty    if {@code err} is unknown: whether it is known to differ from {@code ""}.
     */
    record ObjectCase(Tri isNull, String cls, String err, boolean errNonEmpty) {}

    private static final Pattern BASE_OBJECT = Pattern.compile("^(__object_\\d+)(\\..*)?$");

    private final StaticTypeFacts facts;

    public SummaryRelevance(StaticTypeFacts facts) {
        this.facts = facts;
    }

    /**
     * @return the top-level object identifiers ({@code __object_i}) occurring in the path.
     */
    public static Set<String> baseObjects(List<Expression<Boolean>> path) {
        Set<String> result = new TreeSet<>();
        for (Expression<Boolean> e : path) {
            for (Variable<?> v : ExpressionUtil.freeVariables(e)) {
                Matcher m = BASE_OBJECT.matcher(v.getName());
                if (m.matches()) {
                    result.add(m.group(1));
                }
            }
        }
        return result;
    }

    /**
     * @return {@code true} if no summary of {@code signature} can satisfy {@code path} for object {@code obj}.
     */
    public boolean isExcluded(List<Expression<Boolean>> path, String obj, ConstructorSignature signature) {
        for (ObjectCase c : casesOf(signature)) {
            if (evalConjunction(path, obj, c) != Tri.FALSE) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the signatures among {@code candidates} that are not excluded for {@code obj}, simplest first.
     */
    public List<ConstructorSignature> relevantFor(List<Expression<Boolean>> path, String obj,
                                                 Collection<ConstructorSignature> candidates) {
        List<ConstructorSignature> result = new ArrayList<>();
        for (ConstructorSignature s : candidates) {
            if (!isExcluded(path, obj, s)) {
                result.add(s);
            }
        }
        result.sort(ConstructorSignature.SIMPLEST_FIRST);
        return result;
    }

    /**
     * Signatures among {@code candidates} that are not excluded for at least one of {@code objects}, in the order
     * in which they should be summarized: first those for which the last (flipped) decision of the path evaluates
     * to TRUE (and not just UNKNOWN) for some object and outcome, then simplest first.
     */
    public List<ConstructorSignature> needed(List<Expression<Boolean>> path, Collection<String> objects,
                                             Collection<ConstructorSignature> candidates) {
        List<ConstructorSignature> needed = new ArrayList<>();
        Set<String> decidedByTarget = new TreeSet<>();
        Expression<Boolean> target = path.isEmpty() ? null : path.get(path.size() - 1);

        for (ConstructorSignature s : candidates) {
            boolean isNeeded = false;
            for (String obj : objects) {
                if (!isExcluded(path, obj, s)) {
                    isNeeded = true;
                    if (target != null && satisfiesSomeCase(target, obj, s)) {
                        decidedByTarget.add(s.signature());
                    }
                }
            }
            if (isNeeded) {
                needed.add(s);
            }
        }

        needed.sort(Comparator.<ConstructorSignature>comparingInt(s -> decidedByTarget.contains(s.signature()) ? 0 : 1)
                .thenComparing(ConstructorSignature.SIMPLEST_FIRST));
        return needed;
    }

    private boolean satisfiesSomeCase(Expression<Boolean> e, String obj, ConstructorSignature s) {
        for (ObjectCase c : casesOf(s)) {
            if (eval(e, obj, c) == Tri.TRUE) {
                return true;
            }
        }
        return false;
    }

    List<ObjectCase> casesOf(ConstructorSignature s) {
        List<ObjectCase> cases = new ArrayList<>();
        if (s.isNull()) {
            cases.add(new ObjectCase(Tri.TRUE, "null", "", false));
            return cases;
        }
        cases.add(new ObjectCase(Tri.FALSE, s.rootClass(), "", false));
        ConstructorErrors errors = facts.errorsOf(s);
        // the class of the object is fixed at allocation, before the constructor body can raise an error; the
        // summaries of error paths record it as well (o.cls = R), but not the nullness of the object
        for (String error : errors.types()) {
            cases.add(new ObjectCase(Tri.UNKNOWN, s.rootClass(), error, true));
        }
        if (!errors.complete()) {
            cases.add(new ObjectCase(Tri.UNKNOWN, s.rootClass(), null, true));
        }
        return cases;
    }

    private Tri evalConjunction(List<Expression<Boolean>> path, String obj, ObjectCase c) {
        Tri result = Tri.TRUE;
        for (Expression<Boolean> e : path) {
            result = result.and(eval(e, obj, c));
            if (result == Tri.FALSE) {
                return Tri.FALSE;
            }
        }
        return result;
    }

    Tri eval(Expression<?> e, String obj, ObjectCase c) {
        if (e instanceof Constant<?> constant) {
            return constant.getValue() instanceof Boolean b ? Tri.of(b) : Tri.UNKNOWN;
        }
        if (e instanceof Negation negation) {
            return eval(negation.getNegated(), obj, c).not();
        }
        if (e instanceof PropositionalCompound pc) {
            Tri l = eval(pc.getLeft(), obj, c);
            Tri r = eval(pc.getRight(), obj, c);
            switch (pc.getOperator()) {
                case AND: return l.and(r);
                case OR: return l.or(r);
                case IMPLY: return l.not().or(r);
                case EQUIV: return l.equiv(r);
                case XOR: return l.equiv(r).not();
                default: return Tri.UNKNOWN;
            }
        }
        if (e instanceof NumericBooleanExpression nbe) {
            return evalNullCheck(nbe, obj, c);
        }
        if (e instanceof StringBooleanExpression sbe) {
            return evalStringEquality(sbe, obj, c);
        }
        if (e instanceof FunctionExpression<?> fe) {
            return evalFunction(fe, obj, c);
        }
        return Tri.UNKNOWN;
    }

    private Tri evalNullCheck(NumericBooleanExpression e, String obj, ObjectCase c) {
        NumericComparator cmp = e.getComparator();
        if (cmp != NumericComparator.EQ && cmp != NumericComparator.NE) {
            return Tri.UNKNOWN;
        }
        boolean isNullCheck = (isVar(e.getLeft(), obj) && isVar(e.getRight(), "null"))
                || (isVar(e.getLeft(), "null") && isVar(e.getRight(), obj));
        if (!isNullCheck) {
            return Tri.UNKNOWN;
        }
        return cmp == NumericComparator.EQ ? c.isNull() : c.isNull().not();
    }

    private Tri evalStringEquality(StringBooleanExpression e, String obj, ObjectCase c) {
        if (e.getOperator() != StringBooleanOperator.EQUALS) {
            return Tri.UNKNOWN;
        }
        Expression<?> var = e.getLeft();
        Expression<?> other = e.getRight();
        if (!(var instanceof Variable)) {
            var = e.getRight();
            other = e.getLeft();
        }
        String value = stringConstant(other);
        if (value == null) {
            return Tri.UNKNOWN;
        }
        if (isVar(var, obj + ".cls")) {
            return c.cls() == null ? Tri.UNKNOWN : Tri.of(c.cls().equals(value));
        }
        if (isVar(var, obj + ".err")) {
            if (c.err() != null) {
                return Tri.of(c.err().equals(value));
            }
            return (c.errNonEmpty() && value.isEmpty()) ? Tri.FALSE : Tri.UNKNOWN;
        }
        return Tri.UNKNOWN;
    }

    private Tri evalFunction(FunctionExpression<?> e, String obj, ObjectCase c) {
        Expression<?>[] args = e.getArgs();
        if (args.length == 0 || !isVar(args[0], obj + ".cls") || c.cls() == null) {
            return Tri.UNKNOWN;
        }
        String name = e.getFunction().getName();
        if ("obj.extends".equals(name) && args.length == 2) {
            String sup = stringConstant(args[1]);
            return sup == null ? Tri.UNKNOWN : Tri.of(facts.extendsRel(c.cls(), sup));
        }
        if ("obj.method.of".equals(name) && args.length == 4) {
            String m = stringConstant(args[1]);
            String d = stringConstant(args[2]);
            String declaring = stringConstant(args[3]);
            if (m == null || d == null || declaring == null) {
                return Tri.UNKNOWN;
            }
            return Tri.of(facts.methodOf(c.cls(), m, d, declaring));
        }
        return Tri.UNKNOWN;
    }

    private static boolean isVar(Expression<?> e, String name) {
        return e instanceof Variable<?> v && v.getName().equals(name);
    }

    private static String stringConstant(Expression<?> e) {
        return (e instanceof Constant<?> c && c.getValue() instanceof String s) ? s : null;
    }
}
