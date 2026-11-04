# AST audit checklist

Syntactic candidates, not a claim of complete violations. No application source is changed. Each findings.tsv row includes exact source and AST. Zero candidates is not proof of compliance.

| Goal | Candidates | Next step |
|---|---:|---|
| G1 | 4600 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G2 | 272 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G3 | 1051 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G4 | 761 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G5 | 4 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G6 | 1702 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G7 | 1080 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G8 | 7 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G9 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G10 | 0 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G11 | 20 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G12 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G13 | 0 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G14 | 72 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G15 | 579 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G16 | 7 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G17 | 152 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G18 | 1075 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G19 | 482 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G20 | 128 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G21 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G22 | 11697 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G23 | 29 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G24 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G25 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G26 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G27 | 0 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G28 | 336 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G29 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G30 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G31 | 129 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G32 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G33 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G34 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G35 | 238 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G36 | 39 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G37 | 74 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G38 | 304 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G39 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G40 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G41 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |
| G42 | 9 | [ ] Resolve candidates using semantic symbols and approved design; add fixtures before rewriting |
| G43 | unknown | [ ] NOT MEASURED: implement semantic/design/build/resource check; never treat as zero violations |

## Execution

Parsed 3125 files; 24847 candidate findings. The Scalafix process exit status must also be checked for parse failures.

- [ ] Establish compiling baseline and fresh SemanticDB for definitions AND callers.
- [ ] Group findings by semantic symbol and AST shape; record REWRITE/LINT/UNCHANGED/DECIDE.
- [ ] Test every safe rewrite against expected output and look-alikes, then compile callers.
- [ ] Apply safe rules across main and test sources; rerun inventory and test idempotence.
- [ ] Separately implement final-type migrations, retaining compiler errors as a tracked repair queue.
- [ ] Cover all unmeasured goals before claiming the refactor is complete.
