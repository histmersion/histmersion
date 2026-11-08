# Testing a rule against samples

A rule is done when it turns a set of **input** samples into the **expected** output, and the result compiles. Both are small scala-cli projects that you can run `run-rule.sh` on.

## The input project

```
samples/
  project.scala   //> using scala 2.13.16
                  //> using options ...   (the real project's flags, see below)
                  //> using dep ...       (the libraries the samples use)
  Samples.scala   the samples, one per inventory shape, tagged
  Stubs.scala     minimal stand-ins for project code the samples need (optional)
```

- **Use the inventory from [exploring.md](exploring.md):** at least one sample per shape, and two or three for the shapes with many occurrences.
- **Copy samples from real code**, with their formatting, comments and odd spacing. Shorten them, but don't "clean them up". Messy code is where token patches break.
- **Add look-alikes**: the same method name on another type, a user-defined method with the same name, an already-migrated call. They must stay UNCHANGED.
- **Add the edge cases** the inventory flagged: suppressed code (`// scalafix:ok`, `@SuppressWarnings(Array("scalafix:MyRule"))`), nested and chained occurrences, occurrences inside string interpolation, Scala 3 syntax if the codebase uses it.
- **Tag every sample** with a comment so humans and the rule author know the intent:

  ```scala
  // S1 REWRITE
  def s1(m: Map[Int, Int]) = m.mapValues(_ + 1)
  // S2 LINT (depends on the view being lazy)
  val cached = prices.mapValues(expensive) // assert: MyRule
  // S3 UNCHANGED (MapView#mapValues)
  def s3(m: Map[Int, Int]) = m.view.mapValues(_ + 1)
  // S4 DECIDE (only iterated: drop .toMap?)
  def s4(m: Map[Int, Int]) = m.mapValues(_ + 1).foreach(println)
  ```

- **Use the real compiler flags.** Copy `scalacOptions` from the real build into `//> using options`, especially `-Xfatal-warnings`/`-Werror`, `-Wvalue-discard`, `-Wunused`, `-Xsource:3` and `-deprecation`. A rewrite that's fine with default flags can fail the real build.
- The input must **compile** before the rule runs. If you can't compile a sample without half the codebase, stub the types it needs in `Stubs.scala`.

## The expected output

```bash
cp -R samples expected
# edit expected/Samples.scala: apply each REWRITE by hand, leave LINT and UNCHANGED as they are
scala-cli compile expected
```

- The **user owns** the expected output. Propose it if it's missing, but get it confirmed, especially for DECIDE cases.
- Never edit the expected output to make a failing test pass. If you think it's wrong, say why and ask.
- LINT samples stay unchanged in the expected output. Mark them with `// assert: MyRule` at the end of the line where the lint is reported, usually the line of the method name. `run-rule.sh` then checks that each marked line gets a lint, and that no other line does (so a suppressed sample must have no lint). `// assert: MyRule.id` expects a specific `Diagnostic` id. `// assert:` with no name accepts any lint.
- The markers are comments, so they stay in the expected output too. The same convention works in scalafix-testkit (see below).
- **Compile the expected output.** If it doesn't compile, fix it (with the user) before writing the rule.

## Running

```bash
scripts/run-rule.sh rules/MyRule.scala samples expected
```

| Output | Meaning |
|---|---|
| `== Changes made by the rule` | the diff from input to rule output. Read it, even when everything is OK |
| `OK: the result matches the expected output` | text matches exactly, including whitespace and imports |
| `FAIL: the result differs` + diff | `-` lines are expected, `+` lines are what the rule produced |
| `OK: the lints match the // assert: markers` | every marked line got a lint, and no other line did |
| `missing lint: File.scala:25 [MyRule]` / `unexpected lint: ...` | a marked line without a lint, or a lint nobody asked for |
| `OK: the rewritten sources compile` | compiled with the input project's flags |
| `error: the project doesn't compile` | the **input** doesn't compile, so fix the samples first |
| `note: scalafix exited with 64` | a lint with `Error` severity was reported. That's fine if the lints match the markers |
| `FAIL: scalafix failed with exit code ...` | scalafix itself failed: an unknown rule, or the rule doesn't compile. Read the messages above |

Exit code 0 means every check passed. Iterate on the rule until you get it.

## Don't overfit

- Write matchers for **shapes**, not for samples. A condition like `recv.syntax == "m"` or a check of the line number means the rule only passes the test.
- Keep a few real occurrences **out of** the samples. After the rule passes, run it on the real code ([applying.md](applying.md)) and check that those were handled correctly too.
- If the real run finds a shape the samples don't cover, add a sample for it, get its expected output confirmed, and run the tests again. The samples only grow.

## Keeping the tests in a repository: scalafix-testkit

For a rule that lives on (in a rules repository or shared across projects), use scalafix-testkit instead of `run-rule.sh`. Start from the template, `sbt new scalacenter/scalafix.g8`, which creates:

- `rules/`: the rule as a normal sbt module, plus `META-INF/services/scalafix.v1.Rule`,
- `input/`: samples, each file starting with a `/* rule = MyRule */` comment,
- `output/`: the expected output, the same files without the comment,
- `tests/`: a suite that runs every input file and compares it with its output.

Lints are asserted inline in the input with a trailing comment: `prices.mapValues(expensive) // assert: MyRule`. Run the suite with `sbt tests/test`. The same rules apply: samples from real code, the user owns `output/`, and nothing is done until the suite is green.
