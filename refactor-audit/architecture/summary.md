# Architecture evidence inventory

Full source and test coverage. Candidate inventories preserve declarations, signatures, constructors, ASTs, references and source hashes. Counts are evidence rows, not confirmed policy violations.

- files: 3159
- declarations: 56389
- references: 808497
- unresolved-references: 8939
- signature-type-references: 163926
- alias-expanded-references: 6993
- visibility-candidates: 490
- visibility-consumer-relationships: 39671
- services-repositories-controllers: 237
- comments: 15960
- top-level-objects: 1392
- object-plan-rows: 645

Service implementations are linked through resolved parent symbols; a class called InMemory elsewhere is not evidence of service parity. Inherited methods, indirect implementations and behavioral parity require further analysis. Unresolved, generated and external references are retained explicitly.
service-obligations.tsv checks trait shape, companion Implementation/InMemory and private Implementation through resolved transitive inheritance. Required service identities come from configuration; disappearance fails the scan. Other Service names are candidates requiring role confirmation. Behavioral parity is always separately unproven by structural evidence.
visibility-candidates.tsv includes configured owners, Repo/Row/Implementation names and owners with database dependencies. Effective visibility includes enclosing owners. visibility-consumers.tsv includes source names, declared/inferred signatures and transparent aliases; opaque representations are not expanded. Automatic candidates require role/scope confirmation. External aliases, implicit arguments and generated typeclass instances remain coverage gaps. Counts are relationships, not independent violations.
Every row of the object plan is reconciled. An absent original object is never marked completed: destination and behavior still need proof. Same-name candidates do not establish conceptual equivalence.
Comments, string constants, annotations and construction ASTs are review inputs. File groups combine same-named companions/aliases, resolved sealed hierarchies and named Iron constraints proven by parent type arguments. Remaining multiple groups require tightly-coupled-helper review; naming alone grants no constraint exception. Visibility, transaction ownership, invariants and layer crossings require the corresponding policy checks.
