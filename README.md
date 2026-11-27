# alloy-instance-checker-generator

Standalone Java scripts using Alloy 6.2.0 (Java 17+):

- `InstanceGenerator`: generate XML instances by solving.
- `InstanceChecker`: fix an XML instance in the model and check it by solving.
- `InstanceEvaluator`: evaluate native Alloy formulas against XML without solving.

Compilation and launch instructions are in each source file's header.

## Native evaluation

`InstanceEvaluator MODEL XML [-cmd=N|--command=N] [--no-overflow]`

By default, evaluates a synthetic `run {}` and signature facts. `-cmd=N` selects
an explicit model command instead (zero-based); signature facts are still included.
Runs evaluate their resolved body and facts; checks evaluate the **negated
assertion**, so true means this XML is a counterexample. Signature facts hold
throughout temporal traces; other formulas start at the initial state.

Arithmetic uses the XML bitwidth and wraps unless `--no-overflow` is supplied.
Exit codes: **0** true, **1** false, **2** input/evaluation error.

## Semantic gaps

**Native true does not certify instance validity.** Alloy's solver adds declaration
constraints and bounds separately; the public formula translation API does not
supply that complete validation setup. This script evaluates the available
formulas without reconstructing those constraints.

These examples assume otherwise unconstrained models:

| Model or selected command | XML contents | Native result |
| --- | --- | --- |
| `sig A { f: A }` | one A, empty f | true: implicit field `one` omitted |
| `sig A { f: A -> one A }` | one A, empty f | true: arrow multiplicity omitted |
| `one sig A {}` | two A atoms | true: signature multiplicity omitted |
| `fact { all a: A \| one a.f }` | one A, empty f | false: explicit formula checked |
| `run {} for exactly 0 A` | one A | true: command scope omitted |
| `run { no A } for exactly 0 A` | one A | false: command body checked |

Field/signature `some` and `lone`, other structural declaration constraints, and
command bounds are likewise not comprehensively validated. `InstanceChecker`
rejects the declaration violations above, but uses its own synthetic run and
does not validate an original command's scopes either.

Selecting `run p` implicitly existentially quantifies the predicate's parameters.
For example:

```alloy
pred p[a: set A] { no a }
run p  // resolved as: some a: set A | no a
```

Although the source command has no written quantifier, its resolved formula
quantifies over sets. Native translation raises `Higher order declaration` for
this command (also for `lone`/`some` parameters). This is an evaluation-path
limitation, not a claim that Alloy cannot solve the command.

With `a: A` (implicit `one`), `run p` instead asks for a single atom satisfying
`no a`, so it is false. In contrast, `run { p[none] }` supplies an argument and
introduces no implicit parameter quantifier: it evaluates to true with either
declaration. Alloy inlines the call without inserting parameter multiplicity checks.
