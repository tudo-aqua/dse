# Proposal: Incremental (on-demand) constructor summaries

Status: v3. Step 1 is implemented on the local branch `incremental-summaries`, based on `symbolic-objects` @ `b77a523`:
the catalogue, the static exclusion, the summary store and the OPAL error inference (see §7). Nothing is wired into
`ConstraintsTree` yet, so eager mode behaves as before.

## 1. What happens today

All summary work is done eagerly, before the first path is explored. This happens in `Config` → `SmtProblemManager` → `ConstructorSummaryManager`:

1. `Opal.generateSignaturesOfPossibleConstructorCallsFromNondetObject(depth)` lists **every** constructor
   signature for every `nondetObject` call site, at nesting depths 1..d. An example signature is
   `LC;|(LA;)V|{LB;|(I)V|{}}`.
2. `performDseOnConstructor` runs a full nested DSE (several SPouT launches) **for each signature**.
3. All summary traces are concatenated into one disjunction `bluePrintConstructorSummaries` over `__object_0`.
4. `ConstraintsTree.updateContext` adds `(assert (or <blueprint[__object_i]>))` for every base object in
   the path, then solves.

Stage 2 therefore costs one DSE per signature, and that cost is paid in full before exploration starts.

## 2. Goal

Start with only the `null|NULL` summary. Summarize a constructor only when a path query needs it.
To pick candidates, use type information from the recorded path constraint, matched against the OPAL
signature list.

What changes and what stays:
* The OPAL signature list, the class hierarchy and the polymorphism facts stay **static**.
* The **summaries** become lazy, because each one is a DSE run.

## 3. Refinement loop: "summarize until SAT"

For an open target node with path constraint `φ` (including the flipped last decision):

```
S := summarized signatures (global, monotone; initially {null|NULL})
loop:
    res := solve(static ∧ blueprint(S) ∧ φ)
    if res == SAT:  return model                  // o.init picks a constructor from S
    N := needed(φ) \ S                            // Sect. 4: signatures NOT statically excluded
    if N is empty:  return UNSAT                  // exact: no excluded signature can satisfy φ
    summarize(first k of order(N));  S := S ∪ new
```

Properties:

* **Complete mode.** UNSAT is reported only after every signature that is *not provably useless* has been
  summarized. Theorem 2 still holds.
* **Sound pruning.** A signature is skipped for a query only if the static facts alone prove that no summary
  path of it can satisfy `φ` (Sect. 4).
* **Reuse.** Summaries are global: one built for one query or object is reused by all later ones.

## 4. Static exclusion: signatures that cannot satisfy φ

Idea: a constructor of class `R` produces an object of class `R`. If `φ` requires `o : B` and `R ⋢ B`, the
summary of that constructor can never satisfy `φ`. We do not need to build it to know this.

**Three-valued evaluation.** For each base object `o` in `φ` (top-level objects only, as agreed) and each
candidate signature `s`, evaluate `φ` in Kleene logic (TRUE / FALSE / UNKNOWN). Atoms we can decide from `s` alone
are fixed. Every other atom is UNKNOWN. This includes field values, nested objects `o.f…`, primitives and other objects.

`s` is **excluded for `o`** if `φ` evaluates to FALSE in every *abstract case* of `s`:

| Abstract case of `s` (root class `R`) | `o = null` | `o.cls` | `o.err` |
|---|---|---|---|
| `null\|NULL` | TRUE | `"null"` | `""` |
| normal termination | FALSE | `R` | `""` |
| error `E`, for each `E ∈ errors(s)` (OPAL, Sect. 5) | UNKNOWN | UNKNOWN | `E` |

Decided atoms (all over static data):

| Atom | Value in a case with known `o.cls = R` |
|---|---|
| `(obj.extends o.cls "T")`, `instance_of` | `R ⊑ T` (class hierarchy; `"null" ⊑ T` is TRUE) |
| `(obj.method.of o.cls "m" "d" "D")` | `m d` resolves to `D` on `R` (PolymorphyInformation) |
| `(= o.cls "T")` | `R = T` |
| `(= o null)` | from the table |
| `(= o.err "E")` | from the table |

`needed(φ) = ⋃_o { s | s not excluded for o }`. A signature excluded for every object in `φ` cannot appear in any
model, so leaving it out keeps UNSAT exact.

Why this works well with the trace facts:
* **The type bound.** SPouT records the bound of `nondetObject(T)` as an assumption `o : T`. The same evaluation
  then automatically drops every signature from other call sites or outside the bound.
