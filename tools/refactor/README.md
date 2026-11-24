# Refactor detectors

Detector configurations read application sources; explicitly named repair configurations apply Scalafix patches.
Run commands from the `contracts3-scalafix` worktree root.

`bash tools/refactor/scalafix.sh <args>` runs the Scalafix CLI at the build's versions and passes the build's Scala as
`--scala-version` (unless the arguments name one). It reads them from `target/build-versions.properties`, which sbt writes
from `project/Dependencies.scala` whenever it loads the build; the rules' default SemanticDB roots
(`target/out/<platform>/scala-{scalaVersion}`) follow `--scala-version`. Standalone fixtures keep their own pinned Scala
(`//> using scala …` and the matching `--scala-version`).

`AstRecipe.propertyFlowsOnly` consumes frozen E007/E172 diagnostics and the existing
newtype index/history, then delegates patches to TypeConsolidation. It aliases a
generated helper to a model constructor property's type when the compiler and AST
show an unchanged property projection: a matching-name parameter, a whole method
result, or a whole typed value (including supported collection projections). Both
types must have the same base and qualified Pure constraint, compatible platform,
module and source set; the helper must be empty, stable, generated with owner-match
provenance, and unused as a constructor property itself. Conflicting property
targets veto the entire helper. Custom behavior, transformations, unknown origins,
different parameter names, real property-to-property merges and unsupported
diagnostics remain unresolved. Configuration selects the mode and scope, not
individual application type or expression mappings. Golden and negative fixtures
are in compiler-repairs-fixture/property-flows; their synthetic provenance is
documented in property-origins/README.md. The mode uses the existing preview,
ApplyFrozenPreview, CompileBatch and stable-ID measurement pipeline.

For `.map(...).toMap`, an E172 tuple `<:<` witness must match the key of the whole
typed Map val/method result. Only direct property projections in the matching
tuple/arrow position qualify. A value rewrite additionally needs the compiler's
exact value-variable upper bound to match the declared Map value. Both key and
value constraints are retained for conflict checks, including unsupported value
projections; a helper used for two properties is not consolidated. Nested bindings,
transformed keys/values, raw primitives and non-property sources remain unresolved.
The map-property-fixture proves key/value identity and negative/conflict cases.

`AstRecipe.generatedHelperAdapters` extends native-Boolean owner mode with
same-base Pure helper-to-helper/property conversions, retaining both type
definitions. Both types must belong to the immediate implementation owner, with
unique compatible module/source-set/platform definitions. Each must be a known
constructor property or have owner-match generated-member provenance, and at least
one must be a generated non-property helper. Empty constructor companions may
retain given aliases; custom methods/constructors and constrained types are
excluded. Unknown types, two model properties and cross-owner conversions remain
unresolved. The existing structural converter handles supported collections,
tuples and functions; producer expressions are evaluated once. This mode requires
nativeBooleans so the known unsafe E134 overload adapter remains disabled and
cannot be combined with configured nominal boundaries, error/comparison adapters
or callback history. helper-adapters and helper-origins contain the positive,
negative and synthetic-provenance fixtures;
compiler-repairs-helper-reject-overloads.conf must fail.

`AstRecipe.generatedValueNames` repairs generated companion/value collisions.
An indexed, canonical qualified Pure companion must have a unique typed
same-name value and exact owner-match provenance for that value in a stable object
owner. The rule chooses a fresh `<Name>Value` companion and canonical alias,
retains the original type name as an alias and retains the value's public name.
Only rooted one-argument constructor heads inside that value's initializer change;
unqualified/own-owner term references to that name in the initializer veto the
repair. Foreign qualified members with the same spelling are preserved. Given
aliases may remain when they have no same-name term reference; other companion
behavior stays excluded. No
constructor is required for empty or raw initializers. Other constructor/member
references outside the initializer remain outside this repair's coverage; ordinary
value indexing and iteration are preserved. Unsupported/custom/local/ambiguous
cases stay unresolved. Existing shapes.tsv records old-to-new opaque definition
identities and carries the generated origin to the new definition through the
standard appliedPlans index history. This is naming repair, not semantic type
merging, primitive elimination or a new finding denominator. Fixtures are in
value-names-fixture; execution still uses ApplyFrozenPreview and CompileBatch.
It may run with propertyFlowsOnly in one frozen pass; all other rewrite combinations
remain prohibited, and the existing overlap check protects the combined patches.

`AstRecipe.resolveImportedPure` is opt-in for compiler adapters and is prohibited
in property consolidation. It resolves canonical Iron RefinedType/Pure imports,
explicit import renames and qualified names, and native scalar/Array bases. Scope
checks use declarations across frozen Scala sources, including package/type/term
shadows, visible imports and enclosing inheritance. Import-dependent recognition
rejects unknown wildcards, uncertain inherited bindings, misleading Pure aliases,
fake parents and shadowed primitives/namespaces. Fully rooted identities remain
available. Only Iron/any, Scala/Predef and java.lang wildcards have known bindings;
arbitrary aliases/inheritance/imports stay unsupported. An AST object index finds
canonical companions with renamed parent imports even when the inventory's
syntactic newtype-shape index misses them. This changes repair resolution, not the
measurement detector or its acceptance limits. Existing ownership, same-base,
constraint and boundary gates still apply; E134 stays disabled in the project mode.
imported-pure-fixture checks the supported and shadowed cases, and the disabled
fixture config must produce an empty plan.

## Project-wide measurement and bulk rewrite gates

The bounded migration goal uses `refactor-audit/migration-rounds.tsv` to retain every original goal while limiting further detector refinement to two rounds per family. Existing fixtures and source regressions are not independent holdouts. Confirmed violations, unresolved candidates, and evidence relationships remain separate; no overall completion percentage is available until the baseline is established. Historical fixes are not added to the new baseline's numerator. Production and test findings remain visible; a production-first batch does not exempt tests.

The current execution gate is recorded in `refactor-audit/priority-family-status.md`.
The bounded acceptance review has assigned dispositions to every family; this does
not establish inventory acceptance. Insufficient detectors are frozen with explicit
limits. Copy automation is paused at its two-round limit. Prioritize transaction
ownership, capability access, module isolation and primitive/newtype modeling.
Use the configurable pipeline in ProjectAssessment; its first complete repair
execution passed for the method-body clock batch. Extend existing entry points rather than creating
batch-specific runners, verifiers or report frameworks; those require explicit
justification and user approval. Configuration may select a batch, but the batch
must cover all eligible confirmed findings for its reusable transformation, with
exclusions explained before application. Standard verification is compilation,
relevant behavioral tests and stable-ID remeasurement. An additional check needs a
concrete risk not covered by that contract.

### Configurable migration pipeline

`ProjectAssessment.pipeline` composes existing rules; it does not replace their
finding histories or create a new finding ledger. `pipeline-example.conf` is a
non-executing example referencing the completed clock batch. Run it with:

```
bash tools/refactor/scalafix.sh --config tools/refactor/pipeline-example.conf --files tools/refactor/ProjectAssessment.scala
```

`mode = plan` validates configuration structure only. It does not check preview
freshness, run tests, validate eligibility judgments or authorize reusing an old
observation. The example cannot be switched to apply: its observations already
exist and its preview belongs to an already completed batch.

