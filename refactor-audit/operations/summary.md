# Semantic operation inventory

3127 files; 6320 source occurrences. Definitions and call-site ASTs remain available in occurrences.tsv.

These are operation inventories, not blanket violations. Case-class copy owners are resolved against project declarations; unrelated copy methods remain separate. Fold allocations are syntactically visible constructors/tuples or resolved case-class calls, not a proof about evaluation semantics or indirect effects. Iron accessors and unresolved inline expansions remain separate. Every value access still needs an approved boundary decision. Customized derivations/givens require behavioral review.

| Family | Classification | Scope | Count |
|---|---|---|---:|
| copy | case-class-copy | production | 427 |
| copy | case-class-copy | test | 1488 |
| copy | non-case-class-copy | production | 2 |
| copy | non-case-class-copy | test | 1 |
| copy | unresolved-copy-owner | production | 66 |
| copy | unresolved-copy-owner | test | 194 |
| derivation-call | database-derived | production | 42 |
| derivation-call | kindlings-derived | production | 151 |
| derivation-call | kindlings-derived | test | 2 |
| derivation-call | other-derived-review | production | 19 |
| derivation-call | other-derived-review | test | 29 |
| fold | no-visible-allocation; indirect-effects-unresolved | production | 1337 |
| fold | no-visible-allocation; indirect-effects-unresolved | test | 764 |
| fold | visible-allocation-review | production | 154 |
| fold | visible-allocation-review | test | 6 |
| given-instance | instance-body-review | production | 528 |
| given-instance | instance-body-review | test | 24 |
| unwrap | accessor-or-inline-expansion-review | production | 38 |
| unwrap | accessor-or-inline-expansion-review | test | 12 |
| unwrap | iron-accessor | production | 617 |
| unwrap | iron-accessor | test | 257 |
| unwrap | project-value-member-review | production | 97 |
| unwrap | project-value-member-review | test | 65 |
