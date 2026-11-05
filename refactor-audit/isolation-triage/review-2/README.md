# Bounded review of remaining isolation relationships

All 346 relationships from the post-Title observation are retained by their original
IDs: 155 production and 191 test. Source hashes match the preceding successful build.
The eighteen production files were reviewed against their bodies, the configured
dependency directions, AGENTS and the approved domain design's contribution contracts.

| Production treatment | Relationships | Work |
|---|---:|---|
| Confirmed, bulk rewrite | 29 | Four feature-owned database codecs (26 relationships); three unused domain imports in two utility files |
| Confirmed, targeted rewrite | 3 | Move the source-specific Spool adapter to library acquisition/ingest with its consumers |
| Confirmed, manual/design | 10 | Seven unread-model/controller relationships and three media source-parse relationships |
| Accepted exact contributions | 109 | 93 source-referrer relationships and 16 personal Readable relationships |
| Unresolved policy evidence | 4 | ImportOptions/BundleService transport the library registry, as documented in the as-built design; reconcile that with the final utility-owned interface requirement |

The accepted set retains the earlier 21 AttestationSourceReferrer decisions and adds
88 exact existing relationships. SourceReferrer implementations are explicitly listed
in AGENTS/domain design section 23, including util.jobs; they run in the caller's
transaction and coordinate the table owners. Readable is owned by personal and
implemented by the domains whose records it lists. These decisions do not authorize
direct calls to those domains' repositories or services, nor the library-specific
fields inside personal's UnreadItem. The latter remain confirmed migration work.

This is an explicit review of existing contribution contracts, not permission to
defer known violations. The 42 confirmed relationships must be repaired; the four
unresolved relationships have no exception. Counts are relationship evidence, not
independent conceptual defects. Identity ambiguity remains visible on each row.

Tests retain 59 earlier exact non-violation decisions; 132 remain candidates.
There is no blanket test exemption and no new test-quality sample. The current
classification overlay is `treatment/findings.tsv`; the detector observation remains
unchanged. Its regression gate continues to reject new or reopened identities, even
in a file with an accepted relationship. No policy suppression, denominator reset,
detector refinement round, or independent precision/recall claim is introduced.

## Next repair batch

The compiled-reference pass is now complete: five consumers of the domain status,
confidence and precision codecs were found in generated row codecs. See
`../../codec-ownership/README.md` for the resulting extraction/caller batch. The
investigation plan below is retained as history; a compiler probe is still pending.

Take the four domain codecs and three dead imports together. Preserve the generic
array codec and vocabulary factory. `../../codec-ownership/inventory-1` contains a
project-wide scan of source names and compiler-synthetic symbols, plus the actual
given ASTs and all source hashes. That evidence finds generic array-codec consumers
but **no consumers of the four feature-specific codecs**. This is not yet proof they
are unused: macro-generated consumers may not be represented.

The source repository already defines an API Source.Status codec locally; its type
differs from the domain Source.Status codec under review. VocabCodecSuite exercises
API records through repositories, so its success alone would not prove that all
four old domain codecs are unnecessary. Before extracting unused compatibility
code into new files, inspect current compiled references or perform a guarded compiler
probe with explicit fallback diagnostics. Then either remove genuinely obsolete
definitions or relocate the required definitions and all actual consumers together.
Do not add feature imports to every database consumer indiscriminately.

Bound this consumer investigation to one compiled-reference pass and one guarded
compiler probe. If evidence still cannot distinguish unused definitions from hidden
consumers, retain the uncertainty and advance the already reviewed Spool/unread-model
work; do not expand the detector indefinitely or count the codecs as fixed.