A fresh batch supplies a scopeReview path documenting all eligible confirmed IDs
and exclusions, prepare/rewrites invocations (config, files, semantic), paired
baselineTests/afterTests configurations for BatchTestVerification, a compileConfig
for CompileBatch, and original-ID identityChecks using existing reconciliation
rules, or capabilityFixes listing exact original CAP IDs. capabilityFixes compares
the complete before/after identity tables, allowing only those removals and no
other row or multiplicity changes. It does not infer confirmation from a detector
match; the scope review must establish that. The pipeline requires full `compile` and `Test/compile`, matching test scopes
and environments, and fresh build/test/output observations. Identity-check selection
still requires review: a configured check must cover the batch's original IDs;
the runner cannot infer that from a filename.

Apply order is full pre-assessment, baseline tests, preparation, rewrites, full
compilation, after tests, post-assessment, and original-ID reconciliation. The
assessment retains its capability/isolation regression gates and archived evidence.
Source hashes forbid preparation/test changes; policy and scope-review fingerprints
must stay fixed during execution. Stages use argument arrays, Scala 3 parsing and
ordinary SemanticDB. No shell scripts or stale-SemanticDB override are involved.
Failures stop the sequence and leave the worktree for inspection, without automatic
rollback or baseline recapture. The batch observation contains stage logs and status;
repair credit stays in existing family histories. Extra identity checks reuse existing
tools; introducing another batch-specific verifier still requires approval.

Validation so far: both changed Scala rules compile; the example plan succeeds;
apply rejects existing test observations before executing stages; a deliberately
failed sbt task propagates a nonzero Scalafix exit through CompileBatch while all
3,159 application source hashes remain stable. The method-body clock batch then
completed the full pipeline: two exact IDs repaired, all 3,159 sources compiled,
47 unchanged test identities passed before/after with no skips, and no capability
or isolation regressions. Tooling checks receive no repair credit.

Historical copy development: the cheap-model source audit selected the frozen `copy-holdout.tsv` without reading detector reports; it has 21 grouped locations, including 21 positive copy occurrences and two legitimate comparison locations. The first run found 19 positives, missed two method-result receivers, and had no false positives. Round one addressed that gap; the sample then became development/regression evidence. Subsequent independent acceptance results, including insufficient test recall, are retained in the consolidated ledger. Negative diversity and nonrandom selection limit inference beyond sampled cases. `Printer.copy` remains unclassified rather than being forced into either label.

`CopyToLens` rewrites resolved, non-generic, single-field case-class updates using token patches, preserving nested edits and argument evaluation. It defers multi-field updates, custom methods, generic type changes, accumulator algorithms, unsupported modules and tests. A source manifest rejects stale class metadata. `CopyVerification` compiles and executes input, expected and rewritten code, checks the expected AST and evaluation order, verifies negative/positive checks, and proves a second application is unchanged. All application edits remain Scalafix-generated.

### Evidence for all 43 goals

The assessment generates `refactor-audit/inventory/coverage.md` from `inventory-coverage.conf`. Every goal must have an explicit evidence mapping and remaining decision; the generator rejects missing/duplicate goal IDs, missing reports and unknown table columns. G33 (live-data/process integrity) explicitly requires external evidence. This is an evidence index, **not** a claim that all 43 policy detectors are complete. Counts overlap and must not be summed as violations. A zero count is never automatic approval.

Additional inventories run as part of the same assessment:

- `OperationInventory.scala`: copies, unwraps, folds, derivation calls and given instances, preserving source/AST, semantic owner and receiver signature. Case-class copies are distinguished from unrelated `copy` methods. Fold callback allocations are separated from the one-time seed; indirect effects remain unresolved. Compiled fixtures assert 18 classified occurrences, including template-style givens, and reject a deliberately false classification.
- `ArchitectureInventory.scala`: declarations, signatures, constructor visibility, references, comments, string constants, annotations, construction ASTs, logical-file review groups and service implementation evidence. Implementations are linked by parent symbols; an unrelated class named `InMemory` is not evidence of parity. All 645 object-plan rows are reconciled against current declarations. Missing originals remain obligations, never completed migrations. Fixtures verify actual implementation edges, lookalikes, constructors, caller references, relocation uncertainty and rejection of incomplete source coverage.
- `BuildInventory.scala`: evaluated names, descriptions, class directories and Compile/Runtime classpaths for every sbt project. JVM/JS products and sbt hashed artifact paths are matched to evaluated product directories. Configurable forbidden edges and runtime macro dependencies are reported separately. Parser fixtures reject incomplete settings and distinguish project products from same-named external artifacts. Tests and the published plugin contract require separate policy review.
- `ResourceInventory.scala`: parsed HOCON values and include directives, build ASTs, and every row in the architecture ledgers. Environment substitutions are retained without reading environment values; includes are recorded without fetching them. Nested values, unresolved substitutions, include capture and malformed HOCON rejection have fixtures. Missing ledger source files require relocation/retirement proof.

Detailed reports live under `refactor-audit/{operations,architecture,build,resources}/`. Large raw TSVs and logs are ignored; summaries, counts and verification outcomes are versioned. To run one inventory independently, use the Scalafix CLI with its `*-inventory.conf` (operations uses `operations.conf`). Architecture and operation scans require `--sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules`; build/resource inventories run on their own rule file. Run the full assessment before accepting combined evidence as current.

`architecture/type-ownership.tsv` inventories every class, trait, object, enum and alias, including already-nested declarations. `ArchitectureInventory.propertyOwners` maps exact child symbols to approved companion object symbols. Configured matches and violations are distinct from unreviewed ownership. Missing children or owner objects fail the scan. Same-package/module prefix candidates are hypotheses only: service components and coincidental names receive no automatic ownership decision. Namespace-only owners and unprefixed properties are supported. Counts separate production and test declarations by module.

`architecture/dependency-crossings.tsv` inventories project package crossings from source references, declared/inferred signatures and transparent alias expansion. `dependency-directions.conf` encodes the domain/supporting-area direction matrix from AGENTS.md. Direction permission does not approve a feature contract. `publicContracts` identifies approved contract symbols and their members; it cannot waive forbidden direction. `crossingPermits` records exact owner/target exceptions. Missing configured identities fail. Unmapped regions and unresolved names remain explicit, and counts separate modules and production/tests. Same-package dependencies, generated implicit arguments and module-layer isolation require their separate evidence; this report is not proof that every architectural crossing is legal.

`architecture/model-construction.tsv` gives every class its constructor access and invariant-policy status. `invariantModels` states exact model obligations (currently the shared Period endpoint ordering); `plainRecords` records reviewed records with no aggregate invariant. Stale or conflicting identities fail. `construction-sites.tsv` retains explicit invocation and construction syntax and links available result types to project classes; companion calls list possible apply-result classes, not a proof of overload selection. `factory-candidates.tsv` links declared/inferred result types (including transparent aliases) to model-result call candidates in method bodies. A return type may merely contain a model, and a method may be a query or delegate rather than a factory. Neither private constructors nor Either results prove invariant preservation. Direct-body evidence does not cover reflective construction, macro expansion or complete delegated data flow.

Zero linked construction sites does not prove a class is unused. All explicit normal, infix and unary calls are retained, including unresolved callable values and companion metadata. Source declaration and generated-member signatures are collected across files to recover missing call-site metadata. `invocation-counts.tsv` separates invocation kinds and resolution status by module and source scope. Model-result calls may delegate or return cached values; they are not proof of construction. External metadata, parameterless selections, reflection and generated implicit calls still need separate evidence. Factory counts are relationships, not validated smart constructors or confirmed violations.

