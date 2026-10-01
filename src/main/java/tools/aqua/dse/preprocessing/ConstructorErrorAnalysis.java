package tools.aqua.dse.preprocessing;

import org.opalj.br.ClassFile;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.ClassType;
import org.opalj.br.Code;
import org.opalj.br.ExceptionHandler;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor$;
import org.opalj.br.ReferenceType;
import org.opalj.br.analyses.Project;
import org.opalj.br.instructions.Instruction;
import org.opalj.tac.AITACode;
import org.opalj.tac.AssignmentLikeStmt;
import org.opalj.tac.Call;
import org.opalj.tac.DUVar;
import org.opalj.tac.Expr;
import org.opalj.tac.FunctionCall;
import org.opalj.tac.LazyDetachedTACAIKey$;
import org.opalj.tac.MethodCall;
import org.opalj.tac.Stmt;
import org.opalj.tac.TACMethodParameter;
import org.opalj.value.IsReferenceValue;
import org.opalj.value.ValueInformation;
import scala.Option;
import scala.collection.Iterator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static over-approximation of the exceptions that may escape a constructor call: language exceptions raised by
 * bytecode instructions, explicitly thrown exceptions, and exceptions escaping called methods, in all code
 * reachable from the constructor, <em>including library (JDK) code</em>. The project passed to this analysis must
 * therefore contain library method bodies (see {@link Opal#inferConstructorErrors}).
 *
 * <p>For each method with a body, the analysis walks the three-address code (computed by OPAL's abstract
 * interpretation, so dead code is removed and the receiver {@code this} and fresh objects are known to be
 * non-null) and collects per statement:
 * <ul>
 *   <li>{@code throw e}: the type of {@code e} (if the abstract interpretation only knows an upper bound, the bound
 *       is used and the result is imprecise), plus {@code NullPointerException} if {@code e} may be null;</li>
 *   <li>method calls: the exceptions escaping all resolved call targets (recursively, computed as a fixed point for
 *       recursive methods), plus {@code NullPointerException} if the receiver may be null;</li>
 *   <li>all other instructions: the language exceptions of the instruction ({@link Instruction#jvmExceptions()},
 *       e.g. {@code ArithmeticException}, {@code ArrayIndexOutOfBoundsException}, {@code ClassCastException}),
 *       where {@code NullPointerException} is dropped if the dereferenced value is known to be non-null.</li>
 * </ul>
 * Exceptions that are definitely caught by a handler covering the statement are removed.
 *
 * <p><b>Warning:</b> subtypes of {@code java.lang.Error} other than {@code AssertionError} are not modeled, neither
 * implicit (linkage and VM errors) nor explicitly thrown ones; see {@link #addEscaping}. Neither is
 * {@code IllegalMonitorStateException} (unstructured locking). Constructors of
 * {@code java.lang.Object} and of {@code java.lang.Throwable} and its subclasses are treated as non-throwing.
 *
 * <p>Where the analysis cannot be precise (native or abstract methods without body, {@code invokedynamic},
 * unresolvable calls, imprecise types of thrown values, more call targets than {@code maxCallTargets}, more analyzed
 * methods than {@code maxMethods}), it uses the declared exceptions ({@code throws} clauses) and, depending on the
 * {@link Mode}, either treats the result as complete or marks it incomplete.
 */
public final class ConstructorErrorAnalysis {

    /** Treatment of the places where the analysis cannot be precise. */
    public enum Mode {
        /** Declared and language exceptions only: imprecision is ignored, results are treated as complete. */
        DECLARED_AND_LANGUAGE,
        /** As above, but imprecision marks the result as incomplete ("unknown other exceptions possible"). */
        WITH_UNKNOWN
    }

    public static final int DEFAULT_MAX_CALL_TARGETS = 32;
    public static final int DEFAULT_MAX_METHODS = 20_000;

    private static final int INVOKEDYNAMIC = 186;
    private static final ClassType NPE = ClassType.apply("java/lang/NullPointerException");
    private static final ClassType ERROR = ClassType.apply("java/lang/Error");
    private static final ClassType ASSERTION_ERROR = ClassType.apply("java/lang/AssertionError");
    private static final ClassType THROWABLE = ClassType.apply("java/lang/Throwable");
    /** Raised by return instructions only for unstructured locking, which javac never generates. */
    private static final ClassType IMSE = ClassType.apply("java/lang/IllegalMonitorStateException");

    private final Project<?> project;
    private final ClassHierarchy ch;
    private final scala.Function1<Method, AITACode<TACMethodParameter, ValueInformation>> tacai;
    private final Mode mode;
    private final int maxCallTargets;
    private final int maxMethods;

    /** Results that no longer change. */
    private final Map<Method, ConstructorErrors> finalResults = new HashMap<>();
    /** Current approximations of methods on a recursive cycle (fixed-point iteration). */
    private final Map<Method, ConstructorErrors> approximations = new HashMap<>();
    private final Set<Method> onStack = new HashSet<>();
    /** Non-final methods already computed in the current iteration (each is computed at most once per iteration). */
    private final Set<Method> computedThisIteration = new HashSet<>();
    private boolean usedApproximation = false;
    private boolean approximationChanged = false;
    /** Distinct methods whose body was analyzed (bounded by {@code maxMethods}). */
    private final Set<Method> analyzedMethods = new HashSet<>();

    public ConstructorErrorAnalysis(Project<?> project, Mode mode) {
        this(project, mode, DEFAULT_MAX_CALL_TARGETS, DEFAULT_MAX_METHODS);
    }

    @SuppressWarnings("unchecked")
    public ConstructorErrorAnalysis(Project<?> project, Mode mode, int maxCallTargets, int maxMethods) {
        this.project = project;
        this.ch = project.classHierarchy();
        this.tacai = (scala.Function1<Method, AITACode<TACMethodParameter, ValueInformation>>)
                project.get(LazyDetachedTACAIKey$.MODULE$);
        this.mode = mode;
        this.maxCallTargets = maxCallTargets;
        this.maxMethods = maxMethods;
    }

    /**
     * Errors of a single constructor call (without nested constructor calls of arguments).
     *
     * @param rootClass  JVM type name, e.g. {@code LB;}.
     * @param descriptor JVM descriptor, e.g. {@code (I)V}.
     */
    public ConstructorErrors errorsOfConstructor(String rootClass, String descriptor) {
        if (rootClass.equals("Ljava/lang/Object;")) {
            return ConstructorErrors.NONE;
        }
        ClassType type = ClassType.apply(rootClass.substring(1, rootClass.length() - 1));
        Option<ClassFile> cf = project.classFile(type);
        if (cf.isEmpty()) {
            return ConstructorErrors.UNKNOWN;
        }
        Option<Method> init = cf.get().findMethod("<init>", MethodDescriptor$.MODULE$.apply(descriptor));
        if (init.isEmpty()) {
            return ConstructorErrors.UNKNOWN;
        }
        return analyze(init.get());
    }

    /** @return number of distinct methods whose body has been analyzed so far. */
    public int analyzedMethodCount() {
        return analyzedMethods.size();
    }

    /** Fixed point over recursive cycles reachable from {@code root}. */
    ConstructorErrors analyze(Method root) {
        ConstructorErrors result;
        do {
            approximationChanged = false;
            usedApproximation = false;
            computedThisIteration.clear();
            result = escaping(root);
        } while (approximationChanged);
        finalResults.putAll(approximations);
        approximations.clear();
        computedThisIteration.clear();
        return result;
    }

    /** Exceptions that may escape a call of {@code m}. */
    private ConstructorErrors escaping(Method m) {
        ConstructorErrors known = finalResults.get(m);
        if (known != null) {
            return known;
        }
        if (onStack.contains(m) || computedThisIteration.contains(m)) {
            usedApproximation = true;
            return approximations.getOrDefault(m, ConstructorErrors.NONE);
        }

        boolean outerUsedApproximation = usedApproximation;
        usedApproximation = false;
        onStack.add(m);
        ConstructorErrors result;
        try {
            result = compute(m);
        } finally {
            onStack.remove(m);
        }

        if (!usedApproximation) {
            finalResults.put(m, result);
        } else {
            ConstructorErrors old = approximations.get(m);
            ConstructorErrors updated = old == null ? result : old.union(result);
            if (!updated.equals(old)) {
                approximationChanged = true;
            }
            approximations.put(m, updated);
            computedThisIteration.add(m);
            result = updated;
        }
        usedApproximation |= outerUsedApproximation;
        return result;
    }

    private ConstructorErrors compute(Method m) {
        ClassType declaring = m.classFile().thisType();
        if (m.isConstructor() && (declaring.equals(ClassType.Object()) || ch.isSubtypeOf(declaring, THROWABLE))) {
            return ConstructorErrors.NONE;
        }
        if (m.body().isEmpty()) {
            return declared(m);
        }
        if (!analyzedMethods.contains(m)) {
            if (analyzedMethods.size() >= maxMethods) {
                return declared(m);
            }
            analyzedMethods.add(m);
        }

        Code code = m.body().get();
        Instruction[] instructions = (Instruction[]) code.instructions();
        AITACode<TACMethodParameter, ValueInformation> tac = tacai.apply(m);
        Set<String> types = new TreeSet<>();
        boolean complete = true;

        for (Stmt<DUVar<ValueInformation>> stmt : tac.stmts()) {
            int pc = stmt.pc();
            if (pc < 0) {
                continue; // synthetic statements (e.g., parameter initialization)
            }

            if (stmt.isThrow()) {
                IsReferenceValue value = referenceValue(stmt.asThrow().exception());
                if (value == null) {
                    complete &= addEscaping(code, pc, THROWABLE, false, types);
                    continue;
                }
                Iterator<? extends ReferenceType> it = value.upperTypeBound().iterator();
                while (it.hasNext()) {
                    ReferenceType t = it.next();
                    complete &= addEscaping(code, pc, t.asClassType(), value.isPrecise(), types);
                }
                if (!value.isNull().isNo()) {
                    complete &= addEscaping(code, pc, NPE, true, types);
                }
                continue;
            }

            Call<DUVar<ValueInformation>> call = callOf(stmt);
            if (call != null) {
                complete &= addCall(code, pc, declaring, call, instructions[pc], types);
                continue;
            }

            Expr<DUVar<ValueInformation>> dereferenced = dereferencedValue(stmt);
            scala.collection.Iterator<ClassType> jvm = instructions[pc].jvmExceptions().iterator();
            while (jvm.hasNext()) {
                ClassType t = jvm.next();
                if (t.equals(IMSE)) {
                    continue;
                }
                if (t.equals(NPE) && dereferenced != null && isNonNull(dereferenced)) {
                    continue;
                }
                complete &= addEscaping(code, pc, t, true, types);
            }
        }
        return new ConstructorErrors(types, complete);
    }

    private boolean addCall(Code code, int pc, ClassType caller, Call<DUVar<ValueInformation>> call,
                            Instruction instruction, Set<String> types) {
        boolean complete = true;
        Option<Expr<DUVar<ValueInformation>>> receiver = call.receiverOption();
        if (receiver.isDefined() && !isNonNull(receiver.get())) {
            complete &= addEscaping(code, pc, NPE, true, types);
        }

        if (instruction.opcode() == INVOKEDYNAMIC) {
            return complete && imprecise();
        }

        scala.collection.Set<Method> targets;
        try {
            targets = call.resolveCallTargets(caller, project, scala.$less$colon$less$.MODULE$.refl());
        } catch (RuntimeException e) {
            return complete && imprecise();
        }
        if (targets.isEmpty()) {
            return complete && imprecise();
        }

        boolean tooManyTargets = targets.size() > maxCallTargets;
        scala.collection.Iterator<Method> it = targets.iterator();
        while (it.hasNext()) {
            Method target = it.next();
            ConstructorErrors calleeErrors = tooManyTargets ? declared(target) : escaping(target);
            complete &= calleeErrors.complete();
            for (String type : calleeErrors.types()) {
                complete &= addEscaping(code, pc, ClassType.apply(type), true, types);
            }
        }
        return complete;
    }

    /** Fallback where the body is not analyzed: the declared exceptions. */
    private ConstructorErrors declared(Method m) {
        Set<String> types = new TreeSet<>();
        if (m.exceptionTable().isDefined()) {
            scala.collection.Iterator<ClassType> it = m.exceptionTable().get().exceptions().iterator();
            while (it.hasNext()) {
                types.add(it.next().fqn());
            }
        }
        return new ConstructorErrors(types, imprecise());
    }

    /** @return the completeness of a result at a place where the analysis is imprecise. */
    private boolean imprecise() {
        return mode == Mode.DECLARED_AND_LANGUAGE;
    }

    /**
     * Adds {@code type} unless it is definitely caught at {@code pc}.
     *
     * @param precise {@code false} if the exception may also be a subtype of {@code type}.
     * @return {@code false} if the result became incomplete.
     */
    private boolean addEscaping(Code code, int pc, ClassType type, boolean precise, Set<String> types) {
        // WARNING: deliberate unsoundness. Subtypes of java.lang.Error other than AssertionError are dropped,
        // even when thrown explicitly (the JDK throws LinkageError, OutOfMemoryError, InternalError, ... deep in
        // library code). Keeping them inflates the static error list, and with it the branching on o.err at
        // every concolic object, while they practically never occur when executing constructors. If such an error
        // does occur at runtime, it is not in the static list and ends up in the "other error" branch of the
        // o.err decision. An imprecise throw whose upper bound is Error (e.g. a rethrow) may still be an
        // AssertionError and is recorded as such.
        if (ch.isSubtypeOf(type, ERROR) && !type.equals(ASSERTION_ERROR)) {
            if (!precise && ch.isSubtypeOf(ASSERTION_ERROR, type)) {
                type = ASSERTION_ERROR;
            } else {
                return true;
            }
        }
        scala.collection.Iterator<ExceptionHandler> handlers = code.exceptionHandlersFor(pc).iterator();
        while (handlers.hasNext()) {
            Option<ClassType> catchType = handlers.next().catchType();
            if (catchType.isEmpty() || ch.isSubtypeOf(type, catchType.get())) {
                // definitely caught; a rethrow in the handler is a throw statement of its own
                return true;
            }
        }
        types.add(type.fqn());
        return precise || imprecise();
    }

    @SuppressWarnings("unchecked")
    private static Call<DUVar<ValueInformation>> callOf(Stmt<DUVar<ValueInformation>> stmt) {
        if (stmt instanceof MethodCall) {
            return (Call<DUVar<ValueInformation>>) stmt;
        }
        if (stmt instanceof AssignmentLikeStmt) {
            Expr<?> expr = ((AssignmentLikeStmt<DUVar<ValueInformation>>) stmt).expr();
            if (expr instanceof FunctionCall) {
                return (Call<DUVar<ValueInformation>>) expr;
            }
        }
        return null;
    }

    /** The value whose nullness determines a NullPointerException of this statement, if any. */
    private static Expr<DUVar<ValueInformation>> dereferencedValue(Stmt<DUVar<ValueInformation>> stmt) {
        if (stmt.isPutField()) return stmt.asPutField().objRef();
        if (stmt.isArrayStore()) return stmt.asArrayStore().arrayRef();
        if (stmt.isMonitorEnter()) return stmt.asMonitorEnter().objRef();
        if (stmt.isMonitorExit()) return stmt.asMonitorExit().objRef();
        if (stmt instanceof AssignmentLikeStmt) {
            @SuppressWarnings("unchecked")
            Expr<DUVar<ValueInformation>> expr = ((AssignmentLikeStmt<DUVar<ValueInformation>>) stmt).expr();
            if (expr.isGetField()) return expr.asGetField().objRef();
            if (expr.isArrayLoad()) return expr.asArrayLoad().arrayRef();
            if (expr instanceof org.opalj.tac.ArrayLength) return expr.asArrayLength().arrayRef();
        }
        return null;
    }

    private static boolean isNonNull(Expr<DUVar<ValueInformation>> e) {
        IsReferenceValue value = referenceValue(e);
        return value != null && value.isNull().isNo();
    }

    private static IsReferenceValue referenceValue(Expr<DUVar<ValueInformation>> e) {
        if (!e.isVar()) {
            return null;
        }
        ValueInformation value = e.asVar().value();
        return value.isReferenceValue() ? value.asReferenceValue() : null;
    }
}
