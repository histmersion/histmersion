# Rules for fixing compile errors on this branch

The tree was moved from primitives to newtypes. Fix errors so that the code is right, not merely compiling.

- An opaque newtype's only operations are its own. A domain operation belongs on the type or its companion: when code
  does `placeId.startsWith("pleiades_")`, add an extension such as `Place.Id.isPleiades` beside the type and call it.
- Do not bridge two newtypes with `A(b.value)` in domain code. If they are the same concept, make the signature use one
  type. `.value` and constructing from a raw value are fine at real boundaries: JSON, SQL, HTTP, CLI parsing, Markdown
  text assembly, a library call, and inside the type's own companion.
- Types whose names are positional (`...Result`, `...Part1`, `...Arg1`, `...S`, `...Raw`) are leftovers: replace them with
  the real concept nearby, or the plain type if it is a private helper's text or number, and delete the definition.
- Boolean results of predicates are plain `Boolean`. Private helpers inside a newtype's own companion may use the
  underlying value. Public signatures use concepts, not primitives.
- API-layer types (`com.histmersion.api`) are unconstrained and never validate. Never make one module depend on another
  to fix an error.
- Never use `asInstanceOf`, `@unchecked`, `@nowarn`, a global given conversion between types, or `Any`. Never weaken or
  delete a test or an assertion, and never change behaviour to get a pass: the output of each expression stays the same.
- No comments explaining the change. Do not reformat lines you are not changing. Do not touch files outside your list.
- Work only in this worktree. Never touch the live server, config, database or user data, and run no real network calls.

## Behaviour reference

Earlier automated rewrites mangled some expressions, so a line that looks wrong may really be wrong even when a small fix would
compile. Commit 7e197c2b is the behaviour before all of this: read a file there with `git show 7e197c2b:<path>` (it may have
lived under another path; `git log --all --format= --name-only -- '*/<File>.scala' | sort -u` helps). Whatever you write must
compute what that version computed. Known case: `Years.parseIso` turns an astronomical year into a historical one, so it
returns `Option[catalogue.Year]` as `.map(v => fromAstronomical(Astronomical(v)))`. Report any such mangled line you fix.

## Hints

`type X = _root_.a.b.X` aliases that refer to themselves are leftovers: delete the alias and use the real type. In `*Markdown`,
`*Export` and `*Render` objects text is plain String. `Year` in the API layer is one type, `com.histmersion.api.catalogue.Year`,
base Long. A newtype that needs sorting gets `given Ordering[X] = Ordering.by(_.value)` in its own companion if that is in your
files, otherwise `.sortBy(_.value)` at the use site. Read each file's relevant parts rather than patching lines blindly.
If two types in different files are one concept, list it in your reply rather than bridging silently, and bridge only if unavoidable.

## Do not undo earlier progress

The master version is the reference for what code computes, not for how it is written. Keep lens updates (`.modify`): never turn
them back into `.copy`. Keep newtypes on public signatures: never revert a concept to a primitive because the old file had one.
Predicate results are the only signatures that go back to Boolean. Plain Boolean fields of a record keep their types.

## Test files

A test's helper takes the concept, not a primitive: `def candidate(id: String...)` becomes `def candidate(id: Place.Id...)`, and its callers pass the concept.
Keep what is asserted exactly as it is: never change an expected value, drop a check or loosen an assertion. To read text out of a newtype, use `.value`
(`md.value.contains("x")`) rather than changing what the code under test returns. If a test expects an API that no longer exists, or that disagrees
with the code under test, do not guess: compare with the behaviour reference and report it. `assertEquals` between a newtype and a literal works through
`import com.histmersion.testsupport.ComparedByValue.given`, which you may add.

## Trusting a test run

sbt 2 caches test results, even across `clean`: `test` may run a few suites or none and still exit 0. A pass counts only from `<project>/testOnly *`, and only if the total matches the expected number of tests.

## Public signatures keep their concepts

Do not delete a type from a public signature and use `String`, `Int`, `Boolean` or `Array[Byte]` instead, even when that makes the error go away. If a name is positional, replace it with the real concept: name one, defining it on the owner if none exists. Plain types are fine only in private helpers and for predicate results. Do not change behaviour to suit a type: if a value the old code accepted no longer fits, report it.

A test must not take over behaviour from the code under test. If a test expected the code to normalise, parse or check something and the code no longer does, that is a regression in src/main: report it, and do not normalise inside the test.

## Repositories and SQL (db, plugin-host)

SQL is a boundary: `Param.S(x.value)`, SQL fragments as plain String and row-to-type reads may use `.value` and raw constructors. A repository's public methods take and return concepts. Rows hold what the database holds, so reading a row must accept every value the old code accepted: a newtype with a constraint must not start rejecting stored data (read it the way the old code did and report the case). Use domain types (`com.histmersion.domain...`), never add a new reference to `com.histmersion.api...` in `db` or `plugin-host`. In a repository, a positional type such as `RecordAppliedPart1` means the real concept of that column: check the column in the SQL rather than trusting the parameter order. Multi-valued tables, `?` order and SQL text must stay byte-identical.

## Plugins

A plugin module sees only `plugin-api` (`com.histmersion.plugin...`), its own types and, for bundled plugins, `upstreams`. It never sees `domain` or `api`. Parsing an upstream's JSON and building its URLs are boundaries. Positional leftovers in `plugin-api` (`GetHeadersPart1`) stay as they are for now: use them, do not edit `plugin-api`, and list them. A plugin's own positional leftovers get real names. Tests make no real network calls.