### Primitive and existing-type inventory

`ModelTypeRewrite` uses exact configured record/method symbols and parameter order.
`primitiveTypes` maps each target newtype to its expected semantic base (String,
Int, Long or Double); omitted mappings retain the legacy Int behavior. It fails on
a mismatched source base rather than widening or narrowing values. `optionalFields`
and `listFields` select distinct container shapes; matching result/known-value lists
support typed forwarding. Nested containers and arbitrary collection types remain
unsupported. Scalar unwrapping still requires explicit boundary owners/call slots.

`model-concepts-rewrite-guards.conf` invokes the existing configurable
RewriteVerification on seven Iron Pure concepts in one fixture. It checks exact
expected output, definitions/callers compilation, values, single evaluation, list
order, concept separation, wrong-base rejection and wrong-parameter-order rejection.
`model-concepts-legacy-verification.conf` proves the old Int fixture remains valid.
No new batch-specific verification runner was introduced. The purpose review and
impact tools also accept configuration, retaining their historical default paths.

`model-concepts-impact.conf` joins the current model inventory against the 79-ID
review and verifies source freshness across the whole project. Its reference and
one-hop caller tables are preparation evidence, not confirmed findings or complete
transitive propagation. Production preview generation and boundary compilation
remain necessary before application; passing fixtures alone do not authorize a
claim that the multi-concept production rewrite is complete.

The assessment also runs `ModelInventory.scala` over every production/test source and verifies its compiled fixtures. Its reports live in `refactor-audit/models/`. Run it independently with the Scalafix CLI and `--config tools/refactor/models.conf --sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules` after compiling with SemanticDB.

`positions.tsv` records fields, constructor/method/lambda parameters, return values, vals/vars, givens, aliases and type bounds, including inferred symbol signatures. `leaves.tsv` follows nested type arguments, transparent aliases, functions, tuples, unions, by-name/repeated types and singleton primitives. A cross-file symbol catalogue resolves project declarations before classification. Opaque and Iron types are distinguished from their primitive representation; custom types named String or Int are not primitives. Unit and bottom/top types are not model-value primitive candidates.

`types.tsv` catalogs existing classes, traits, enums, objects, aliases, opaque types and Iron companions, with ownership, generic parameters, AST and signatures. `uses.tsv` gives resolved symbol consumers. Global occurrence IDs use semantic symbols; local/anonymous IDs are scoped to a file and source position and are not stable across edits.

`model-candidates.tsv` selects production members for review; local variables, lambdas, tests and alias definitions remain available in the full inventory. Module-to-layer hints are configured in `models.conf` and do not establish correct ownership. `proposals.tsv` suggests names and existing same-named types, without approving equivalence. Purpose, units, constraints, boundaries and owner must still be assigned. Do not edit generated proposals as an approval registry: reviewed mappings belong in a separate versioned configuration before rewrites are enabled.

`pagination-review.tsv` collects potential page size, page number, offset, cursor and limit concepts. `parameterization.tsv` records whether existing generic parameters occur in constructor positions; absence is a phantom-role review prompt, not proof that a phantom is appropriate. `review.md` gives the largest production-member groups. No type merge or primitive replacement is performed.

Pagination review must preserve boundary semantics: `api.library.Filter.limit` uses zero/default input, and `server/.../library/search/SearchFilters.page` resolves nonpositive values against configured defaults before querying. Domain `PageSize` is positive; `Offset` is a zero-based row position. An import-work cap is not automatically a page size. API replacements should be Pure opaque wire types under the policy, with server conversion handling defaults and constraints. The inspected paging contracts do not establish a need for entity phantom parameters; that remains a design decision rather than an automatic name-based rewrite.

Missing metadata, external definitions and unsupported semantic forms remain in `unresolved.tsv` and `unresolved-symbols.tsv`. An external container's explicit type arguments are still inspected even when its definition is unavailable. Therefore primitive counts are discovered occurrences, not a claim that all external aliases have been expanded or all occurrences are violations. Coverage requires the complete file set and unchanged source hashes; any source change or fixture mismatch fails the run. Check both the process exit status and `status.txt` before trusting reports.

`newtype-constraints.tsv` records direct Iron parent/base/constraint identities and expands transparent parameterless aliases. `pureConstraints`, `nonPureConstraints` and `clientModules` are configurable; unknown/generic aliases remain unresolved. `constraint-counts.tsv` separates module and production/test scope. Inherited newtypes remain a coverage gap.

`position-roles.tsv` classifies declaration context: record fields, public trait contracts, other constructors, non-public members, locals, tests and explicitly configured boundaries. `primitive-role-counts.tsv` provides module counts. `model-contract-candidates.tsv` prioritizes production record fields and trait contracts; the full inventory retains every other candidate. These roles do not establish semantic purpose or exemptions. `boundaryOwners` uses exact method symbols and fails when a configured boundary disappears. No production boundaries are exempted by default.

`ModelInventoryVerification.scala` compiles five fixture files and checks the exact multiset of 37 primitive occurrences, existing newtypes across files, lookalikes, phantom/represented parameters and a resolved consumer edge. It checks record/contract roles against local/private/boundary cases and rejects absent boundaries. Constraint fixtures distinguish Iron Pure, its compiler-proven exported alias, a cross-file alias to Pure, known non-Pure constraints and a restrictive alias misleadingly named Pure. The exact external identities are configured in `iron-constraints.conf`, shared by production and fixture scans. Deliberately wrong expectations and an omitted source file must fail. All application sources remain unchanged.

`architecture/service-obligations.tsv` checks each candidate service's trait, companion Implementation/InMemory and private Implementation, traversing resolved parent relationships. `ArchitectureInventory.requiredServices` pins identities whose shape is required (currently BasemapService); a missing configured symbol fails instead of reducing the count to zero. Other service candidates require role/mechanism review. Structural evidence never proves behavioral parity. Fixtures cover public versus private implementation, indirect inheritance, unrelated InMemory, parent type arguments and missing required services.

`architecture/file-groups.tsv` groups same-named companions/aliases, resolved sealed families and `<Name>Rule` aliases actually used as Iron constraints by `<Name>`. A matching prefix alone does not qualify. Remaining multiple groups are a focused helper/ownership review queue, with production/test counts in `file-group-counts.tsv`. Fixtures include sealed case classes/objects, an Iron alias/companion/constraint family and an unrelated same-prefix pair. Assessment fingerprints include fixture code and expectations, the object plan, build inputs, configuration resources and architecture ledgers, so changing those during a run invalidates its result.

`architecture/visibility-candidates.tsv` extends the original twelve-repository inventory to discovered Repo/Row/Implementation declarations and owners with semantic database dependencies. Effective access accounts for enclosing private declarations, qualified package scope and protected access. Discovery is a candidate queue: a database dependency alone does not make a public infrastructure service a private repository. `visibilityTargets` shares the existing `RepositoryVisibility.targets`; `visibilityScopes` can add exact owners independent of naming. Missing configured owners fail the scan.

