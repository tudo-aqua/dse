# DSE

DSE (dynamic symbolic execution) is a generic dynamic symbolic execution.

Some components of DSE were originally developed as part of JDart, first 
at NASA Ames Research Center, and later at TU Dortmund University.
Original JDart repositories can be found here:

- https://github.com/psycopaths/jdart
- https://github.com/tudo-aqua/jdart


## Installation

### Dependencies

- JConstraints: https://github.com/tudo-aqua/jconstraints

### Building

- Install JConstraints to local maven repository (```./compile-jconstraints.sh```)
- Clone this repository
- Run ```mvn clean package```


## Usage

- Run ```java -jar target/dse-...-.jar```
- Parameters

```
 -D <property=value>   use value for given property
 -f <file>             use given properties file
 -h                    show help

dse properties:

 dse.executor          executor command (e.g. java)
 dse.executor.args     executor args (e.g. -cp ... Main)
 dse.b64encode         base64-encode concolic values passed to executor: true / false (default)
 dse.explore           one of: inorder, bfs, dfs (default)
 dse.terminate.on      | separated list of: assertion, error, bug, completion (default)
 dse.dp                jconstraints id of solving backend
 dse.dp.incremental    use incremental solving: true / false (default)
 dse.bounds            use bounds on integer values when solving: true / false (default)
 dse.bounds.step       step width (increase of bounds) when using bounds iteratively
 dse.bounds.iter       no. of bounded solving attempts before dropping bounds
 dse.bounds.type       fibonacci: uses fibonacci seq. from index 2 (1, 2, 3, 5, ...) as steps
```

## Trace Language

### Grammar

DSE observes the standard output of the executor command for lines of the 
following format:

```
trace ::= (decision|declaration|error|abort|assumption)* 
    "[ENDOFTRACE]\n"

declaration ::= "[DECLARE] " <SMTLib variable declaration>  "\n"

decision ::= "[DECISION] "  <SMTLib assertion> 
    " // branchCount=" <int> ", branchId=" <int> "\n"

error ::= "[ERROR] " <cause, e.g., exception class>  "\n"

abort ::= "[ABORT] " <causen>  "\n"

assumption ::= "[ASSUMPTION] " <SMTLib assertion> 
    " // sat=" <true|false>  "\n"
```

### Concrete Example of expected Trace:

```
[DECLARE] (declare-fun __string_0 () String)
[DECISION] (assert (= __string_0 "whoopsy")) // branchCount=2, branchId=0
[ERROR] java/lang/AssertionError
[ENDOFTRACE]
```

## Executor Parameters set by DSE

DSE passes values to be seeded to the executor. Values are seeded as 
return values of calls to methods of the ```tools.aqua.concolic.Verifier``` 
class from here: https://github.com/tudo-aqua/verifier-stub

```
-Dconcolic.bools=[comma separated list of Boolean values]
-Dconcolic.bytes=[comma separated list of byte values]
-Dconcolic.chars=[comma separated list of char values]
-Dconcolic.shorts=[comma separated list of short values]
-Dconcolic.ints=[comma separated list of int values]
-Dconcolic.longs=[comma separated list of long values]
-Dconcolic.floats=[comma separated list of float values]
-Dconcolic.doubles=[comma separated list of double values]
-Dconcolic.strings=[comma separated list of string values]
```

When using the ```dse.b64encode``` option, comma separated values are 
base64-encloded individually. This indicated to an executor 
by prepending a list of values with [64]. E.g. ```-Dconcolic-ints=[b64]...```

## Example

Assume the following Java class:

```java
import tools.aqua.concolic.Verifier;

public class Main {

    public static void main(String[] args) {
        int i = Verifier.nondetInt();
        int[] arr = new int[10];
        arr[2] = i;
        if (40 > arr[2]) {
            assert false;
        }
    }
}
```

And the following executor script that uses [SPouT](https://github.com/tudo-aqua/spout) as a concolic executor:

```bash
#!/bin/bash
export JAVA_HOME=[path-to-graalvm-ce-java11-21.2.0]
[path-to-graalvm-espresso-native-ce-java11-21.2.0]/bin/java -truffle -ea $@
```

We anaylze this class with DSE like this: 

```bash
 java -cp target/dse-0.0.1-SNAPSHOT-jar-with-dependencies.jar tools.aqua.dse.DSELauncher \
     -Ddse.executor=./executor.sh \
     -Ddse.executor.args="-cp [classpath]  Main" \
     -Ddse.dp=z3 \
     -Ddse.terminate.on=completion \
     -Ddse.explore=BFS
```

DSE produces the following output (removed prefix and added spaces for readability):

```
./executor.sh          -cp [classpath]  Main

Decision{condition=('__int_0' bvslt 40), branches=2, branchId=0, assumption=false}

ERROR: java/lang/AssertionError

./executor.sh     -Dconcolic.ints=41     -cp [classpath]  Main

Decision{condition=('__int_0' bvsge 40), branches=2, branchId=1, assumption=false}

OK: 


+ 0 : ('__int_0' bvslt 40)
  + ERROR[complete path:true] .  . java/lang/AssertionError
+ 1 : ('__int_0' bvsge 40)
  + OK[complete path:true] . __int_0:=41

```







