# 0017. What two ratchet checkers count: newtype companions and test suites

- Status: accepted
- Date: 2026-11-21

## Context

Since 2026-11-21 no ratchet may rise from new code; a false positive is fixed in the checker. Two checkers counted code
the coding practices allow, so any new code of that shape was a rise.

## Decision

- **`unwrap` (ContractsSuite).** A `.value` inside the indented body of a newtype's companion (`object X extends
  Newtype[…]:`, or Iron's `RefinedType`/`RefinedSubtype`) is the newtype's own raw value, where rule 1 puts it ("only
  inside the newtype's own companion and at a boundary"); the guideline inventory's G44 already exempts it. The code
  after the body counts again. The rule's older "a newtype's own file" test matched only `extends RefinedType`, so since
  the `Newtype` trait it had counted every operation written on its own type. The check reads source text, so it cannot
  tell the receiver's type: a `.value` of another newtype inside a companion body is exempt there too (G44 still counts
  it).
- **G9 (GuidelineInventory).** A test-scope type named as a test suite (`…Suite`, `…Spec`, `…Test`, `…Tests`) is no
  G9prefix/G9prefixRole finding: a suite is named for its subject, and rule 5 calls a component of an entity no property.
  Other test-scope names (`…Fixture`, `…Order`) are still found. The inventory's fixture keeps its test-scope files under
  `src/test-fixture`, because scala-cli compiles a `test/` directory apart from the rest.

Both were approved by the owner on 2026-11-21.

## Consequences

The counts fell (unwrap by the companion operations across values, api, core and server; G9prefixRole from 567 to 81).
New operations on a newtype and new suites named for their subject no longer need an exception.

## Code

`ContractsSuite.Rules.unwraps` (and its test), `tools/refactor/GuidelineInventory.scala` (`testSuiteSuffixes`),
`tools/refactor/guideline-fixture/`.