`visibility-consumers.tsv` includes direct name references, declared/inferred signature references and transparent alias chains, separating production/tests and inside/outside expected features. `type-references.tsv` and `alias-references.tsv` preserve the underlying evidence for layer checks. Opaque representations remain hidden. Fixtures check broad privacy, inherited privacy, inferred/aliased row consumers, opaque non-leaks, database owners without suffixes and absent configured targets. Generated implicit arguments/typeclass instances and external alias metadata remain gaps; relationship counts are not violation counts.

```text
bash tools/refactor/scalafix.sh --config tools/refactor/assessment.conf --files tools/refactor/ProjectAssessment.scala
```

This read-only Scalafix assessment compiles production and tests with ordinary SemanticDB and formatting disabled, runs the developed capability/structural detectors and current migration measurements over all modules, and verifies the structural fixtures and source expectations. The assessment itself never applies application patches. Child commands are sbt and Scalafix; no Python or shell rewrite scripts are used.

`refactor-audit/assessment/current/summary.md` gives per-rule totals; `matrix.tsv` gives per-rule/per-source-module counts and baseline deltas. `coverage.tsv` lists production/test source counts, and `status.txt` must say MEASUREMENT_COMPLETE before these reports are accepted. Logs and detailed source/AST reports are in the same directory. Any failed compiler, missing/stale SemanticDB, source assertion, fixture, coverage check or source/policy change during the run fails the assessment; old reports must not be treated as current success. Sources shared by cross-built rows are counted once, not as independently verified platform-specific occurrences.

The 26 current metrics cover 14 capability checks, five structural checks, configured repository consumers, configured old owners/controller names, the CLI stdout/stderr/console-injection migrations, and plain Kindlings-to-Tapir schema bridges. The CLI migration metrics are applicable only to CLI production; their zero entries in other modules are not compliance evidence. Primitive-role mappings and repository/owner inventories remain explicitly configured scopes. Historical insertion/generation recipes (Console scaffolding, test insertion, one-off boundary patches and file movers) are not general detectors and must not be replayed as assessments. ContractInventory produces review candidates rather than confirmed violations. The 43-goal readiness matrix remains separate and still exposes unimplemented policy checks.

### Schema derivation batch

`SchemaDerives.scala` detects `given Schema[A] = summon[KindlingsSchema[A]].schema` by resolved symbols. It inventories all production/test modules and records each match's AST, eligibility, generated given symbol and explicit consumers. Scala 3 omits symbols on derives syntax; eligibility therefore checks the typed compiler-generated Kindlings instance on the companion. Non-local, generic, modified or otherwise unsupported definitions are reported for review. Customized schema expressions and strict JSON codecs are outside this rewrite.

Run the Scalafix CLI with `--config tools/refactor/schema-derives.conf --sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules` to inspect. Apply with `schema-derives-apply.conf` after inspecting the plan. Source-set/hash checks reject stale plans. Apply adds `com.histmersion.api.json.Schema` to the existing derives clause, removes the redundant given/empty companion, and updates explicit given references, including imported aliases. The rule generates the shared adapter through Scalafix. `SchemaPatchWhitespace.scala` removes whitespace tokens left by removed definitions; run it over the same files after the rewrite. Recompile before semantic verification.

The adapter must be a concrete subtype of Tapir Schema: Scala resolves a plain type alias's derives through Tapir's companion, bypassing the intended Kindlings bridge. The concrete adapter delegates to Kindlings and preserves every schema constructor field. It requires no additional dependency. Its allocation occurs when the schema instance is constructed.

`SchemaVerification.scala` is the repeatable fixture runner, invoked by the project assessment. It compiles input and expected output, compares the automatic patch exactly, verifies failure before and success after, compiles and executes the rewritten consumers, checks idempotence, and tests nested automatic derivation and schema-field preservation. Its child processes are Scala CLI and Scalafix; artifacts stay under this worktree's `target/`.

Use `assessment-schema.conf` for the project-wide zero gate on `derivation/schema-bridge`. Zero means the plain bridge family is removed; it does not mean customized schemas, codecs, database derivations, or the entire derives policy are finished. The previous measurement baseline remains versioned; adding this detector makes the previous policy fingerprint incomparable rather than silently resetting it.

The initial baseline is versioned in `assessment/baseline.tsv`. Normal runs never replace it. `assessment-baseline.conf` explicitly captures a new reviewed baseline; do not use it to hide regressions. Rule/configuration fingerprint changes make deltas incomparable until the policy change is reviewed. `assessment-regression.conf` rejects increases in any rule/module cell against a comparable baseline.

For a batch completion gate, include `assessment.conf` and set `ProjectAssessment.zero = ["capability/console", "migration/old-owner"]` to the exact metrics the batch promises to eliminate. The command must exit successfully and report zero for each selected metric. `assessment-zero-reject.conf` deliberately selects the still-violated clock rule and must fail. Unsupported stdout/stderr shapes count as residuals, not as successful rewrites. Set `compile = false` only when ordinary compilation was already completed for the exact sources; SemanticDB validity is still checked.

Bulk migration target: at least 90% of the agreed violations fixed automatically, with the difficult remainder recorded explicitly. Select whole AST/symbol transformation families by volume; a one-occurrence rewrite proves a fixture, not useful migration throughput. `automation-backlog.md` reconciles the historical architecture ledger by family without presenting its stale exception counts as current findings. Before claiming a percentage, distinguish confirmed violations, valid cases, automatic fixes and deferred cases, retain a stable denominator, and validate definitions and call sites together. Do not sum overlapping detector metrics or count relocation/reference changes as separate policy violations fixed.

## Injected command console

Command output uses our `com.histmersion.util.console.Console`, passed with `using`. `Cli.out.println(...)` and `Cli.err.println(...)` are contextual accessors, not global streams. Commands that previously captured output in singleton fields are now contextual builders. Console adapters are selected at process entry points; no companion default given is supplied. Scribe remains the diagnostic logger, and the CLI configures it to stderr. Configuration warnings retain lazy evaluation and use scribe.

The dependency-free JVM `console` module is shared by CLI, server launchers and plugin-resolver. It contains the interface and stream adapters, never domain code. Only the adapter has raw stream/input permission. Four exact entry methods may select an adapter, but may not print directly. Rules reject console construction inside commands and Console dependencies in domain/plugin code.

`console-inventory.conf` uses SemanticDB to trace output dependencies through methods and member values, with file-scoped local symbols. `console-inject.conf` applies the resulting plan through AST/token patches. Always regenerate the plan from ordinary SemanticDB immediately before applying: source hashes reject stale plans. The final inventory has no remaining targets, and `--check` on the regenerated plan passes. This is snapshot migration tooling, not proof of arbitrary callback or dynamic-dispatch coverage.

The other `console-*.conf` recipes generate the parsed interface, update launchers/tests, handle default arguments, expose the imperative API and separate terminal prompts. They record the reviewed stages of this migration; do not rerun historical generation recipes indiscriminately against the completed code.

Compile the policy fixture against the real interface:

```text
scala-cli compile tools/refactor/console-policy-fixtures.scala modules/console/src/main/scala/com/histmersion/util/console/Console.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-console-fixtures
bash tools/refactor/scalafix.sh --config tools/refactor/console-policy-fixtures.conf --sourceroot tools/refactor --semanticdb-targetroots target/refactor-console-fixtures --files tools/refactor/console-policy-fixtures.scala
```

