# Media's source lookup boundary

The reviewed violation was AssetsControllers calling the library's Citekey parser
directly. Media owns MediaSources; library supplies SourceMedia. The configured AST
batch replaces the parse/existence pair with lookup returning MediaSources.Key or
MediaSources.Error.Invalid/NotFound. The key is an opaque Iron String/Pure type on
the interface companion. Media does not duplicate library validation or depend on
library vocabulary. SourceMedia validates first and then performs the same lookup.
The controller maps the errors to the existing wire codes and passes the typed key
to the asset service at that boundary.

The original controller discarded the parser's trimmed result and looked up the
original string. This batch preserves that behavior explicitly; it does not silently
canonicalize writes or change whitespace handling. Existing current/markdown methods
and the asset service still use strings and remain model-inventory candidates.
This repairs the reviewed ownership violation, not every primitive in the interface.

All application/test edits were made through Scalafix. AstRecipe selects exact
parsed trees in manifest-hash-guarded files and records before/after ASTs, rejecting
missing/duplicate matches and overlapping patches. It is a reusable application
mechanism for reviewed design recipes, not a semantic detector or autonomous design
inference. ApplyFrozenPreview applies the parsed, frozen output. The production
batch changes six files and seven AST nodes, including all three test doubles.
The finding's treatment remains manual/design: the interface decision was authored
for this boundary. Mechanical application through a reusable rule does not convert
that design work into a general bulk-rewrite capability or earn 90% automation credit.

Four characterization tests were generated before production changes. They cover
invalid/null input before lookup, exact parser error propagation, a valid missing
source and retention of the original input in missing-source errors. They use the
real SourceMedia over the in-memory source service. Existing AssetsControllersSuite
adds successful uploads, invalid payloads, alias reads and maintenance refusal.
The test doubles supply controlled lookup outcomes; they do not implement library
validation. SourcesControllersSuite and ArticlesControllersSuite cover the other
changed fixtures.

Evidence checkpoints:

- tests-before: seven tests pass without skips, before production changes.
- build-1: full production/test compilation and fifteen tests pass before changes.
- build-2: full production/test compilation and the same fifteen tests pass after.
- preview-1: reviewed production AST patch and source hashes.
- reconciliation-1: exactly three original production IDs repaired; all other 311
  identities preserved, no new/reopened findings, and all fifteen test bodies
  unchanged. Preview hashes match the compiled sources, with no unplanned changes.

Fresh scans cover the same 3,157 sources in 28 modules as build-2. Production
isolation retains seven confirmed unread-projection relationships, 109 accepted
contributions and four unresolved registry cases. Tests retain 59 prior non-violations
and 132 candidates. There are 69 ambiguous owner relationships and 182,287
unclassified direction-evidence rows. These remain explicit limitations.
The model inventory records 129 Iron companions; production member primitive leaves
are 30,386, all-scope leaves 62,219 and unresolved leaves 46,908. These count changes
are not automatically counted as confirmed model repairs.

The first preview attempt rejected an expression replacement because it parsed only
top-level source fragments. AstRecipe now accepts either a source fragment or a
single statement/expression, and still parses each complete output file. No output
was written by the failed attempt. The first apply invocation traversed only the
rule file and correctly failed its complete-plan guard; it changed no production
files. build-1 was started after that refusal and is retained as additional baseline
evidence, not claimed as post-rewrite validation. The corrected apply traversed all
module sources; build-2 is the actual post-rewrite check.

No live database, native-image, independent detector-quality or migration-wide 90%
claim follows from these checks. No baseline reset, exception or detector refinement
round was used.
