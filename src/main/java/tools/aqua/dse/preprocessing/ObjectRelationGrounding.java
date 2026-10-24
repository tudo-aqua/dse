package tools.aqua.dse.preprocessing;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.LogicalOperator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.expressions.StringBooleanExpression;
import gov.nasa.jpf.constraints.expressions.functions.FunctionExpression;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.DuplicatingVisitor;
import gov.nasa.jpf.constraints.util.ExpressionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quantifier-free encoding of the static relations {@code obj.extends} and {@code obj.method.of}: every application
 * is replaced by the disjunction over the rows of the relation that match its constant arguments, e.g.
 * {@code (obj.extends o.cls "LA;")} by {@code (or (= o.cls "null") (= o.cls "LA;") (= o.cls "LB;"))}.
 *
 * <p>This is equivalent to the definition by a universally quantified formula ({@link Opal#renderExtendsSummary},
 * {@link Opal#generatePolymorphismSummaryInternal}), but the solver does not need to instantiate quantifiers over
 * the whole class table, which is slow for large tables (e.g., if objects are only bounded by {@code Object}).
 * In practice, the class argument is the only non-constant one, so an application becomes a disjunction of
 * equalities with class names.
 */
public final class ObjectRelationGrounding {

    public static final String EXTENDS = "obj.extends";
    public static final String METHOD_OF = "obj.method.of";

    private final Map<String, List<String[]>> relations = new HashMap<>();
    /** rows of a relation matching a pattern of constant arguments (null = not constant), by relation and pattern */
    private final Map<String, Map<List<String>, List<List<String>>>> cache = new HashMap<>();

    /**
     * @param extendsRelation pairs {@code {sub, super}} of {@code obj.extends}.
     * @param methods         rows {@code {class, name, descriptor, declaringClass}} of {@code obj.method.of}.
     */
    public ObjectRelationGrounding(List<String[]> extendsRelation, List<String[]> methods) {
        relations.put(EXTENDS, extendsRelation);
        relations.put(METHOD_OF, methods);
    }

    /**
     * Adds rows to {@code obj.extends} (e.g. for classes that only occur in constructor summaries). Constraints
     * grounded before are not affected: callers add classes before the constraints that mention them.
     */
    public void addExtends(List<String[]> rows) {
        List<String[]> relation = relations.get(EXTENDS);
        Set<List<String>> known = new java.util.HashSet<>();
        for (String[] row : relation) {
            known.add(Arrays.asList(row));
        }
        boolean changed = false;
        List<String[]> extended = new ArrayList<>(relation);
        for (String[] row : rows) {
            if (known.add(Arrays.asList(row))) {
                extended.add(row);
                changed = true;
            }
        }
        if (changed) {
            relations.put(EXTENDS, extended);
            cache.remove(EXTENDS);
        }
    }

    /** @return whether the class occurs as subtype in {@code obj.extends} */
    public boolean knowsClass(String jvmTypeName) {
        for (String[] row : relations.get(EXTENDS)) {
            if (row[0].equals(jvmTypeName)) {
                return true;
            }
        }
        return false;
    }

    public static ObjectRelationGrounding of(List<String[]> extendsRelation, List<Opal.PolymorphyInformation> infos) {
        List<String[]> methods = new ArrayList<>();
        for (Opal.PolymorphyInformation info : infos) {
            methods.add(new String[]{info.accessingClass(), info.methodName(), info.methodDescriptor(),
                    info.declaringClass()});
        }
        return new ObjectRelationGrounding(extendsRelation, methods);
    }

    @SuppressWarnings("unchecked")
    public Expression<Boolean> ground(Expression<Boolean> expression) {
        return (Expression<Boolean>) expression.accept(visitor, null);
    }

    public List<Expression<Boolean>> ground(List<Expression<Boolean>> expressions) {
        List<Expression<Boolean>> result = new ArrayList<>(expressions.size());
        for (Expression<Boolean> e : expressions) {
            result.add(ground(e));
        }
        return result;
    }

    private final DuplicatingVisitor<Void> visitor = new DuplicatingVisitor<>() {
        @Override
        public <E> Expression<?> visit(FunctionExpression<E> f, Void data) {
            String name = f.getFunction().getName();
            if (!relations.containsKey(name)) {
                return defaultVisit(f, data);
            }
            Expression<?>[] args = Arrays.stream(f.getArgs()).map(a -> visit(a, data)).toArray(Expression<?>[]::new);
            return instantiate(name, args);
        }
    };

    private Expression<Boolean> instantiate(String relation, Expression<?>[] args) {
        List<String> pattern = new ArrayList<>(args.length);
        for (Expression<?> arg : args) {
            pattern.add(arg instanceof Constant<?> c ? String.valueOf(c.getValue()) : null);
        }
        List<List<String>> projections = cache.computeIfAbsent(relation, r -> new HashMap<>())
                .computeIfAbsent(pattern, p -> project(relation, p));
        List<Expression<Boolean>> disjuncts = new ArrayList<>(projections.size());
        for (List<String> values : projections) {
            if (values.isEmpty()) {
                return ExpressionUtil.TRUE; // all arguments constant and the row exists
            }
            Expression<Boolean> conjunction = null;
            int v = 0;
            for (int i = 0; i < args.length; i++) {
                if (pattern.get(i) != null) {
                    continue;
                }
                Expression<Boolean> eq = StringBooleanExpression.createEquals(args[i],
                        Constant.create(BuiltinTypes.STRING, values.get(v++)));
                conjunction = conjunction == null ? eq : PropositionalCompound.create(conjunction, LogicalOperator.AND, eq);
            }
            disjuncts.add(conjunction);
        }
        return balancedOr(disjuncts, 0, disjuncts.size());
    }

    /** the values of the non-constant arguments of all rows that match the constant ones, without duplicates */
    private List<List<String>> project(String relation, List<String> pattern) {
        Set<List<String>> result = new LinkedHashSet<>();
        for (String[] row : relations.get(relation)) {
            List<String> values = new ArrayList<>();
            boolean matches = true;
            for (int i = 0; i < row.length && matches; i++) {
                if (pattern.get(i) == null) {
                    values.add(row[i]);
                } else {
                    matches = pattern.get(i).equals(row[i]);
                }
            }
            if (matches) {
                result.add(values);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    /** balanced tree of binary disjunctions: deep chains overflow the stack of the translation to Z3 */
    private static Expression<Boolean> balancedOr(List<Expression<Boolean>> terms, int from, int to) {
        if (from == to) {
            return ExpressionUtil.FALSE;
        }
        if (to - from == 1) {
            return terms.get(from);
        }
        int mid = (from + to) >>> 1;
        return PropositionalCompound.create(balancedOr(terms, from, mid), LogicalOperator.OR, balancedOr(terms, mid, to));
    }
}