Six exact diagnostics must pass fixture verification; substituting `console-policy-reject.conf` must exit 64. User-output regressions cover isolated injected consoles, channels, Unicode, JSON, progress, prompts and exit statuses. Production and aggregate test compilation use `scalafmtOnCompile := false` and ordinary `-Xsemanticdb`.

## Historical launcher output repair (superseded)

`launcher-output.conf` is a reviewed snapshot recipe using `RepairBoundaries` AST templates, scoped to four production files. It threads supplied output functions through resolver/relink signatures and callers and sends three diagnostics to scribe. Run it twice: outer definition patches intentionally defer nested body rewrites. `launcher-output-check.conf` must then pass with zero remaining matches. It is not a general semantic migration rule: its source paths, owners and shapes must be reviewed again for a different snapshot.

`launcher-output-tests.conf` adds regression tests through Scalafix in `PluginResolverSuite.scala` and `PluginToolSuite.scala`. These exercise JSON, digest-only stdout, usage stderr and exception exit status. The test insertion is idempotent. The application compiles with `-source:future` and normal SemanticDB; always disable scalafmt-on-compile during this migration.

The three exact entry-function permissions in `capabilities.conf` permit console access only. Seven raw stream references remain at these entries (four relocated, three existing); report this policy change separately from code repairs. The server generated-plugin-tree test currently rejects additional local skill and metadata files; do not delete those files to make this batch green. See `refactor-audit/RESULTS.md` for full results and outstanding findings.

## Migration readiness

### Repository visibility

`repository-visibility.conf` inventories the twelve configured exact repository symbols across production and test SemanticDB. Run with `--auto-classpath --auto-classpath-roots target/out --files modules` after ordinary production and test compilation. The report in `refactor-audit/repository-visibility/` records every resolved reference with its AST, outside consumer files, source coverage and a checklist. Counts include member and parameter references; they are not independent operations. Reflection and inferred public-type escapes still require review and compiler validation.

`repository-visibility-apply.conf` permits only the reviewed bundle repositories. It verifies that the entire source file set and content hashes match the inventory before constructing patches, and refuses targets with outside consumers. It adds `private[goals]` to GoalBundleRepo and `private[worklists]` to QueueBundleRepo. Compile all production and tests, then run `repository-visibility-check.conf` on both files. Missing approved declarations fail check mode. Fresh inventory reports the two as private and the remaining ten as blocked.

The fixture under `repository-fixture/src/main/scala` covers renamed imports, child-package access, method references, an outside consumer and a same-named unrelated object. Compile with SemanticDB into `target/repository-fixture`, inventory with `repository-fixture.conf`, apply `repository-fixture-apply.conf` using Scalafix `--out-from`/`--out-to`, and compare with `repository-fixture/expected/Repositories.scala`. Compile the output and check with `repository-fixture-check.conf`. `repository-fixture-reject.conf` must reject the exposed repository before writing; applying an old application manifest after a source change must also fail.

The 43-goal register is encoded in `migration.conf`, based on the user's `docs/refactor-goals.md` in the main checkout (read only). Historical counts and checkmarks are not completion evidence. Run the readiness entry point:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/migration.conf --sourceroot . --files modules --files tools/refactor/source-fixtures.scala --files tools/refactor/structure-fixtures.scala
```

This runs the current syntactic checks, both syntactic fixture files and 24 guarded real-source expectations in one invocation. It writes `refactor-audit/migration/coverage-matrix.md`, `readiness.txt`, findings, coverage and SHA-256 fingerprints of the parsed inputs and decoded policy. Input changes during the scan fail verification. Synthetic fixture findings are excluded from application counts.

**Current expected result: exit 1, NOT READY.** All 3,125 application/test Scala files are parsed, both fixture files pass, and all 24 real-source assertions pass. G10's exact production package constraint is measured; 42 goals remain partial or unmeasured. Syntactic findings are 34 primitive roles, 12 repository visibility and 4 Handlers declarations (two classes and their companions). Entry-point and bucket checks currently find zero in production. Zero for a narrower check does not complete its wider goal.

The entry point does not execute the separate semantic audit or claim its results are fresh. Those goals remain blockers until semantic coverage and the other evidence specified in the matrix are integrated. Removing goals, referencing unknown checks or omitting configured fixtures cannot produce a readiness pass. Only goals supported by the rule's implemented coverage registry can become measured; editing a configuration title or deleting its remaining-work text cannot promote unsupported goals.

`migration-incomplete-register.conf` is a negative configuration fixture: passing it in place of `migration.conf` must fail decoding with exit 4. The source assertion and fixture rejection checks below remain separate tests of the verifier itself. Readiness measures whether detectors can govern the migration; migration completion will additionally require zero violations or explicitly approved review decisions. No application rewrite is authorized by a successful fixture run alone.

## Semantic capability policy

`CapabilityAccess.scala` is a Scalafix semantic rule. `capabilities.conf` defines
eleven checks: environment, system properties, console output, clocks, lifecycle,
filesystem, HTTP, configuration loading, processes, repository factories and
repository transaction lifecycle.

The policy matches resolved symbols, including aliases and method references.
`symbols` uses normalized semantic matching (all overloads); `prefixes` uses raw
SemanticDB symbol prefixes. `within` restricts enclosing owner prefixes; `outside`
excludes owner prefixes. `repositoriesOnly` selects explicitly configured
`repositoryOwners` or enclosing types with a configured `repositorySuffixes` suffix.
Suffixes are an explicit naming convention, not proof that all repositories were found.

Permissions name an exact enclosing owner symbol and specific capability IDs, with
a reason. They are not file-wide or general-purpose exemptions. Permissions apply
to nested code inside that owner. The report counts permission uses; zero uses
requires review and does not prove the permission is obsolete when coverage is incomplete.

The clock policy distinguishes overloads using parameter signatures: `now(clock)`
is allowed, while `now()` and `now(zone)` are reported. Configuration parsing of
supplied strings is allowed; external loading and access to environmental config
are reported. Whether a domain should depend on a config representation at all is
a separate, not-yet-implemented layer dependency check.

Unknown configuration keys, duplicate IDs and permissions naming unknown
capabilities are rejected. `sourceRoots` independently enumerates expected Scala
sources containing `sourcePathContains`; missing analyzed files fail the run.

## Fixture verification

Compile fixtures with the same Scala version as the application:

```text
scala-cli compile tools/refactor/fixtures.scala tools/refactor/clock-fixtures.scala tools/refactor/config-fixtures.scala tools/refactor/import-fixtures.scala tools/refactor/transaction-fixtures.scala tools/refactor/transaction-helpers.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-fixtures
```

Run the fixture assertions through Scalafix itself:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/fixtures.conf --sourceroot tools/refactor --semanticdb-targetroots target/refactor-fixtures --files tools/refactor/fixtures.scala --files tools/refactor/clock-fixtures.scala --files tools/refactor/config-fixtures.scala --files tools/refactor/import-fixtures.scala --files tools/refactor/transaction-fixtures.scala --files tools/refactor/transaction-helpers.scala
```

The rule runtime is Scala 2.13; analyzed code is Scala 3.10. Fixture mode compares
every diagnostic's ID and line against `// expect:` markers, including duplicate
counts, and rejects unexpected diagnostics. Unmarked lines are negative examples.
The configured fixture-file manifest prevents accidentally omitting a file.
This mode returns no lint patches after successful assertions. Exit 0 is required.

