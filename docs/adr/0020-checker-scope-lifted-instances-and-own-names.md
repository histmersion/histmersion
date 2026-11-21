# 0020. What two more ratchet checkers count: lifted newtype instances and a file's own type names

- Status: accepted
- Date: 2026-11-21

## Context

No ratchet may rise from new code, and a checker's false positive is fixed in the checker (as in ADR-0017). The
structured log's new code met two more checkers that counted code the coding practices allow.

## Decision

- **`derives` (ContractsSuite).** A given that lifts a newtype's instance from its base type's
  (`given x: [T] => (m: RefinedType.Mirror[T]) => (base: Codec[m.BaseType]) => Codec[T]`, recognised by
  `RefinedType.Mirror[`) is no hand-written codec. It is how every newtype gets its instance (G45's lifted type class),
  and no derives clause can request one, since an opaque type has none. A codec of a record written by hand is still
  counted.
- **`direction` (ArchitectureSuite).** A type name a file defines itself, at any depth (an `object Trace` inside a
  record's companion), is that file's own type there: the bare name shadows a top-level type of the same name in another
  place, so its uses are no dependency on that place.

Both were approved by the owner on 2026-11-21.

## Consequences

Newtype text codecs (ADR-0019) and nested companion types with common names no longer need an exception. Both checks
read source text: a lift is recognised by its mirror parameter, a definition by its keyword at the start of a line.

## Code

`ContractsSuite` (`NewtypeLift`, and its test), `ArchitectureSuite` (`ownNames`, and its test).
