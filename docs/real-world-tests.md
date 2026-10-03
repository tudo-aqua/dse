# Real-world test cases: library methods with concolic object parameters

## Results

We added five test cases (`DSEIntegrationTest`, group `real-world`). Each explores a method from a widely used
library. The method takes object parameters, and the test creates those objects with `Verifier.nondetObject`.
The libraries are commons-lang3 3.21.0, commons-collections4 4.6.0 and the JDK class library. Each library is
bundled as a fat jar built from Maven Central with `e2e/libs/build-fat-jar.sh`, and OPAL analyzes its classes as
project classes.

The programs exercise object features only:
- dynamic dispatch over library interfaces (`Comparable`, `Comparator`, `Predicate` with 18 constructor signatures);
- type tests and inheritance (the asymmetric `equals` of a subclass, through `java.util.Objects.equals`);
- null handling, and identity vs. equality;
- the choice among a library class's constructors.

All five tests pass in both modes. Eager and incremental summaries find the same executed paths with the same
verdicts.

Incremental summaries end up summarizing every constructor in these tests: the catalogues are small (2–17
constructors), and the explored paths need every constructor alternative at some point. Incremental mode therefore
saves no summaries here. It is 1–9 s slower than eager mode, mainly because its error analysis loads the JDK with
method bodies and runs into its budget of 20,000 methods (about 8–9 s set-up). Solver time is negligible in both
modes (at most 3.2 s, in the `Predicate` test).

| Test (library method) | Executed paths | Verdicts (both modes) | Constructors (summarized eager / incremental) | Total time eager / incremental (set-up) |
|---|---|---|---|---|
| JDK `Objects.equals(p, q)` (`Point` / `ColorPoint`) | 18 | 16 OK, 2 assertion violations (asymmetric `equals`) | 2 (2 / 2) | 6 s (3 s) / 7 s (3 s) |
| lang3 `MutableInt.equals(b)`, `compareTo(b)` | 6 | 2 OK, 1 assertion violation (equal but distinct objects), 3 NPE | 3 (3 / 3) | 5 s (3 s) / 14 s (12 s) |
| lang3 `ComparableUtils.is(a).between(b, c)` | 17 | 10 OK, 7 NPE; assertion unreachable (as expected) | 3 (3 / 3) | 8 s (3 s) / 17 s (11 s) |
| collections4 `NullComparator.compare(a, b)` | 10 | 5 OK, 1 assertion violation (`nullsAreHigh`), 4 NPE | 7 (7 / 7) | 9 s (5 s) / 19 s (13 s) |
| collections4 `Predicate.evaluate(o)` (twice, same object) | 20 | 6 OK, 1 assertion violation (`UniquePredicate`), 12 NPE (null children), 1 `FunctorException` (`ExceptionPredicate`) | 17 (17 / 17) | 19 s (8 s) / 28 s (12 s) |

Times are wall-clock time of `DSE.executeAnalysis`, including set-up, on an Apple Silicon laptop, with nesting
depth 1.

An earlier version of this table reported eager times of up to 927 s. Those were an artifact of the test harness:
- `DSEIntegrationTest` forwarded its output to the console byte by byte, and Surefire records a stack trace for
  every write to the console.
- Eager mode printed the whole static SMT code (about 150 KB for collections4) for every query.

Both are fixed.

## Fixes in DSE found with these tests

- **Boolean constructor parameters:** the primitive variables of summaries were renamed per object for every type
  except `bool`. A boolean constructor argument then reached SPouT as the variable name (DIVERGED), and it clashed
  with the program's own nondet booleans.
- **Unsupported constructors:** the signature generation now skips constructors with `String` or array parameters,
  and OPAL's synthetic lambda classes (`…$Lambda`), which don't exist at run time.
- **Robustness:** a trace that ends where an earlier trace with the same decisions continued now marks the target
  BUGGY instead of crashing the exploration.
- **Eager mode, every query:** it regenerated the static SMT code (class hierarchy and polymorphism tables) only to
  print it, which costs 12–41 ms for the small benchmarks and about 0.3 s for collections4. It no longer does this.
  The paper-experiment numbers for eager mode (`e2e/experiments`) still include this overhead, roughly 5% per run.

## What currently does not work because of missing SPouT features

These limitations restricted the choice of test cases. The affected constructors are skipped, or the library was
not used.

1. **Strings as symbolic objects.** SPouT treats strings like primitive values. A `String` constructor argument can
   only be `null`, and a symbolic `String` object stops the recording ("Strings are currently not supported as
   symbolic objects"). DSE therefore skips constructors with `String` parameters, e.g. `MutableInt(String)` and most
   `Message` implementations of log4j. String-centric libraries such as log4j-api are effectively out of scope.
2. **Type conversions between strings and numbers.** The `Integer.parseInt` / `Double.parseDouble` / `Float.parseFloat`
   substitutions stop the recording for concolic strings ("Concolic type conversion from string to int is not
   supported"). For invalid input, e.g. `null`, they throw a **host** exception instead of a guest
   `NumberFormatException`/NPE. That aborts the analyzed program without an `[ERROR]` line, and it yields a wrong
   constructor summary (a constructor that throws is summarized as successful). Fix: rethrow as a guest exception
   (`meta.throwExceptionWithMessage`) in `SPouT.parseInt` etc.
3. **Number-to-string formatting with symbolic values.** For example `Long.toHexString` in the message of
   `BitField`'s `IllegalStateException`. When an array is created with a symbolic length, the length annotation stays
   attached to the array object. A later null check of the array is then recorded as `(= <32-bit int expression>
   null)`. jconstraints cannot parse this ill-typed constraint, so DSE gets no trace and the path becomes BUGGY. This
   affected `BitField(long)` with masks wider than 32 bits, so `BitField` was dropped.
4. **Arrays as constructor arguments.** SPouT's constructor instantiation (`Config.parseObjectValue`) handles only
   primitives and objects, and arrays fail with a host `ArrayIndexOutOfBoundsException`. This rules out varargs
   constructors such as `AllPredicate(Predicate...)`. The signature format has no encoding for arrays either.
5. **Native / substituted JDK methods.** SPouT drops annotations when calling some of them ("removing annotations
   before calling substituted/native method `System.arraycopy`"). Symbolic information is lost there. This did not
   change any verdict in our tests, but it is a source of imprecision.
6. **Not in the artifact's SPouT:** the property `concolic.max.object.annotation.depth`, which DSE passes. It is
   ignored.