Repeat the same invocation with these configurations to test failure paths:

| Configuration | Expected result |
|---|---|
| `reject-fixtures.conf` | Exit 1: direct lint errors plus indirect repository violations rejected at completion |
| `typo.conf` | Exit 4: misspelled configuration key rejected |
| `invalid.conf` | Exit 4: permission references an unknown capability |
| `missing-diagnostic.conf` | Exit 1: deliberately allowed violation breaks expected diagnostics |
| `incomplete-fixtures.conf`, passing only `fixtures.scala` | Exit 1: omitted fixture files break coverage |

These fixtures cover look-alikes, aliased and ordinary imports, method values,
captured console streams, nested calls, explicit-owner and suffix-selected
repositories, borrowed connections, narrow permissions, injected clocks, config
loading and pure parsing. They are a starting contract, not exhaustive coverage of
all libraries and syntax forms.

The six compiled semantic fixture files assert 37 diagnostics: 32 direct and 5 through known helper bodies. Transaction fixtures cover cross-file helpers, aliases, multiple hops, recursion, local helpers, exact-owner permissions and safe statement closure. A regression with repeated local SemanticDB IDs failed before file-scoped graph keys were introduced and passes afterwards; raw `localN` IDs must never be treated as project-wide symbols. A chained ProcessBuilder construction/start is one capability dependency; the source audit caught its previous triple report, and the regression now asserts one.

The repository-only checks follow resolved calls through known source method bodies. The report's `call-chain` column gives a witness from the reported call to the forbidden API. These are call-site findings, not independent transaction operations. Direct diagnostics and indirect ones are deduplicated at a given rule/location. Source-local symbols are keyed by file and symbol; global symbols retain their resolved identities. General filesystem/HTTP helper propagation is not enabled: calling an approved supplied utility must not become a violation merely because its implementation performs I/O. Dynamic dispatch, unknown helper bodies, callback targets passed as values, constructor/initializer effects and inferred escapes remain outside this graph's coverage.

## Application audit

First compile with SemanticDB, without automatic formatting:

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' compile
```

Then run the detector. Both JVM and Scala.js outputs are searched:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/capabilities.conf --sourceroot . --auto-classpath --auto-classpath-roots target/out --exclude '**/src/test/**' --files modules
```

Do not disable stale SemanticDB checks. Production compilation now passes after the
Scalafix boundary repairs. The normal audit covers all 2,355 main files and reports
432 policy findings. Use `capabilities-source.conf` to also assert all 21 audited
source examples. The run fails on violations, with no missing files;
`refactor-audit/capabilities/missing-files.txt` lists any gaps and
`coverage.tsv` groups them by module. `findings.tsv` includes source locations,
owners, referenced symbols, source fragments and ASTs. The command exit status
and coverage report both matter.

## Compiler repair rules

### CLI stderr boundary batch

`CliErrorBoundary.scala` inspects resolved Console.err/System.err uses and the
actual PrintStream method signature. It rewrites immediate calls only: String
print/println and zero-argument println/flush on stable known singleton roots.
Method references, non-string overloads, escaped streams and effectful receivers
remain unsupported in the report. `cli-error.conf`, `cli-error-apply.conf` and
`cli-error-check.conf` provide inspect/apply/check modes with the ordinary
semantic options and `--files modules/cli/src/main`.

`AddCliError.scala` inserts narrow package-private methods and a regression into
OutputSuite through AST patches. By-name String parameters allow each method to
capture its stream before evaluating the message, preserving original evaluation
order. Console.err and System.err remain separate; no PrintStream escapes the
boundary. Run aggregate compilation and `cli/testOnly *` after applying.

The executable fixtures use `cli-error-fixtures/Support.scala` and either
`cli-error-fixtures/input` or the separately rewritten output. Run with
`--main-class fixture.Verify --server=false --semanticdb` and a dedicated target
root. Apply `cli-error-fixtures.conf` with `--out-from`/`--out-to`, compare its
Example.scala with the expected file, and run the output with Support.scala.
Tests assert stream replacement during argument evaluation, distinct dynamic and
process stderr, exact bytes, overload selection and flush counts.
`cli-error-fixtures-check.conf` rejects the original's eight supported calls and
passes the recompiled output, retaining two unsupported examples. Application
check mode must report zero supported and unsupported cases for this batch.

### CLI stdout boundary batch

`CliOutputBoundary.scala` matches semantic `Predef.print`/`println` references in
CLI production sources, excluding the configured exact boundary owner. It replaces
only the callable reference, preserving argument syntax, overloads and callbacks.
Selected receivers must resolve to the Predef singleton; potentially effectful
receivers are left unchanged and recorded as unsupported. This rule does not
rewrite imports, stderr, printf, arbitrary PrintStreams or server diagnostics.

Use `cli-output.conf` for inspection, `cli-output-apply.conf` for application and
`cli-output-check.conf` for idempotence, with ordinary semantic options and
`--files modules/cli/src/main`. The TSV and summary distinguish supported rewrites
from unsupported cases. Check mode fails on remaining supported matches; it does
not certify unsupported cases as compliant. The independent capability audit
continues to detect raw console access.

`AddCliOutput.scala` adds the three package-private methods at the existing Cli
boundary and the real OutputSuite regression via AST patches. No new permission
is required. After recompiling, run `cli/testOnly *` and the full capability audit.
The executable input/expected fixtures in `cli-output-fixtures/` assert exact
stdout and stderr bytes and evaluation behavior. Run the input with SemanticDB,
apply `cli-output-fixtures.conf` to a separate output directory, compare with
expected source, then compile/run the result with fresh SemanticDB.
`cli-output-fixtures-check.conf` fails on the original and passes on the result,
while retaining and reporting the effectful-receiver case.

### Controller rename batch

`RenameControllers.scala` uses the exact configured class/companion symbols. It
preserves aliases, unrelated same-named symbols and string literals. Its TSV
records every candidate's symbol, source, AST and disposition. These two mappings
were inspected against normally compiled production and test sources before use;
this rule is not a general package/class relocation engine.

Use `controller-renames.conf` to inspect, `controller-renames-apply.conf` to apply
and `controller-renames-check.conf` to reject remaining matches, with the normal
Scalafix semantic options and `--files modules/server` (including tests).
After applying, `MoveControllerFiles.scala` moves the two source files only when
the renamed class and companion exist and destinations do not collide.
`ControllerDocLinks.scala` updates the reviewed old links inside comment tokens.
Recompile before running semantic checks again.

`controller-fixtures/input/Example.scala` exercises constructors, companions,
aliased imports, qualified types, callbacks, lookalikes and string preservation.
Compile with SemanticDB, run `controller-fixtures.conf` using `--out-from` and
`--out-to`, compare against `controller-fixtures/expected/Example.scala`, and
compile the result. `controller-fixtures-reject.conf` must fail on the input and
pass on the recompiled output. The application check must also pass after the
rename. The four structural findings become zero without removing the Handlers
violation cases from `structure-fixtures.scala`.

The contract snapshot run also exposed an API/domain role comparison in
`AgentDefinitions.agent`. `controller-boundary-repair.conf` performs the checked
AST replacement that converts the parsed API role to the domain role before
roster membership is tested. Snapshot expectations are unchanged.

