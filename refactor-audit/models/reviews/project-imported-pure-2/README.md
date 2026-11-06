# Imported Pure compiler adapters

This applied preview contains93 compiler patches (89 E007/4 E008) in21 production
files, zero application test files. The opt-in repair resolver recognizes actual
Iron/native identities through canonical imports, explicit import renames and
qualified names. It checks frozen declarations across files, visible imports,
package/type/term shadows and enclosing inheritance. Unknown import-dependent
bindings, misleading Pure aliases and fake parents stay unresolved. Existing
owner/base/constraint/boundary rules remain; E134 stays disabled.

Ten positive fixture adapters match the independent golden and actual rewritten
future-source/fatal-E165 runtime passes, including numeric/Boolean/list values and
Array identity. Seven negative bindings are unchanged. The disabled mode emits
zero patches. Five ordinary/two owner and the legacy property goldens stay exact.
Preview1 missed two import-renamed parent definitions absent from the syntax shape
index; a unique AST object lookup corrects repair discovery without changing the
inventory detector or golden. That project preview was not applied. Preview2 alone
is applied and all21 files match its frozen output.

Actual frontier28 still fails all ten tasks: API1847 per platform, values latest17
cached, upstreams246, vaults latest95 cached, Calibre145 and ai-http64. Other failing
tasks remain cached; dependent application tests are blocked. No whole-project
test/native pass or complete compiler regression attribution is claimed.

The full3169-Scala census matches the actual build snapshot (which additionally
includes build.sbt). Raw IDs and opaque shape IDs have zero delta from the preceding
type-flow census; origins are byte-identical. Current raw candidates886 production/
6009 test; opaque shapes26888 production/19 test;2978 overlap groups;16260 unresolved
references. Measurement alias/import/inference limits remain. The two applied
cohort stages total127 AST patches in28 distinct production files, not127 verified
policy violations. Current status/history is ../../../priority-family-status.md and
../../../migration-rounds.tsv, with no separate tracking system or new exception.
