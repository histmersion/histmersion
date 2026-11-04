# Bulk automation target

The objective is at least 90% automatic fixes across the agreed policy-violation inventory, leaving the difficult remainder for explicit review. A one-site Scalafix recipe is not the delivery unit. The delivery unit is a reusable transformation family applied to all eligible definitions and call sites across all modules.

The historical `architecture/contracts.tsv` ledger records 2,818 occurrences in 1,277 grouped rows. Its five largest families account for 2,688 occurrences (95.4%): derives 1,042; copy 479; layer 464; unwrap 390; primitive 313. These counts are historical heuristic exceptions, not a fresh, deduplicated semantic baseline. The new project assessment's 270 capability/structural diagnostics measure a different, narrower set and cannot be substituted as the denominator.

## Current order of work

The overall target is unchanged. [Execution priorities](execution-priorities.md)
supersede the historical order below: close current batch validation, pause copy
automation, then prioritize transaction ownership, capability access, module isolation
and primitive/newtype modeling. Detection is independently valuable without a repair.
Track every policy family, with stable identities/history and production/test separation;
large easy categories must not displace architectural risks or hide unsupported cases.

## Historical order (superseded)

1. **Derivation family first.** Resolve derivation library symbols and their target types; group declaration/call AST shapes across the whole project. Identify equivalent supported derives clauses, named-given consumers, already-derived instances and customized instances. Compile expected-output fixtures before applying all eligible instance and caller changes together. Do not rewrite custom instances merely because their type is derivable.
2. **Primitive types and layer crossings.** Build shared concept/ownership/conversion mappings from the approved architecture. Rewrite signatures, records, construction sites and boundary conversions together. Keep intentional final-type migrations distinct from compile-safe ones, with compiler errors counted as a repair queue. Do not substitute unchecked casts or widen boundaries to make compilation pass.
3. **Newtype access.** Resolve actual Iron accessors, distinguish ordinary fields named value, and enforce configured conversion boundaries. Remove redundant unwrap/rewrap chains only where types and evaluation semantics establish equivalence; report required wire/SQL/formatting conversions as legitimate cases.
4. **Record updates.** Match actual case-class copy methods and supported optics shapes. Preserve single evaluation, update ordering, defaults and behavior. Accumulating folds and algorithms needing a different representation are a separate transformation family, not blind copy replacements.

This order is provisional until semantic shape counts establish automatic coverage. Do not spend another batch on isolated repository visibility while the high-volume families remain unmeasured.

## Evidence required for every family

- Fresh counts by rule and module; source/AST/symbol evidence; a stable, deduplicated set of confirmed violations.
- Classification into automatic rewrite, legitimate non-violation, unsupported rewrite and design decision. Candidates are not automatically violations, and cases are not silently removed from the denominator.
- A configurable rule covering each supported shape, fixtures including lookalikes and negative cases, and a whole-project patch preview.
- One application batch for the supported set, followed by compilation of definitions and callers, relevant behavioral tests, and idempotence checks.
- Post-rewrite residual counts. The selected measurement must reach zero for completed scope; any unsupported matches remain visible. Compare per-module deltas so a reduction in one module cannot hide an increase elsewhere.
- Cumulative automatic-fix count and deferred list against the agreed inventory. No claim of 90% completion until the denominator and residuals are validated. Manual work belongs to the classified remainder, not the default path.

The assessment entry point and its batch gates are documented in `tools/refactor/README.md`. Measurement completeness, detector coverage of the full policy, and successful automatic migration are three separate results.