`RepairBoundaries.scala` matches parsed Scala trees using scoped recipes in
`repair-boundaries.conf` and `repair-tests.conf`. Names starting with `__` are
AST captures. Repeated captures must have identical structure. Paths and optional
enclosing method names restrict each rewrite. Captured source tokens retain their
syntax, and the resulting tree must equal structural AST substitution before a
patch is accepted. These are compiler-guided repairs for this snapshot, not general
symbol-safe migrations to apply indiscriminately in other projects.

Use the usual Scalafix CLI invocation with `repair-boundaries.conf` to inspect,
`repair-apply.conf` to apply, and `repair-check.conf` to reject remaining matches.
The report contains before/after source and ASTs for the latest run. Some recipes
depend on earlier repairs; repeat until check mode passes, then compile. Application
sources were changed only through these Scalafix rules, with formatting-on-compile
disabled.

The fixture under `repair-fixtures/input/agents/ModelPicker.scala` rewrites to
`repair-fixtures/expected/agents/ModelPicker.scala`; the expected output compiles
against Chimney 2.1.0. Running check mode on the input fails with one remaining
repair. Checking repaired production sources succeeds. `RelocateDomainSuites.scala`
moves the three suites that depend on domain helpers from API to core, checking
their class names, refusing existing destinations and verifying the copied contents
before deleting the original. `AddTargetRegression.scala` adds checked-coordinate
boundary cases to the existing personal model suite.

Test recipes apply with `repair-tests.conf`; `repair-tests-check.conf` rejects
remaining matches. Pass the repaired modules' `src/test` directories as files.
The three CLI validation tests now assert preservation of wire inputs for server
validation. Domain citekey rejection remains tested in the relocated core suite;
the server coordinate regression covers out-of-range and non-finite values.

Compilation and targeted verification (without formatting application sources):

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' compile 'Test/compile'
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' 'core/test' 'cli/testOnly *' 'files/test' 'server/testOnly *PersonalTypesSuite *PersonalControllersSuite *VocabularyWireSuite *AgentsSettingsSuite *PagesSuite'
```

## Historical best-effort reproduction

Scala's [best-effort mode](https://www.scala-lang.org/api/3.5.1/docs/docs/internals/best-effort-compilation.html) can emit SemanticDB despite type errors. This was verified on the installed Scala 3.10.1 using `best-effort-fixture.scala`: compilation exits 1 with two intended errors, yet the two known capability references remain detectable. The missing type appears in the unresolved-name report. Error placeholders can still carry symbols, so an empty unresolved report would not certify valid code.

Generate provisional metadata separately from ordinary metadata, with formatting disabled:

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set server / Compile / scalacOptions ++= Seq("-Xsemanticdb", "-Ybest-effort", "-semanticdb-target", "/Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort")' 'set cli / Compile / scalacOptions ++= Seq("-Xsemanticdb", "-Ybest-effort", "-semanticdb-target", "/Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort")' 'all server/compile cli/compile'
```

Observed exit 1: CLI reports eight inherited errors; server also reaches a compiler pickling assertion in `CompareRunsViews.scala`. SemanticDB extraction happened before that assertion. Do not call this a successful build. The following audit reads the isolated provisional output alongside the ordinary metadata already present in `target/out`:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/capabilities-best-effort.conf --sourceroot . --auto-classpath --auto-classpath-roots target/out --semanticdb-targetroots target/refactor-best-effort --exclude '**/src/test/**' --files modules
```

All 2,355 main files were read, with no omitted files. All 21 guarded source expectations passed, including service-owned transactions, borrowed DbCon, injected Clock overloads, ctx.http/ctx.env, pure config parsing and pure Path construction. There are 432 provisional policy findings and 4,578 name lookups without symbols. The latter is a diagnostic inventory, not a count of compiler errors. `bestEffort=true` forces exit 1 even when coverage and assertions pass; `verification.txt`, `coverage.tsv`, `findings.tsv` and `unresolved.tsv` are under `refactor-audit/capabilities-best-effort/`.

Reproduce the broken-code fixture separately:

```text
scala-cli compile tools/refactor/best-effort-fixture.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort-fixtures --scalac-option -Ybest-effort
bash tools/refactor/scalafix.sh --config tools/refactor/best-effort-fixtures.conf --sourceroot tools/refactor --semanticdb-targetroots target/refactor-best-effort-fixtures --files tools/refactor/best-effort-fixture.scala
```

The compile must fail; the assertion run must pass. Switching to `best-effort-reject.conf` must fail verification because provisional compiler evidence cannot certify compliance. Keep best-effort metadata out of the ordinary audit's classpath roots.

[Lorikeet](https://github.com/epfl-systemf/lorikeet) was also reviewed: it offers query/template syntax over Scalafix, but its documented grading workflow skips noncompiling submissions. It does not remove this evidence gap; no additional framework or formatting workflow was installed.

## Broader candidate inventory

```text
bash tools/refactor/scalafix.sh --rules file:tools/refactor/ContractInventory.scala --sourceroot . --files modules
```

`ContractInventory` is exploratory syntax analysis. Its generated checklist names
G1–G43 and marks unimplemented checks as unmeasured. Candidates are not confirmed
violations: primitive roles, aggregate invariants, object ownership, comments,
custom codecs and module boundaries need more evidence. Comments carry their token
structure rather than repeated enclosing-file ASTs. This inventory does not replace
the tested semantic detector or the architectural design review.

## Magnum 2 fragments

`Magnum2Frag.scala` (G58, `magnum2-frag.conf`) moved the db and server modules from the db module's own `Sql`/`Param`
fragments to Magnum 2's nesting `Frag`: constant text handed to `Sql.raw` becomes `sql"..."`, `Sql.of(text, params*)`
becomes `sql"..."` with each `?` replaced by its value (bound through its codec), `++` chains become one `sql"..."` that
nests every non-constant operand, and String text spliced into SQL stays raw as `${SqlLiteral(...)}`. It runs once,
over the SemanticDB of the Magnum 1.3.1 build; the sites it leaves alone go to `refactor-audit/magnum2/left-alone.tsv`
with the reason. Its fixture compiles `magnum2-frag-fixture/input` against 1.3.1, runs the rule and diffs the result with
`expected/`, which is compiled against 2.0.0:

```text
bash tools/refactor/magnum2-frag-fixture/check.sh
```

`MagnumTypedBinds.scala` (`magnum-typed-binds.conf`, syntactic) did the companion step for the server tests on 2.0.0:
`${x.value}` inside `sql"..."` binds `x` (Iron's `.value` is an `IronType`, which has no codec), and the suites the
compiler named get `NewtypeColumns.given`.

`Magnum3Frag.scala` (G58 slice 2, `magnum3-frag.conf`, syntactic) follows a helper that now builds a `Frag` from `Frag`
columns instead of SQL text as a String (`RangeSql.cols`, the shared `Timestamps.iso`, `PlaceSql.importedSql`, ...) at
its call sites: a String literal at an SQL position becomes a `sql"..."` of the same text, a call may be renamed (a
file's own `ts` to `Timestamps.iso`), `${SqlLiteral(helper(...))}` and `${SqlLiteral(name)}` of a name that is a `Frag`
now lose the wrapper, and the `SqlLiteral` import goes where none is left. A call spliced into an `s"..."` or joined
with `+` is kept and reported (a `Frag` there would put its `toString` into the text). The helpers' definitions are
edited by hand first; the fixture:

```text
bash tools/refactor/magnum3-frag-fixture/check.sh
```

## Newtypes do the job (G1/G2)

`ValueOntoType` removes `.value` where the newtype itself does the work, so the raw value is touched only in the
type's companion and at a boundary: `x.value.op(…)` becomes `x.op(…)` when the newtype's companion declares the
extension `op` (an operation added to the type by hand, then every call site follows); `X(x.value)` of an `x: X`
becomes `x`; `x.value == y.value` (`!=`, `<`, `<=`, `>`, `>=`) of one newtype becomes `x == y` (Newtype's lifted
CanEqual and Ordering); and a `.value` handed to a `sinks` method (regexes over SemanticDB symbols of methods whose
parameter takes the newtype through a type class instance, e.g. Laminar's text child and `:=` through
`com.histmersion.Display`) is dropped, adding `sinkImport` where the file's package is not `sinkPackage`; for an
operator sink (`attr := x`), `sinkReceivers` (regexes over the receiver's symbol) keeps it to the display attributes,
so a protocol position (an option's `value`, a data attribute, `disabled`) never goes through Display. With
`sinkRequires` (a type class symbol) a sink is rewritten only for a newtype whose companion gives that instance: the
type's own display form, never the raw value lifted for every newtype; the others go to the worklist. It reads the project's SemanticDB (`semanticdbRoots`) for the companions'
extensions, limits itself to `paths` (prefixes) less `excludes` (regexes over the path), and writes `report` (each rewrite) and `<report>-missing.tsv` (each
`x.value.op` whose newtype has no extension `op`: the worklist of operations to add to the types). After
`sbt compile Test/compile`, from the worktree root:

```text
SCALAFIX_JAVA_OPTS="-Xmx6g -Xss8m" bash tools/refactor/scalafix.sh --config tools/refactor/value-onto-type-<batch>.conf \
  --sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules/<module>/src/main
