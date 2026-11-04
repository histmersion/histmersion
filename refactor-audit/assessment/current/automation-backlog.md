# Bulk rewrite planning baseline

Historical contracts.tsv: 1277 grouped rows, 2818 occurrences. SHA-256: f1e84a6f161461a077b2754518b54646f87ec788061d302608e89d3bc6e02bf0.

These are recorded exceptions, not a fresh violation count. Do not add them to current detector counts or use them as a verified completion denominator. Reconcile each family with semantic matches and valid cases before rewriting. The target is at least 90% automatic fixes across the agreed violation inventory, with the difficult remainder tracked explicitly.

| Historical family | Rows | Occurrences |
|---|---:|---:|
| derives | 366 | 1042 |
| copy | 168 | 479 |
| layer | 464 | 464 |
| unwrap | 118 | 390 |
| primitive | 72 | 313 |
| global | 42 | 82 |
| helper | 23 | 23 |
| prefix | 16 | 16 |
| service | 7 | 7 |
| concept | 1 | 2 |

Work by reusable AST/symbol transformation across all modules. For each family record confirmed violations, legitimate cases, automatic patches, unsupported cases and post-rewrite residuals. Preserve the denominator and policy version; compile definitions and callers and test behavior. A successful single-site rewrite is a fixture, not the migration batch.
