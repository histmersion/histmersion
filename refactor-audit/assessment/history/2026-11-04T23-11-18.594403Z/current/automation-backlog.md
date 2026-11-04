# Bulk rewrite planning baseline

Historical contracts.tsv: 949 grouped rows, 2204 occurrences. SHA-256: 9bb1c24e5b45c60124fbd0c5628b98232d0d79daa4685c7e7e6d7ae0cb54f261.

These are recorded exceptions, not a fresh violation count. Do not add them to current detector counts or use them as a verified completion denominator. Reconcile each family with semantic matches and valid cases before rewriting. The target is at least 90% automatic fixes across the agreed violation inventory, with the difficult remainder tracked explicitly.

| Historical family | Rows | Occurrences |
|---|---:|---:|
| derives | 123 | 505 |
| copy | 168 | 479 |
| unwrap | 119 | 394 |
| layer | 373 | 373 |
| primitive | 74 | 320 |
| global | 42 | 82 |
| helper | 23 | 23 |
| prefix | 19 | 19 |
| service | 7 | 7 |
| concept | 1 | 2 |

Work by reusable AST/symbol transformation across all modules. For each family record confirmed violations, legitimate cases, automatic patches, unsupported cases and post-rewrite residuals. Preserve the denominator and policy version; compile definitions and callers and test behavior. A successful single-site rewrite is a fixture, not the migration batch.