* **Unsatisfiable branches.** Example: the negated branch of `checkcast B` after `instanceof B`. The bound and the
  `instanceof` fact usually exclude everything, so we get UNSAT without summarizing anything.
* **Error branches.** For `o.err = "E"`, only signatures with `E ∈ errors(s)` survive.

**Order inside `N`.** Prefer signatures whose result is TRUE rather than UNKNOWN for the flipped decision. Then prefer
smaller nesting depth, then fewer parameters. These are cheap DSE runs and give simple test inputs. `k` is configurable.

**Implementation.** The evaluation is a small visitor over the jconstraints expression types used in traces (`Negation`,
`PropositionalCompound`, `FunctionExpression`, equalities with constants). Any unknown node type evaluates to UNKNOWN,
so the analysis stays sound when the trace format grows.

## 5. Constructor errors: static numbering via OPAL

This replaces the "growing DecisionNode" idea from v1.

* OPAL infers `errors(s)` for each signature. For a nested signature this is the union over all constructors in it.
  The global list `possibleErrorsWithInConstructors` is fixed before exploration and sorted, so the
  `(= o.err …)` decision keeps a static `branchCount`. `TraceParser` and `DecisionNode` need no structural change.
* The ε-branch can stay at id `count-1`, because the count no longer changes.
* An over-approximation of the errors is fine for correctness. Each spurious error branch costs one query, which
  §4 usually rejects without summarizing anything: no surviving `s` has `E ∈ errors(s)`, or the ones that do get
  summarized and turn out UNSAT.
* The exception names must match what SPouT reports (JVM internal names, e.g. `java/lang/AssertionError`).
* Open point: should implicit runtime exceptions (NPE, ArithmeticException, …) be included? OPAL's
  thrown-exceptions analysis reports them, but this can inflate the list.

## 6. Code changes (sketch)

