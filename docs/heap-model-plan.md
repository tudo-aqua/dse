# Plan: a symbolic heap model without shape assumptions

Branches: DSE `heap-model`, SPouT `heap-model` (both from the TACAS'27 versions).

## Problem

Constructor summaries and path constraints currently assume tree-shaped object structures:

- SPouT attaches the symbol of an object to the object itself. A field that refers to an object that already has a
  symbol (a cycle back to an ancestor, or two fields sharing an object) gets no symbol of its own; SPouT logs "heap
  loop" and stops. Reading the field yields the other symbol, so a branch on aliasing becomes a tautology in the
  executed run (`__object_0 == __object_0`), its negation is UNSAT, and feasible paths are pruned.
- Summaries do not record aliasing (the `OBJECT_EQ` condition in `annotateObject` is commented out), and DSE asserts
  that all object symbols of a query are pairwise distinct unless null. Models with sharing or cycles are impossible.

Minimal examples (all explored incompletely today, depending on which constructor the solver picks first):

```java
class Node { Node next; Node() { next = this; } Node(Node n) { next = n; } }
// if (n != null && n.next == n) assert false;      reachable with Node(); the path n.next != n is lost

class Pair { Object a, b; Pair(Object x) { a = x; b = x; } Pair(Object x, Object y) { a = x; b = y; } }
// if (p != null && p.a != null && p.a == p.b) assert false;   missed in eager mode (depth 2)
```

## Idea

1. **Object identities (oids) in the solver, fields as functions.** An object is an `Int`; every field is an
   uninterpreted function of the object: `Pair_a(o)`, `MutableInt_value(o)`, `cls(o)`. Aliasing is equality of oids;
   congruence (`a(o) = b(o)` implies `x(a(o)) = x(b(o))`) is built into the theory; cycles are equations
   (`Node_next(o) = o`), so no unrolling and no annotation depth limit is needed.
2. **Terms on references, not on objects.** In SPouT, a reference on the stack, in a local, in a field, or in an
   array element carries a *term*: `__object_k` for a nondet object, `Pair_b(__object_0)` for the result of reading
   `p.b`, `null`. Two references to the same concrete object can carry different terms; `p.a == p.b` is recorded as
   `(= (Pair_a __object_0) (Pair_b __object_0))`, which the solver decides with the summaries. (Oids on objects alone
   would reproduce the tautologies above: both references would carry the oid of the one concrete object.)
3. **Summaries describe the heap exactly.** At the end of a constructor, SPouT walks the heap reachable from `this` by
   identity (finite, no depth limit) and records class, fields, and identities of all reachable objects.
4. **Naming scheme.** DSE instantiates only `__object_k` (nondet calls) with summaries. Objects inside a summary are
   existentially quantified constants `__heap_k_j` (j-th object reachable in the construction of `__object_k`); DSE
   never instantiates them. Summaries are templated on `__object_0` / `__heap_0_j` and renamed per object (as today).
5. **Allocation ownership instead of global distinctness.** `owner(o)` is the nondet object whose construction
   allocated `o`: `owner(__object_k) = k`, `owner(__heap_k_j) = k`; within a summary path, all allocated objects are
   pairwise distinct. Objects of different owners are distinct because `owner` is a function. Objects that existed
   before the construction (static singletons such as `ComparableComparator.INSTANCE`) get no owner and may alias
   across nondet objects (sound; optionally named by their static field for precision).
6. **Writes after construction (store semantics).** Program writes to fields of symbolic objects are recorded per
   field as `(term of receiver, term of value)`. A read through term `t` yields
   `ite(t = t_w_n, v_n, ... ite(t = t_w_1, v_1, f(t)))` (newest write first; syntactically equal terms short-cut).
   Without this, a write through one alias and a read through another reproduce the tautology problem. Objects
   allocated by the program are fresh, so writes to them need no logging.

Bounds that remain: the nesting depth of constructor *calls* (recursive parameter types, signature enumeration) and a
cap on the size of the reachable heap of a summary (huge JDK structures); exceeding the cap is reported (incomplete
summary), never silently dropped.

## Phases

### 0. Oracle tests (DSE, first)
Integration tests with expected verdicts and expected explored branches (both outcomes of every aliasing branch):
self-loop, cycle of length 2, sharing between fields, nested reads through aliases (`p.a.x` vs `p.b.x`), write
through one alias then read through the other, sharing of a static singleton between two nondet objects, a shared
object passed to two constructor arguments. They document today's failures and are the acceptance tests.

### 1. Encoding (DSE)
- Declarations: `cls (Int) String`, `owner (Int) Int`, one function per field (named by declaring class and field,
  sort from the field type), arrays: `length (Int) (_ BitVec 32)` and one element function per element sort
  `(Int (_ BitVec 32)) Sort`. Bases `__object_k`, err as today.
- Replace `objectNotIdentityConstraints` (all symbols pairwise distinct) by owner constraints and per-summary
  distinctness.
- Grounding: `obj.extends(cls(t), C)` / `obj.method.of(cls(t), ...)` already work for non-constant arguments.
- Remove the field-sort renaming (`__as_<sort>`): field functions have unique names and fixed sorts.
- Adapt `startQuery` (bases are only `__object_\d+`), `SummaryRelevance` (atoms over `cls(__object_k)`),
  `StaticTypeFacts`, eager blueprint rendering, and the trace parser (function terms in decisions).

### 2. Terms on references (SPouT; largest part)
- Reference slots carry annotations like primitive slots: `aload`/`astore` and their short forms, `dup*`/`swap`,
  `aaload`/`aastore`, `getfield`/`putfield`/`getstatic`/`putstatic` of references, argument passing and `areturn`,
  `checkcast`, exceptions (`athrow`), and the quick nodes.
- All consumers use the slot term instead of `objectAnnotation(obj)`: null checks, `if_acmp*`, `ifnull`,
  `instanceof`, `checkcast`, dispatch (`obj.method.of(cls(t), ...)`), `getClass`, receivers of field accesses.
- `nondetObject` returns its object with the slot term `__object_k`; objects reachable at that moment are flagged as
  *initial symbolic* (identity walk, no depth). A field read from a flagged object without logged writes yields
  `f(t_receiver)`; from an unflagged object the stored slot annotation (or none).
- Substituted/native methods that drop annotations (e.g. `System.arraycopy`) stay imprecise as today, but are
  reported.

### 3. Writes (SPouT)
Per-field write logs for flagged objects, `ite` chains on reads (see idea 6); arrays keyed by array term and index.

### 4. Summaries (SPouT, summary mode)
At the end of the constructor: identity walk from `this`; names `__object_0` (this), `__heap_0_j` (others);
facts `cls(o) = C`, `f(o) = value` (oid name, `null`, or the symbolic expression of a primitive), `owner(o) = 0`
for allocated objects, pairwise distinctness of allocated objects, array lengths and elements (capped). Replaces the
depth-bounded `annotateObject` walk and its heap-loop handling; `concolic.max.object.annotation.depth` is no longer
needed.

### 5. Summaries in DSE
Parse summaries with function terms, rename `__object_0` / `__heap_0_` per base, declare the `__heap_k_j` constants,
keep the incremental machinery (relevance, refinement loop, over-approximation check) unchanged otherwise.

### 6. Validation
Oracle tests in both modes; regression (svComp, own-tests, real-world, scaling); library experiments; compare paths
and times with the TACAS'27 numbers. Paper: the completeness theorem without a heap-shape assumption (bounds: depth of
constructor calls, size of reachable heaps).

