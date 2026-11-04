# Architecture evidence inventory

Full source and test coverage. Candidate inventories preserve declarations, signatures, constructors, ASTs, references and source hashes. Counts are evidence rows, not confirmed policy violations.

- files: 3127
- declarations: 55998
- references: 802281
- unresolved-references: 9092
- services-repositories-controllers: 234
- comments: 15981
- top-level-objects: 1373
- object-plan-rows: 645

Service implementations are linked through resolved parent symbols; a class called InMemory elsewhere is not evidence of service parity. Inherited methods, indirect implementations and behavioral parity require further analysis. Unresolved, generated and external references are retained explicitly.
service-obligations.tsv checks trait shape, companion Implementation/InMemory and private Implementation through resolved transitive inheritance. Required service identities come from configuration; disappearance fails the scan. Other Service names are candidates requiring role confirmation. Behavioral parity is always separately unproven by structural evidence.
Every row of the object plan is reconciled. An absent original object is never marked completed: destination and behavior still need proof. Same-name candidates do not establish conceptual equivalence.
Comments, string constants, annotations and construction ASTs are review inputs. Multiple file names need companion/alias/sealed-hierarchy grouping before being classified as violations. Visibility, transaction ownership, invariants and layer crossings require the corresponding policy checks.
