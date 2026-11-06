# Combined declaration repairs

Applied once over ten production files: seven naming repairs (22 patches) and six
property helper consolidations (12 patches). The standalone naming preview3 is
superseded and was not applied separately. Existing overlap checks verify that
the two declaration modes compose. No expression/type mappings or new runners
were added. Public values/old aliases remain; no model property identity is merged.

Three new map key aliases share Licence.Family.Wire, EventTaxonomy.Kind.Value and
SiteTypes.SiteType.Id. Other aliases follow existing E007 Doi/Isbn/Calibredb flows.
E172 requires exact tuple subtype evidence, a whole typed Map RHS, a matching key
and direct property projections in the corresponding slot. Values need an exact
reported upper bound. All key/value constraints remain for conflicts, even when a
projection cannot be rewritten. Nested/transformed/raw/unknown cases stay unresolved.

Combined fixture goldens and actual future-source/fatal-E165 runtime pass for map
identity/lookup/empty/duplicate/evaluation behavior and naming/ordering/foreign-field
preservation. Negative files are unchanged, including cross-key and key/value
conflicts. The old property golden is exact. Fixture origins are synthetic.

All ten files match the frozen output before the later imported-Pure adapters.
Actual frontier27 still fails all ten tasks: API1945 per platform, values17 and
Calibre145; other failures are cached. Naming E120 diagnostics fall6 ->2. Final
cohort compilation/measurement is frontier28 and type-positions-after-imported-pure-1.
Canonical current status/history is ../../../priority-family-status.md and
../../../migration-rounds.tsv. No global repair percentage or inventory acceptance
is inferred from these artifacts.
