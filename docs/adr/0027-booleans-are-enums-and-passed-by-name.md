# 0027. A state is an enum, and a Boolean left is passed by name

- Status: accepted (owner decision 6 of 2026-11-19; queue row 16)
- Date: 2026-11-23

## Context

The owner decided on 2026-11-19 (notes/refactor-goals.md, decision 6): "Booleans: enums. An enum gives a name and lets
new states be added type-safely; raw Boolean is discouraged. Where a Boolean remains, it may be passed only as a named
argument or used to initialise a value: find a compiler/linter check (Scala 3 flag, or a scalafix rule) that rejects an
unnamed Boolean argument, and make it a check."

**No compiler flag does it.** The build's Scala (3.10.1) was asked for its options (`-W`, `-X`, `-Y` help): none
concerns Boolean or named arguments. The warning options are `-Wall`, `-Wconf`, `-Wenum-comment-discard`, `-Werror`,
`-Wimplausible-patterns`, `-Winfer-union`, `-Wnonunit-statement`, `-Wopt`, `-Wrecurse-with-default`, `-Wsafe-init`,
`-Wshadow`, `-Wtostring-interpolated`, `-Wunreasonable-catch`, `-WunstableInlineAccessors`, `-Wunused`,
`-Wvalue-discard` and `-Wwrong-arrow`, and Scala 3 has no `-Xlint`. A probe calling `f(true, 1)` for
`def f(enabled: Boolean, n: Int)` compiles cleanly under `-Wall -Werror`. Scala 2.13's `-Xlint` (2.13.18) has no such
choice either, and Scalafix ships no built-in rule for it.

## Decision

### The check

Two rules of `GuidelineLint` (`tools/lint`, a Scalafix rule over SemanticDB; docs/adr/0032), `UnnamedBoolean` and
`BooleanParam`, each finding fixed or tolerated with `@tolerated` on its definition, the suppressions only falling:

- **`unnamedBoolean`**: an argument passed by position to a `Boolean` parameter of one of our methods or constructors.
  The parameter's declared type decides, not the argument's form: a literal (`f(true)`) and a value (`f(isOpen)`,
  `new X(a && b)`) are both findings, because the owner's words allow a Boolean only "as a named argument or ... to
  initialise a value", and a variable's name at the call site is not the parameter's name (`f(open)` passed where `f`
  means `dryRun` reads well and is wrong). Counted: a case class's `apply` or an enum case, `new`, a curried list, an
  extension method's own list (its receiver is not an argument), a `using` clause written out, each element of a
  `Boolean*`. Not counted: a named argument (`f(open = isOpen)`); an operator (`&&`, `==`), whose operand has no name to
  give; a method of a library (`Option.when`, `assert`, a Java setter), whose parameter we can neither rename nor turn
  into an enum and, for Java, cannot name at all. The detail names the callee, the parameter and `literal` or `value`.
