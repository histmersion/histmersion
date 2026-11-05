# Capability treatment checkpoint

The complete assessment at 2026-11-05T08:04 covers 3,159 source/test files in
28 modules. Capability policy covers production only: 87 anchors, 105 occurrences,
31 identity-ambiguous occurrences, no new/reopened identities or multiplicity changes.
No additional application repair is claimed here.

The bounded source review retains every observed anchor and its original evidence:

| Classification | Anchors | Occurrences |
|---|---:|---:|
| Confirmed outstanding | 25 | 27 |
| Candidate, not reviewed here | 48 | 64 |
| Unresolved adapter ownership | 13 | 13 |
| Previously adjudicated pure separator | 1 | 1 |

The recording adapter's 13 filesystem operations remain detected. They are neither
automatically condemned as plugin logic nor granted a testkit-wide exemption.
Other entry-point and adapter candidates still need exact-owner decisions.
This checkpoint does not replace earlier review events or verified repair histories.
The pure separator decision is carried forward explicitly. No detector refinement
round, precision/recall acceptance, new exception or automation credit is claimed.

## Next repair batches

1. **Server wall clock:** rewrite constructor definitions and callers together for
   BasemapService, DiskLru and MaintenanceControllers; use the existing deps.clock
   for ServerWiring callbacks. Preserve timestamp formatting and DiskLru's millisecond
   precision. These are one configurable propagation batch, not separate one-site rules.
2. **Clock defaults and HTTP retry:** preserve injectable callback overrides, eliminate
   global defaults, and supply clocks at composition. Include StorageCache,
   StorageFigures, OnDemandTiles, InstanceControllers, Polite, PolicyHttp and
   JavaRangeHttp. Keep module boundaries: plugin-host cannot depend on server utilities.
3. **Home expansion and CLI environment:** supply JVM home and platform/environment
   snapshots through helpers and all callers. JVM user.home and HOME are distinct;
   do not silently substitute one for the other. Preserve trimming and expansion order.
4. **Storage measurement:** supply the existing monotonic Timer for paired elapsed-time
   reads. Wall-clock Clock is not a valid substitute. This remains design treatment.

For each batch: freeze supported source shapes and definitions/callers, obtain
before/after behavioral test results, compile production and tests, then reconcile
original IDs against a fresh whole-project scan. Disappearance alone earns no repair
credit. Unreviewed and unresolved findings remain in the ledger throughout.

## Reproducibility and limits

CapabilityReview is a configured Scalafix report rule. It checks the observation's
complete source fingerprint, exact selector counts, distinct identities, overlapping
decisions, classification/treatment schema, and source AST occurrence multiplicities.
It emits source hashes and AST context, retains all 87 input rows, and refuses to
overwrite a review event. File selectors are expanded into exact IDs in this frozen
event; they are not persistent permission patterns. The TSV is human source judgment,
not independently established detector truth.

The assessment initially stopped on SourceRepo.putIn's stale line-58 example after
codec extraction moved it to line 57. The correction verifies the entire method AST
unchanged, preserves both configs and policy contracts, and changes no assertion
count. Both the failed assessment and the correction remain in history.

Tests, permission occurrence identities, callback effects, dynamic dispatch and
unsupported API families remain outside the capability identity gate. The current
assessment is policy-incomparable with the original aggregate baseline; use the
separate capability/isolation identity gates. Review tooling was added after the
completed scan; detector sources/configuration were not changed by this review.