| File | Change |
|------|--------|
| `preprocessing/ConstructorSummaryManager.java` | Split into a signature catalogue (eager, with root class, depth and `errors(s)`) and a summary store `Map<signature, List<String>>` (lazy). `buildBlueprint()` is built from the current store. `declarationsOfBluePrint` and `objectsWithinAllTraces` are updated incrementally. Cache **per signature**. |
| new `preprocessing/SummaryRelevance.java` | Kleene evaluator and `needed(φ)` / ordering (Sect. 4). |
| `preprocessing/Opal.java` | Signature parsing (root class, depth), `isSubtype(R, T)`, method-resolution lookup, `errors(s)` (Sect. 5). |
| `tree/ConstraintsTree.java` | Move the object-specific part of `updateContext` and `solve` into a `solveWithSummaries(path, val)` refinement loop (Sect. 3). |
| `trace/TraceParser.java` | Use the static error list (no logic change beyond its source). |
| `Config.java` | `dse.summaries.mode = eager \| incremental` (default `eager`, so the paper's results stay reproducible), `dse.summaries.batch = k`. |

Not in scope for now: logging and statistics (added manually later), and end-to-end runs (they need SPouT and the
executor infrastructure). Unit tests cover the evaluator, the ordering and the store.

## 7. Implementation status and the OPAL error inference

### Implemented (step 1)

| Class (`tools.aqua.dse.preprocessing`) | Role | Test |
|---|---|---|
| `ConstructorSignature` | Parses OPAL signatures into root class, descriptor, arguments, depth and nested constructors. Orders them simplest first. | `ConstructorSignatureTest` |
| `StaticTypeFacts` | The `obj.extends` and `obj.method.of` tables plus `errors(s)`. For nested signatures, `errors(s)` is the union over all constructors in `s`. | – |
| `SummaryRelevance` | Kleene evaluator, `isExcluded`, `relevantFor` (per object, for asserting only the relevant summaries), and `needed` (ordered: decides the target first, then simplest first). | `SummaryRelevanceTest` (Fig. 2 hierarchy, constraints parsed with the real SMT-LIB parser) |
| `SummaryStore`, `GeneratedSummary` | Catalogue plus lazily computed summaries, global and monotone. `blueprint(signatures)` renders only the requested summaries. | `SummaryStoreTest` |
| `ConstructorErrors`, `ConstructorErrorAnalysis`, `Opal.inferConstructorErrors` | OPAL error inference, see below. | `ConstructorErrorAnalysisTest` (classes compiled at test time and analyzed against the JDK's `java.base.jmod`) |

Refactorings that do not change behavior:
* `Opal.extendsRelation(Set)` is now the single source of the `obj.extends` relation. `generateExtendsSummary` renders
  it and `StaticTypeFacts` evaluates it, so the solver and the exclusion check always agree.
* `ConstructorSummaryManager.summarizeSignature(String)` is the per-signature summarization step. The eager blueprint
  is built from it, and it is also the `SummaryStore.Generator` for incremental mode.
* `Opal(String classPath, File javaBaseJmod)` lets tests use a JDK's jmod instead of the bundled resource.

### Error inference (`ConstructorErrorAnalysis`)

Call: `opal.inferConstructorErrors(signatures, Mode.DECLARED_AND_LANGUAGE)` returns a `Map<"Class|Descriptor",
ConstructorErrors(types, complete)>` for every constructor that occurs in the signatures, root or nested.

**Loading the JDK.** The analysis runs on a second OPAL project in which `java.base` is loaded **with method bodies**
(`Java17Framework`, `libraryClassFilesAreInterfacesOnly = false`). This lets it follow all code reachable from the
constructor, including JDK code. The main project still loads the JDK as interfaces only, so the RTA call graph
stays small.

**How it works.** For each method with a body, the analysis walks OPAL's three-address code (`LazyDetachedTACAIKey`,
l1 abstract interpretation, which removes dead code and knows that `this` and fresh objects are non-null). It then
collects, per statement:

| Statement | Exceptions |
|---|---|
| `throw e` | type of `e`, plus NPE if `e` may be null |
| method or constructor call | escaping exceptions of all targets (`Call.resolveCallTargets`), plus NPE if the receiver may be null |
| everything else (language exceptions) | `instruction.jvmExceptions()` (`ArithmeticException`, `ArrayIndexOutOfBounds…`, `ClassCast…`, `NegativeArraySize…`, `ArrayStore…`, NPE). NPE is dropped when the dereferenced value is known to be non-null. |

Further rules:
* Exceptions that are definitely caught by a handler covering the pc are removed.
* Recursion is a real fixed point: monotone iteration, each method computed at most once per iteration.
* Not modeled: implicit JVM `Error`s (linkage and VM errors) and `IllegalMonitorStateException` on `return`.
* Constructors of `Object` and of `Throwable` and its subclasses count as non-throwing.

**Modes.** They differ only at the places where the analysis is imprecise:
* native or abstract methods without a body,
* `invokedynamic`,
* unresolvable calls,
* `throw` of a value whose type is only known as an upper bound,
* more than `maxCallTargets` targets (default 32): the targets' declared exceptions are used,
* more than `maxMethods` analyzed methods (default 20,000): declared exceptions from then on.

| Mode | At imprecise places |
|---|---|
| `DECLARED_AND_LANGUAGE` | declared (`throws`) exceptions; the result counts as complete |
| `WITH_UNKNOWN` | the same, and the result is marked incomplete ("other exceptions possible"), which disables error-based pruning for that signature |

**Measurements** (test JDK 17, laptop):
* Loading `java.base` with bodies: about 1 s.
* `Integer.parseInt(s)`: 70 methods, 85 ms. The result contains `NumberFormatException`.
* `Files.readString(path)`: hits the 20,000-method budget, about 8 s, 71 exception types.

Library-heavy constructors therefore inflate the error list. Many of the types come from explicit `throw`s deep in
the JDK (`LinkageError`, `OutOfMemoryError`, `ZoneRulesException`, …). The analysis is shared across all
constructors of one run, so methods that several constructors reach are analyzed only once.

Sorted union over all signatures → the static `possibleErrorsWithInConstructors`. The `err` decision then has a
static `branchCount`.

**Robustness suggestion (step 2).** An error that the analysis missed would get id −1 in `TraceParser`, and the
assertion there would fail. Proposal: reserve one extra **"other error"** branch, whose condition is
`¬(err = ε ∨ err = E1 ∨ … ∨ err = En)`, built by DSE. Then an unpredicted error still lands on a well-defined branch.
The count stays static, and the run reports the analysis gap instead of crashing.

### Next (step 2)

* `Config`: `dse.summaries.mode = eager | incremental`, `dse.summaries.batch`, `dse.errors.mode = declared | unknown`, `dse.errors.maxMethods`, `dse.errors.maxCallTargets`.
* `ConstructorSummaryManager`: in incremental mode, build the `SummaryStore` (catalogue from OPAL, generator =
  `summarizeSignature`), the `StaticTypeFacts` (from `StaticManager` and the error map), and summarize only `null|NULL`
  up front.
* `ConstraintsTree`: the refinement loop. Per object, assert `(or blueprint(relevantFor(φ, o)))`; then
  `needed(φ) \ S` decides between "summarize next batch and retry" and "exact UNSAT".
* `TraceParser`: static error list plus the "other" branch.
