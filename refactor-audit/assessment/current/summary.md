# Project-wide rule measurements

28 source modules; 3127 source/test files. Source and policy fingerprints remained stable. Baseline comparable: false.

Counts are matches within each rule's stated scope, not proof of all 43 architectural goals. Relationship counts and rewrite-plan nodes must not be added to diagnostic counts. CLI-only rows outside cli are not applicable. Zero is never a claim that an unimplemented goal is covered.

| Rule | Current | Baseline | Delta | Scope |
|---|---:|---:|---:|---|
| capability/console-input | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/console-factory | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/domain-console | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/environment | 8 | unknown | incomparable | production; configured symbol/owner policy |
| capability/properties | 12 | unknown | incomparable | production; configured symbol/owner policy |
| capability/console | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/clock | 103 | unknown | incomparable | production; configured symbol/owner policy |
| capability/lifecycle | 11 | unknown | incomparable | production; configured symbol/owner policy |
| capability/filesystem | 67 | unknown | incomparable | production; configured symbol/owner policy |
| capability/http | 1 | unknown | incomparable | production; configured symbol/owner policy |
| capability/config-load | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/process | 7 | unknown | incomparable | production; configured symbol/owner policy |
| capability/repo-factory | 0 | unknown | incomparable | production; configured symbol/owner policy |
| capability/repo-transaction | 22 | unknown | incomparable | production; configured symbol/owner policy |
| structure/bucket | 0 | unknown | incomparable | production; configured structural scope |
| structure/handlers | 0 | unknown | incomparable | production; configured structural scope |
| structure/object-main | 0 | unknown | incomparable | production; configured structural scope |
| structure/primitive-role | 34 | unknown | incomparable | production; configured structural scope |
| structure/repo-visibility | 8 | unknown | incomparable | production; configured structural scope |
| repository/outside-files | 51 | unknown | incomparable | production+tests; twelve configured repository symbols; relationship count |
| migration/old-owner | 0 | unknown | incomparable | production+tests; configured ownership moves |
| migration/controller-name | 0 | unknown | incomparable | production+tests; configured old controller symbols |
| migration/cli-stdout | 0 | unknown | incomparable | cli production only; includes unsupported matches |
| migration/cli-stderr | 0 | unknown | incomparable | cli production only; includes unsupported matches |
| migration/console-injection | 0 | unknown | incomparable | cli production only; dependency-plan nodes |
| derivation/schema-bridge | 0 | unknown | incomparable | production+tests; semantic plain KindlingsSchema bridges, including deferred shapes |
| updates/case-class-copy-production | 108 | unknown | incomparable | production; resolved case-class copy occurrences, including deferred rewrite shapes |
| updates/case-class-copy-test | 58 | unknown | incomparable | test; resolved case-class copy occurrences, including deferred rewrite shapes |
