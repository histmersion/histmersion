# Project-wide rule measurements

28 source modules; 3127 source/test files. Source and policy fingerprints remained stable. Baseline comparable: true.

Counts are matches within each rule's stated scope, not proof of all 43 architectural goals. Relationship counts and rewrite-plan nodes must not be added to diagnostic counts. CLI-only rows outside cli are not applicable. Zero is never a claim that an unimplemented goal is covered.

| Rule | Current | Baseline | Delta | Scope |
|---|---:|---:|---:|---|
| capability/console-input | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/console-factory | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/domain-console | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/environment | 8 | 8 | 0 | production; configured symbol/owner policy |
| capability/properties | 12 | 12 | 0 | production; configured symbol/owner policy |
| capability/console | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/clock | 103 | 103 | 0 | production; configured symbol/owner policy |
| capability/lifecycle | 11 | 11 | 0 | production; configured symbol/owner policy |
| capability/filesystem | 67 | 67 | 0 | production; configured symbol/owner policy |
| capability/http | 1 | 1 | 0 | production; configured symbol/owner policy |
| capability/config-load | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/process | 7 | 7 | 0 | production; configured symbol/owner policy |
| capability/repo-factory | 0 | 0 | 0 | production; configured symbol/owner policy |
| capability/repo-transaction | 19 | 19 | 0 | production; configured symbol/owner policy |
| structure/bucket | 0 | 0 | 0 | production; configured structural scope |
| structure/handlers | 0 | 0 | 0 | production; configured structural scope |
| structure/object-main | 0 | 0 | 0 | production; configured structural scope |
| structure/primitive-role | 34 | 34 | 0 | production; configured structural scope |
| structure/repo-visibility | 8 | 8 | 0 | production; configured structural scope |
| repository/outside-files | 51 | 51 | 0 | production+tests; twelve configured repository symbols; relationship count |
| migration/old-owner | 0 | 0 | 0 | production+tests; configured ownership moves |
| migration/controller-name | 0 | 0 | 0 | production+tests; configured old controller symbols |
| migration/cli-stdout | 0 | 0 | 0 | cli production only; includes unsupported matches |
| migration/cli-stderr | 0 | 0 | 0 | cli production only; includes unsupported matches |
| migration/console-injection | 0 | 0 | 0 | cli production only; dependency-plan nodes |
| derivation/schema-bridge | 0 | 0 | 0 | production+tests; semantic plain KindlingsSchema bridges, including deferred shapes |
| updates/case-class-copy-production | 108 | 108 | 0 | production; resolved case-class copy occurrences, including deferred rewrite shapes |
| updates/case-class-copy-test | 1587 | 1587 | 0 | test; resolved case-class copy occurrences, including deferred rewrite shapes |