```

The fixture:

```text
.claude/skills/scalafix/scripts/run-rule.sh tools/refactor/ValueOntoType.scala \
  tools/refactor/value-onto-type-fixture/input tools/refactor/value-onto-type-fixture/expected
```

`DisplayAsIs` writes the per-type decision that a newtype's friendly form is its text as it is: for each listed
companion (`types`, SemanticDB symbols) one line `given Display[T] = Display(_.value)` at the top of its body (a body
is created for a one-line object), importing `com.histmersion.Display` where the file's package does not see it.
Types whose friendly form differs (enum spellings, times, years, ids shown short) are listed under `forms`
(`newtype`, the `instance` text, the `imports` it needs), each the decided form of that type. Run as above with `display-as-is-<batch>.conf`; the fixture:

```text
.claude/skills/scalafix/scripts/run-rule.sh tools/refactor/DisplayAsIs.scala \
  tools/refactor/display-as-is-fixture/input tools/refactor/display-as-is-fixture/expected
```

`RetypeToNewtype` retypes a parameter from a primitive to a newtype (G1; the G44plaindef findings are its worklist).
Each `retypes` entry names `param` (the parameter's SemanticDB symbol), `newtype` (its companion's symbol), `tpe` and
`wrap` (the text of the type and of the constructor). The declared primitive becomes `tpe` (inside an `Option` or a
collection too); a call that passes `y.value` of that newtype passes `y`, any other argument becomes `wrap(arg)` (a
construction, the boundary's job: a non-boundary call site is a finding to fix by hand); in the body the parameter
stays a newtype where one is taken (an argument to a parameter of that newtype, the receiver of a companion
extension) and gets `.value` everywhere else. `report` lists each change. Run as above with
`retype-to-newtype-<batch>.conf`; the fixture:

```text
.claude/skills/scalafix/scripts/run-rule.sh tools/refactor/RetypeToNewtype.scala \
  tools/refactor/retype-to-newtype-fixture/input tools/refactor/retype-to-newtype-fixture/expected
```

## Remaining work

- Extend the repository call graph to dispatch, supplied callbacks and initializer effects; define approved capability boundaries before adding propagation for other capabilities.
- Analyze inferred types, aliases and overridden methods against forbidden base
  capabilities. Listing a base symbol does not automatically match an override.
- Expand and test the symbol catalog: it is not an exhaustive Java/Scala I/O list.
- Review plugin-testkit/infrastructure ownership and explicit boundary permissions.
  The current conservative package scope includes `plugin.testkit` for review.
- Validate permission-owner existence across the complete compilation scope.
- Add approved type-placement and operation-ownership mappings and their detectors.
- Implement and test the remaining goal-specific checks before any mass rewrite.
- Add independent testkit-level tests of the fixture assertion machinery itself.

Scalafix source checking is not a runtime sandbox. Reflection, native code,
unresolved dynamic dispatch and effects hidden inside external libraries are not
proven safe by these rules. No blanket effect-monad restriction is introduced.
# Audited source contracts

The source-audit checklist is in `refactor-audit/audited-examples.md`. Two cheap-model audits supplied both violations and valid cases. `source-contracts.conf` turns the reviewed model and repository-visibility subset into executable expectations against actual source files. Each expectation guards the original line text and checks the exact count for its rule. Stale examples, unchecked examples, missing source coverage and unexpected fixture diagnostics fail the process.

Run the AST checks on every Scala file, including sources that do not compile:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/source-contracts.conf --sourceroot . --files modules
```

This inventory returns exit 0 when coverage and assertions pass, even with violations. It writes `refactor-audit/source-contracts/{summary.txt,verification.txt,findings.tsv,files.txt,missing-files.txt}` plus input fingerprints. Findings include source and AST. It is not a zero-violation migration gate. The current checks cover configured primitive roles, feature repository visibility, exact forbidden packages, Handlers declarations in configured controller packages, and ordinary Array[String] launcher signatures on objects. The full readiness command above adds the goal coverage requirements and synthetic fixtures.

Run the synthetic fixtures:

```text
bash tools/refactor/scalafix.sh --config tools/refactor/source-fixtures.conf --sourceroot . --files tools/refactor/source-fixtures.scala
```

Expected exit 0, six diagnostics asserted internally. Every unmarked line must stay clean. Cases include configured and unrelated owners, typed parameters, unrelated parameter names, private helpers, lambda parameters, nested implementations, feature-private repositories, overly broad private scope and protected repositories.

Use the same fixture command with these alternative configs to exercise rejection:

| Config | Expected result |
|---|---|
| `source-missing-diagnostic.conf` | Exit 1: removed concept mapping loses an expected finding |
| `source-unexpected-diagnostic.conf` | Exit 1: extra mapping flags a clean owner |
| `source-stale-example.conf` | Exit 1: source guard no longer matches |
| `source-missing-files.conf`, with `--files tools/refactor/fixtures.scala` instead | Exit 1: independently enumerated fixture was not analyzed |

Concept mappings are explicit owner/parameter/raw-type/required-type records. They check declared syntax, not inferred types or aliases. Unconfigured concepts and implementation/call-site propagation are unmeasured. Repository roles currently use the feature-package and `Repo` suffix convention; unnamed repositories remain unmeasured. A feature-private qualifier must name the feature package or an enclosing owner, not a broader domain. These rules make no claim about unwraps, constructor invariants, service ownership, transitive calls or capability coverage in files missing SemanticDB. Tests are parsed for coverage but production checks currently apply only to main sources.