- **`booleanParam`**: a parameter declared `Boolean`: a field of a class, case class or enum case, or a parameter of a
  def that is neither private (`private`, `private[this]`; a package-qualified one counts, a repository's methods are
  `private[feature]`), local nor an override (its type is its parent's). Each is a state the model names with a
  Boolean instead of an enum; the count is the inventory of decision 6, and it falls as states become enums.

Both keys are new, so the merge base's file lists neither. Their first counts are proven by the merge-base tree
(ADR 0023): the branch that adds them changes no input of the inventory but the checker itself, and the update mode
writes the counts. The code that lowers them follows on a branch of its own.

The inventory's fixture pins every case exactly in `expected-findings.tsv` (`Switches.scala`). Its usual
`// assert:` markers are comments, which G22 counts, so the fixture's config names these two rules `reportOnly`: they
are written to the report but not emitted as lints, and the report is the fixture's exact assertion.

### What a Boolean becomes

- A Boolean that means a state becomes an enum whose cases name the states (`enabled: Boolean` becomes
  `Switch.{On, Off}`), with its operations on the companion. On the wire it is a string (ADR 0026); in the database the
  column may stay `boolean` with the enum's `DbCodec` mapping it (no migration, the data untouched), or become text
  with a Flyway migration that keeps the data.
- An enum that was a Boolean in JSON (a wire record, a stored record or setting, a bundle, a checkpoint) has a
  companion that extends `TwoStates` (`modules/api`, `api/json/TwoStates.scala`; the api layer owns the wire's codecs,
  owner 2026-11-28; the codec itself is `NewtypeCodecs.twoStates`, at the JSON boundary): its codec writes the case's
  name, and reads the Boolean an earlier version wrote as the state it meant,
  `true` the first case and `false` the second, so stored JSON and older clients still read. A query parameter that
  was a Boolean stays `true`/`false` on the wire and is mapped to the enum where the endpoint is declared. A `boolean`
  column keeps its data: `NewtypeColumns.twoStates` maps it (db layer), and `domain/TwoStateTransformers` gives Chimney
  the conversion between such an enum and a domain Boolean newtype (domain layer, no codec). The lint rule `derives`
  reads an instance lifted from `TwoStates` as it reads one lifted from a newtype's base, and lists
  `api/json/TwoStates.scala` as the codecs' home.
- A Boolean newtype that served several parameters of different meaning becomes one enum per meaning: `Api.Apply`
  (apply or dry run; the `dryRun` parameters of `importBundle` and `fetchExternalPlaces` map `true` to `DryRun`),
  `Api.Strictness` (`strict`), `Api.Fetching` (`init`'s `fetch`), and `reocr`'s `quality` takes
  `Ingest.Params.Quality`.
- A Boolean that is a computed answer (`def isEmpty: Boolean`, a predicate passed to `filter`) stays: it is a result,
  not a parameter, and neither key counts it.
- Where a Boolean parameter stays (a library's callback, a pure helper), its callers name it.

## Inventory (merge base ed3120e41, the first counts)

Counted by the two keys over every module, main and test (no other module has a finding):

| module | booleanParam main | booleanParam test | unnamedBoolean main | unnamedBoolean test |
|---|---:|---:|---:|---:|
| api | 26 | 0 | 29 | 8 |
| chimney-iron-tests | 0 | 1 | 0 | 1 |
| cli | 126 | 1 | 149 | 37 |
| core | 9 | 1 | 4 | 20 |
| db | 15 | 3 | 26 | 10 |
| files | 5 | 0 | 2 | 11 |
| plugin-api | 0 | 1 | 0 | 1 |
| plugin-host | 1 | 0 | 17 | 1 |
| plugin-testkit | 1 | 0 | 5 | 0 |
| plugins/calibre | 0 | 3 | 0 | 1 |
| plugins/fetchers | 0 | 0 | 1 | 0 |
| plugins/media | 1 | 0 | 1 | 0 |
| server | 82 | 13 | 117 | 33 |
| web | 67 | 1 | 109 | 46 |
| **total** | **333** | **24** | **460** | **169** |

Of the 333 in main code, 178 are fields (a class, case class or enum case) and 155 parameters of defs; 36 are in the
implementation of a service, repository or controller (a file `…Service`, `…Repo`, `…Controllers`; an abstract
declaration in a service trait is not counted, its `Implementation` and `InMemory` are). Of the 629 positional
arguments, 191 are literals and 438 values. The
largest groups: the CLI's command records (`noWait` ×14, `dry`/`dryRun`/`apply` ×19, `force`, `refresh`, `all`), the
server's `dryRun` ×7 and `all` ×5, and the web page's view state (`reduced`, `on`, `soft`, `local`). The callees most
often given a positional Boolean: `AcquisitionControllers.step` (18), `PluginHost.status` (11), the CLI's command
cases (`Import`, `Covers`, `Ingest`, `Fetch`, `Rebuild`), `SourcesMarkdown.source` (9) and `ListFilters.many` (8).

## The first migrations

- **`Cli.Following` (`Follow | Return`)** for `--no-wait`: fourteen command records and the upload and events
  helpers (the Boolean newtype `Cli.NoWait` gone); `CommandLine.noWait(help)` parses the flag once.
- **`Cli.Writes` (`DryRun | Apply`)** for `--dry-run` and `--apply`: nineteen command records (the Boolean newtype
  `Cli.DryRun` gone); `CommandLine.dryRun(help)` and `applyFlag(help)` decide each flag's polarity where it is parsed.
- **`RejectedLinks` (`Hidden | Listed`) and `EventsToCheck` (`Unchecked | Every`)** for the event links' service.
- A repository keeps a Boolean it binds as an SQL parameter when its first parameter would otherwise be one of our
  types: the guideline inventory's G3misplaced counts such a repository method (`EventLinkRepo.eventsToCheck`).

No wire, column or web change was needed for these; nothing was migrated in the database. After them (stacked on the
check): booleanParam main 333 → 293, unnamedBoolean main 460 → 396, test 169 → 147.

## What remains

- The web page's view state (67 fields and parameters), whose change runs the browser suite.
- The wire's Boolean newtypes and query parameters, which become string enums (ADR 0026): `Api.Apply` is also used for
  the `dryRun` query parameter of `importBundle` and `fetchExternalPlaces`, so a value named "apply" carries "dry run"
  there; an enum of the two states removes the inversion.
- The autopilot's switch and drain (`AutopilotSet.On`, `DrainSet.On`, `setDrain(on)`), across the wire, the CLI, the
  MCP tool and the web page.
- The server's MCP tool inputs (`dryRun`, `fix`, `all`, `remove` …), the managers' `upsert(reopen)`,
  `openOf(focusHeld)` and `hints(live)`, the work lists' `list(all)`, and `AcquisitionControllers.step`, the callee
  given most positional Booleans.

## Consequences

A new positional Boolean argument or a new Boolean field is a finding that fails CI's `lint` job. `Boolean`
newtypes (`object On extends Newtype[Boolean, Pure]`, 636 in the modules) are named, so neither key counts them; they
are two-state types that an enum would describe better, and are left to the newtype census (queue row 17).

## Code

`tools/refactor/GuidelineInventory.scala` (`unnamedBoolean`, `booleanParam`, `reportOnly`), its fixture
`tools/refactor/guideline-fixture/` (`Switches.scala`), `tools/lint/rules/Guidelines.scala`; the migrations in
`modules/cli/src/main/scala/com/histmersion/cli/Cli.scala` (`Following`, `Writes`) and `CommandLine.scala`
(`noWait`, `dryRun`, `applyFlag`), and `modules/core/src/main/scala/com/histmersion/domain/catalogue/evidence/`
(`RejectedLinks`, `EventsToCheck`).
