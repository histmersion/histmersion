# Property-flow fixture origins

These are synthetic test origins, not observations of production history. The
fixture supplies the same generated-member provenance shape consumed by the
production rule. Unknown deliberately has no origin. The index incorporates these
origins through the existing appliedPlans mechanism; the empty plan changes no file.

The positive golden replaces five empty Pure helper companions with aliases to the
constructor property's type and companion. Runtime assertions require exact type
identity, unchanged values, collection projections and constructor behavior. The
negative inputs intentionally do not compile: conflicting property targets, absent
provenance, a different parameter name, a transformed result, custom behavior, a
helper already used as a model property, a cross-module target and a shared helper
referring to a Scala.js-only target must remain outside the rewrite plan.