## Open decisions
- Static singletons: unconstrained (sound, may diverge) or named by static field (precise, more work).
- Writes (phase 3) are needed for soundness with aliasing; they can follow phases 1, 2, 4, 5 if the first milestone
  only targets constructor-created aliasing.
- Strings in fields stay concrete (`concolic.symbolic.string.fields=false`) as in the evaluation.

## Implementation status (branches `heap-model` of DSE, SPouT, jconstraints)

Implemented: phases 0–5. Oracle tests (group `heap`) pass in eager and incremental mode; svComp and own-tests pass
unchanged.

Deviations from the plan above:
- **Writes** (phase 3) are not encoded by `ite` chains but by case splits: a read through term `t` of a field that
  was written before decides, for each logged write `(t_w, v)` (newest first), whether `t = t_w` (a branch of the
  path, both outcomes are explored); the value is `v` of the first aliasing write, `f(t)` if there is none.
  Syntactically equal terms (and different constant indices) need no decision.
- **Static singletons**: objects held by static reference fields of the classes met in the heap walk (the class of
  the object and its super classes, the declaring class of the field, the constructed class) are named
  `__static_<class>_<field>` with `obj.owner = -1` (existed before the construction, distinct from all allocated
  objects, may be shared by several nondet objects).
- **Terms across calls**: the annotations of reference arguments travel in a side table keyed by the argument
  array (from `popArguments` to `initArguments`), returned references in a thread-local slot (from `areturn` to the
  push of the result).

Remaining imprecision (reported or concrete, never wrong constraints on the explored path):
- References lose their terms through native and substituted methods (e.g. `Object.getClass`, reflection,
  `System.arraycopy`) and `invokedynamic` call sites; their uses fall back to the object annotation (the identity
  of a nondet object, or the first term by which the object was read).
- Writes through references without terms are concretized for reads of the same object.
- Summaries describe at most 64 elements per array and 10,000 objects per heap.
- Symbolic string fields (`concolic.symbolic.string.fields`, default true): reading a string field through a term
  sets the symbolic value of the string object (as before, the value is attached to the object).
